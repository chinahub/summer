package cn.jiebaba.summer.web.server;

import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.web.bind.HandlerMethodInvoker;
import cn.jiebaba.summer.web.bind.HandlerMethodAccessChecker;
import cn.jiebaba.summer.web.convert.JsonMessageConverter;
import cn.jiebaba.summer.web.convert.MessageConverter;
import cn.jiebaba.summer.web.filter.Filter;
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于 {@link java.net.ServerSocket} 构建的嵌入式 HTTP/1.1 服务器。每个被接受的连接
 * 都在其专属的虚拟线程上处理（经 Loom 的 Java 协程），使用阻塞 IO——这是经典的
 * 虚拟线程服务器模式。无 servlet、无 NIO selector、无第三方依赖。
 */
public final class SummerWebServer {
    private static final Logger LOG = Logger.getLogger(SummerWebServer.class.getName());

    private final WebServerProperties properties;
    private final Router router;
    private final ExceptionHandlerRegistry exceptions;
    private final MessageConverter converter;
    private final ApplicationContext context;
    private WebSocketRegistry webSocketRegistry;
    private List<Filter> securityFilters = List.of();
    private HandlerMethodAccessChecker accessChecker;

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running = false;
    private Thread acceptThread;

    public SummerWebServer(ApplicationContext context, Router router, ExceptionHandlerRegistry exceptions,
                           MessageConverter converter, WebServerProperties properties) {
        this(context, router, exceptions, converter, properties, List.of(), null);
    }

    public SummerWebServer(ApplicationContext context, Router router, ExceptionHandlerRegistry exceptions,
                           MessageConverter converter, WebServerProperties properties,
                           List<Filter> securityFilters, HandlerMethodAccessChecker accessChecker) {
        this.context = context;
        this.router = router;
        this.exceptions = exceptions;
        this.converter = converter;
        this.properties = properties;
        this.securityFilters = securityFilters == null ? List.of() : securityFilters;
        this.accessChecker = accessChecker;
    }

    public void setSecurityFilters(List<Filter> filters) { this.securityFilters = filters == null ? List.of() : filters; }
    public void setAccessChecker(HandlerMethodAccessChecker checker) { this.accessChecker = checker; }

    /**
     * 启动 Web 服务器：绑定端口、创建虚拟线程执行器与请求分发器，
     * 并在平台线程上启动 accept 循环。
     */
    public void start() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(properties.host(), properties.port()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to bind HTTP server to " + properties.host() + ":" + properties.port(), e);
        }
        running = true;
        // 每个连接运行在各自的虚拟线程上
        executor = Executors.newVirtualThreadPerTaskExecutor();
        HandlerMethodInvoker invoker = new HandlerMethodInvoker(context, converter);
        RequestDispatcher dispatcher = new RequestDispatcher(router, invoker, converter, exceptions,
                properties.contextPath(), securityFilters, accessChecker);

        acceptThread = Thread.ofPlatform().name("summer-accept").daemon(false).start(() -> acceptLoop(dispatcher));

        LOG.info("summer web server started on " + properties.host() + ":" + serverSocket.getLocalPort()
                + " (virtual threads, " + router.routes().size() + " routes)"
                + (properties.contextPath().isEmpty() ? "" : " context-path=" + properties.contextPath())
                + (securityFilters.isEmpty() ? "" : " security-filters=" + securityFilters.size()));
    }

    /**
     * 接收循环：阻塞接受连接，将每个连接交给 {@link #handleConnection} 处理；
     * accept 失败时按 1 秒间隔重试。
     */
    private void acceptLoop(RequestDispatcher dispatcher) {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
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
            executor.execute(() -> handleConnection(socket, dispatcher));
        }
    }

    /**
     * 处理单个连接：循环读取并分发请求，支持 keep-alive 复用连接，
     * 遇到 WebSocket 升级请求时转交 {@link #handleWebSocketUpgrade} 处理。
     *
     * @param socket     客户端 socket
     * @param dispatcher 请求分发器
     */
    private void handleConnection(Socket socket, RequestDispatcher dispatcher) {
        try (socket;
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             OutputStream out = socket.getOutputStream()) {
            socket.setSoTimeout(properties.keepAlive() ? properties.keepAliveTimeout() : 15000);
            int requestCount = 0;
            while (true) {
                RawHttpRequest raw;
                try {
                    raw = RawHttpRequest.parse(in, properties.maxHeaderSize(), properties.maxRequestSize());
                } catch (IOException e) {
                    // 客户端关闭或超时 —— 对 keep-alive 属正常
                    return;
                }
                requestCount++;

                if (webSocketRegistry != null && webSocketRegistry.hasEndpoints()
                        && WebSocketHandshake.isUpgradeRequest(raw.headers(), raw.method())) {
                    handleWebSocketUpgrade(socket, in, out, raw);
                    return;
                }

                WebRequest request = new WebRequest(raw);
                request.remoteAddress(socket.getRemoteSocketAddress() == null ? "tcp"
                        : socket.getRemoteSocketAddress().toString());
                WebResponse response = new WebResponse(out);
                response.keepAlive(properties.keepAlive()
                        && requestCount < properties.maxRequestsPerConnection()
                        && shouldKeepAlive(raw));
                dispatcher.dispatch(request, response);
                if (!response.committed()) {
                    response.commit();
                }

                if (!response.keepAlive()) {
                    return;
                }
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "connection error from " + socket.getRemoteSocketAddress(), e);
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
    private void handleWebSocketUpgrade(Socket socket, BufferedInputStream in, OutputStream out, RawHttpRequest raw) {
        String target = raw.target();
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q);
        var endpointOpt = webSocketRegistry.match(path);
        if (endpointOpt.isEmpty()) {
            writeError(out, HttpStatus.NOT_FOUND.code(), "Not Found", "no WebSocket endpoint at " + path);
            return;
        }
        try {
            if (!WebSocketHandshake.completeHandshake(raw.headers(), out)) {
                writeError(out, HttpStatus.BAD_REQUEST.code(), "Bad Request", "missing Sec-WebSocket-Key");
                return;
            }
            socket.setSoTimeout(0); // WebSocket 不设读取超时
            WebSocketSession session = new WebSocketSession(socket, endpointOpt.get());
            LOG.info("WebSocket connected: " + path + " session=" + session.id());
            session.runLoop();
            LOG.info("WebSocket closed: " + path + " session=" + session.id());
        } catch (IOException e) {
            LOG.log(Level.FINE, "WebSocket handshake failed for " + path, e);
        }
    }

    private void writeError(OutputStream out, int status, String error, String message) {
        try {
            WebResponse response = new WebResponse(out);
            response.status(status);
            response.contentType(MediaType.APPLICATION_JSON_UTF8);
            response.body("{\"status\":" + status + ",\"error\":\"" + error
                    + "\",\"message\":\"" + escape(message) + "\"}");
            response.commit();
        } catch (IOException e) {
            LOG.log(Level.FINE, "failed to write error response", e);
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
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "error closing server socket", e);
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                    executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        LOG.info("summer web server stopped");
    }

    public void setWebSocketRegistry(WebSocketRegistry registry) {
        this.webSocketRegistry = registry;
    }

    public WebSocketRegistry webSocketRegistry() { return webSocketRegistry; }

    public int port() {
        return serverSocket != null ? serverSocket.getLocalPort() : properties.port();
    }

    public static SummerWebServer createDefault(ApplicationContext context, Router router,
                                                 ExceptionHandlerRegistry exceptions) {
        Environment env = context.getEnvironment();
        return new SummerWebServer(context, router, exceptions, new JsonMessageConverter(), WebServerProperties.from(env));
    }

    @SuppressWarnings("unused")
    private static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
