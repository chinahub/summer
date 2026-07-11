package cn.jiebaba.summer.web.server;

import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.web.bind.HandlerMethodInvoker;
import cn.jiebaba.summer.web.bind.HandlerMethodAccessChecker;
import cn.jiebaba.summer.web.convert.JsonMessageConverter;
import cn.jiebaba.summer.web.convert.MessageConverter;
import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChainSelector;
import cn.jiebaba.summer.web.http.HttpStatus;
import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.web.http.RawHttpRequest;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;
import cn.jiebaba.summer.web.routing.Router;
import cn.jiebaba.summer.web.support.ExceptionHandlerRegistry;
import cn.jiebaba.summer.web.websocket.WebSocketHandshake;
import cn.jiebaba.summer.web.websocket.WebSocketRegistry;
import cn.jiebaba.summer.web.websocket.WebSocketSession;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * 基于 {@link ServerSocketChannel}（阻塞模式）构建的嵌入式 HTTP/1.1 服务器。每个被接受的连接
 * 都在其专属的虚拟线程上处理（经 Loom 的 Java 协程），使用阻塞 IO——这是经典的
 * 虚拟线程服务器模式。支持可选的 TLS（基于 SSLSocket），无 servlet、无 NIO selector、无第三方依赖。
 */
public final class SummerWebServer {
    private static final Logger LOG = Logger.getLogger(SummerWebServer.class.getName());

    /** 每连接复用读缓冲大小（字节），以块为单位从通道读取。 */
    private static final int BUFFER_SIZE = 8192;

    private final WebServerProperties properties;
    private final Router router;
    private final ExceptionHandlerRegistry exceptions;
    private final MessageConverter converter;
    private final ApplicationContext context;
    private WebSocketRegistry webSocketRegistry;
    private List<Filter> securityFilters = List.of();
    private FilterChainSelector filterChainSelector;
    private HandlerMethodAccessChecker accessChecker;

    private ServerSocketChannel serverChannel;
    private ExecutorService executor;
    private ScheduledExecutorService idleScheduler;
    private volatile boolean running = false;
    private Thread acceptThread;
    private SSLSocketFactory sslSocketFactory;

    public SummerWebServer(ApplicationContext context, Router router, ExceptionHandlerRegistry exceptions,
                           MessageConverter converter, WebServerProperties properties) {
        this(context, router, exceptions, converter, properties, List.of(), null);
    }

    public SummerWebServer(ApplicationContext context, Router router, ExceptionHandlerRegistry exceptions,
                           MessageConverter converter, WebServerProperties properties,
                           List<Filter> securityFilters, HandlerMethodAccessChecker accessChecker) {
        this(context, router, exceptions, converter, properties, securityFilters, null, accessChecker);
    }

    /** 以按请求选择器装配，支持多 {@code SecurityFilterChain} 分发。 */
    public SummerWebServer(ApplicationContext context, Router router, ExceptionHandlerRegistry exceptions,
                           MessageConverter converter, WebServerProperties properties,
                           List<Filter> securityFilters, FilterChainSelector filterChainSelector,
                           HandlerMethodAccessChecker accessChecker) {
        this.context = context;
        this.router = router;
        this.exceptions = exceptions;
        this.converter = converter;
        this.properties = properties;
        this.securityFilters = securityFilters == null ? List.of() : securityFilters;
        this.filterChainSelector = filterChainSelector;
        this.accessChecker = accessChecker;
    }

    public void setSecurityFilters(List<Filter> filters) { this.securityFilters = filters == null ? List.of() : filters; }
    public void setFilterChainSelector(FilterChainSelector selector) { this.filterChainSelector = selector; }
    public void setAccessChecker(HandlerMethodAccessChecker checker) { this.accessChecker = checker; }

    /**
     * 启动 Web 服务器：绑定端口、创建虚拟线程执行器与 idle 超时调度器，
     * 若启用 TLS 则初始化 SSL 上下文，最后在平台线程上启动 accept 循环。
     */
    public void start() {
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(true);
            serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            serverChannel.bind(new InetSocketAddress(properties.host(), properties.port()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to bind HTTP server to " + properties.host() + ":" + properties.port(), e);
        }
        if (properties.ssl() != null && properties.ssl().enabled()) {
            try {
                SSLContext sslContext = buildSslContext(properties.ssl());
                sslSocketFactory = sslContext.getSocketFactory();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize SSL context", e);
            }
        }
        running = true;
        // 每个连接运行在各自的虚拟线程上
        executor = Executors.newVirtualThreadPerTaskExecutor();
        idleScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Thread.ofPlatform().daemon(true).name("summer-idle-timeout").unstarted(r);
            return t;
        });
        HandlerMethodInvoker invoker = new HandlerMethodInvoker(context, converter);
        RequestDispatcher dispatcher = new RequestDispatcher(router, invoker, converter, exceptions,
                properties.contextPath(), securityFilters, filterChainSelector, accessChecker);

        acceptThread = Thread.ofPlatform().name("summer-accept").daemon(false).start(() -> acceptLoop(dispatcher));

        LOG.info("summer web server started on " + properties.host() + ":" + port()
                + " (virtual threads, " + router.routes().size() + " routes)"
                + (properties.contextPath().isEmpty() ? "" : " context-path=" + properties.contextPath())
                + (filterChainSelector != null
                        ? " security=multi-chain"
                        : (securityFilters.isEmpty() ? "" : " security-filters=" + securityFilters.size()))
                + (sslSocketFactory != null ? " TLS=enabled" : ""));
    }

    /**
     * 接收循环：阻塞接受连接，将每个连接交给 {@link #handleConnection} 处理；
     * accept 失败时按 1 秒间隔重试。
     */
    private void acceptLoop(RequestDispatcher dispatcher) {
        while (running) {
            SocketChannel ch;
            try {
                ch = serverChannel.accept();
            } catch (IOException e) {
                if (running) {
                    LOG.log(Level.WARNING, "Accept failed, retrying in 1s", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                } else {
                    break;
                }
            }
            executor.execute(() -> handleConnection(ch, dispatcher));
        }
    }

    /**
     * 处理单个连接：在虚拟线程上循环读取并分发请求，支持 keep-alive 复用连接，
     * 遇到 WebSocket 升级请求时转交 {@link #handleWebSocketUpgrade} 处理。
     * 若启用 TLS，先将原始通道包装为 SSLSocket 再进行读写。
     *
     * @param raw        客户端通道（阻塞模式，尚未握手 TLS）
     * @param dispatcher 请求分发器
     */
    private void handleConnection(SocketChannel raw, RequestDispatcher dispatcher) {
        String remote = remoteAddress(raw);
        ByteChannel ch;
        SslByteChannel tls = null;
        if (sslSocketFactory != null) {
            try {
                tls = wrapTls(raw);
            } catch (IOException e) {
                LOG.log(Level.FINE, "TLS handshake failed from " + remote, e);
                closeQuietly(raw);
                return;
            }
            ch = tls;
        } else {
            ch = raw;
        }
        try {
            ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
            buf.limit(0); // 初始为空，首次读取时从通道填充
            int requestCount = 0;
            while (true) {
                RawHttpRequest rawReq;
                try {
                    rawReq = readRequest(ch, buf);
                } catch (MaxUploadSizeExceededException e) {
                    LOG.warning("upload too large from " + remote
                            + ": " + e.actualSize() + " > " + e.maxSize() + " bytes");
                    writeAscii(ch, "HTTP/1.1 413 Request Entity Too Large\r\n"
                            + "Content-Length: 0\r\nConnection: close\r\n\r\n");
                    return;
                } catch (ClosedByInterruptException e) {
                    Thread.interrupted(); // 读超时（虚拟线程被中断）—— 清除中断标志
                    return;
                } catch (IOException e) {
                    // 客户端关闭或读超时 —— 对 keep-alive 属正常
                    return;
                }
                requestCount++;

                if (webSocketRegistry != null && webSocketRegistry.hasEndpoints()
                        && WebSocketHandshake.isUpgradeRequest(rawReq.headers(), rawReq.method())) {
                    handleWebSocketUpgrade(ch, buf, rawReq);
                    return;
                }

                WebRequest request = new WebRequest(rawReq);
                request.remoteAddress(remote);
                WebResponse response = new WebResponse(ch);
                response.keepAlive(properties.keepAlive()
                        && requestCount < properties.maxRequestsPerConnection()
                        && shouldKeepAlive(rawReq));
                dispatcher.dispatch(request, response);
                if (!response.committed()) {
                    response.commit();
                }

                if (!response.keepAlive()) {
                    return;
                }
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "connection error from " + remote, e);
        } finally {
            closeQuietly(tls);
            closeQuietly(raw);
        }
    }

    /**
     * 在 idle 超时保护下从通道解析一个请求：调度当前虚拟线程的中断作为读超时，
     * 解析完成或异常后取消调度。超时触发的中断会以 {@link ClosedByInterruptException}
     * 形式中断阻塞读取，替代原 {@code Socket.setSoTimeout} 的超时语义。
     */
    private RawHttpRequest readRequest(ReadableByteChannel ch, ByteBuffer buf) throws IOException {
        int timeoutMs = properties.keepAlive() ? properties.keepAliveTimeout() : 15000;
        Thread me = Thread.currentThread();
        ScheduledFuture<?> timeout = idleScheduler.schedule(me::interrupt, timeoutMs, TimeUnit.MILLISECONDS);
        try {
            return RawHttpRequest.parse(ch, buf, properties.maxHeaderSize(), properties.maxRequestSize());
        } finally {
            timeout.cancel(false);
            Thread.interrupted(); // 清除可能恰好触发的中断标志，避免污染后续 handler
        }
    }

    private static boolean shouldKeepAlive(RawHttpRequest raw) {
        String conn = null;
        var values = raw.headers().get("connection");
        if (values != null && !values.isEmpty()) conn = values.get(0);
        if (conn == null) return false;
        conn = conn.trim().toLowerCase();
        if ("close".equals(conn)) return false;
        // HTTP/1.1 默认 keep-alive；HTTP/1.0 需显式 keep-alive
        if ("keep-alive".equals(conn)) return true;
        return raw.protocol() != null && raw.protocol().equalsIgnoreCase("HTTP/1.1");
    }

    /**
     * 处理 WebSocket 升级：匹配端点、完成握手后将连接交由 {@link WebSocketSession} 运行。
     */
    private void handleWebSocketUpgrade(ByteChannel ch, ByteBuffer buf, RawHttpRequest raw) {
        String target = raw.target();
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q);
        var endpointOpt = webSocketRegistry.match(path);
        if (endpointOpt.isEmpty()) {
            writeError(ch, HttpStatus.NOT_FOUND.code(), "Not Found", "no WebSocket endpoint at " + path);
            return;
        }
        try {
            if (!WebSocketHandshake.completeHandshake(raw.headers(), ch)) {
                writeError(ch, HttpStatus.BAD_REQUEST.code(), "Bad Request", "missing Sec-WebSocket-Key");
                return;
            }
            WebSocketSession session = new WebSocketSession(ch, buf, endpointOpt.get());
            LOG.info("WebSocket connected: " + path + " session=" + session.id());
            session.runLoop();
            LOG.info("WebSocket closed: " + path + " session=" + session.id());
        } catch (IOException e) {
            LOG.log(Level.FINE, "WebSocket handshake failed for " + path, e);
        }
    }

    private void writeError(WritableByteChannel ch, int status, String error, String message) {
        try {
            WebResponse response = new WebResponse(ch);
            response.status(status);
            response.contentType(MediaType.APPLICATION_JSON_UTF8);
            response.body("{\"status\":" + status + ",\"error\":\"" + error
                    + "\",\"message\":\"" + escape(message) + "\"}");
            response.commit();
        } catch (IOException e) {
            LOG.log(Level.FINE, "failed to write error response", e);
        }
    }

    private static void writeAscii(WritableByteChannel ch, String text) {
        try {
            ByteBuffer b = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (b.hasRemaining()) {
                ch.write(b);
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "failed to write raw bytes", e);
        }
    }

    private static String remoteAddress(SocketChannel ch) {
        try {
            return ch.getRemoteAddress() == null ? "tcp" : ch.getRemoteAddress().toString();
        } catch (IOException e) {
            return "tcp";
        }
    }

    /**
     * 根据 SSL 配置构建 {@link SSLContext}：加载密钥库（服务端证书）与可选信任库（客户端证书验证）。
     * 密钥库或信任库为 null 时对应管理器置空，由 JDK 默认行为处理。
     */
    private static SSLContext buildSslContext(WebServerProperties.Ssl ssl) throws Exception {
        KeyManager[] keyManagers = null;
        if (ssl.keystore() != null) {
            KeyStore ks = KeyStore.getInstance(ssl.keystoreType() != null ? ssl.keystoreType() : "PKCS12");
            char[] kspass = ssl.keystorePassword() != null ? ssl.keystorePassword().toCharArray() : null;
            try (InputStream in = Files.newInputStream(Paths.get(ssl.keystore()))) {
                ks.load(in, kspass);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, kspass);
            keyManagers = kmf.getKeyManagers();
        }
        TrustManager[] trustManagers = null;
        if (ssl.truststore() != null) {
            KeyStore ts = KeyStore.getInstance(ssl.truststoreType() != null ? ssl.truststoreType() : "PKCS12");
            char[] tspass = ssl.truststorePassword() != null ? ssl.truststorePassword().toCharArray() : null;
            try (InputStream in = Files.newInputStream(Paths.get(ssl.truststore()))) {
                ts.load(in, tspass);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
            trustManagers = tmf.getTrustManagers();
        }
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(keyManagers, trustManagers, null);
        return ctx;
    }

    /** 在已接受的阻塞通道上叠加 TLS：以 SSLSocket 包装底层 Socket，设为服务端模式并完成握手。 */
    private SslByteChannel wrapTls(SocketChannel raw) throws IOException {
        Socket underlying = raw.socket();
        SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(
                underlying,
                underlying.getInetAddress().getHostAddress(),
                underlying.getPort(),
                true);
        socket.setUseClientMode(false);
        socket.setNeedClientAuth(properties.ssl().needClientAuth());
        socket.startHandshake();
        return new SslByteChannel(socket);
    }

    private static void closeQuietly(Channel c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignore) {
                // 忽略关闭异常
            }
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else sb.append(c);
        }
        return sb.toString();
    }

    public void stop() {
        running = false;
        try {
            if (serverChannel != null && serverChannel.isOpen()) serverChannel.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "error closing server channel", e);
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (idleScheduler != null) {
            idleScheduler.shutdownNow();
        }
        LOG.info("summer web server stopped");
    }

    public void setWebSocketRegistry(WebSocketRegistry registry) {
        this.webSocketRegistry = registry;
    }

    public WebSocketRegistry webSocketRegistry() { return webSocketRegistry; }

    public int port() {
        if (serverChannel != null) {
            try {
                return ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
            } catch (IOException e) {
                return properties.port();
            }
        }
        return properties.port();
    }

    public static SummerWebServer createDefault(ApplicationContext context, Router router,
                                                 ExceptionHandlerRegistry exceptions) {
        Environment env = context.getEnvironment();
        return new SummerWebServer(context, router, exceptions, new JsonMessageConverter(), WebServerProperties.from(env));
    }
}
