package cn.jiebaba.summer.test;

import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.sample.Application;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * 进程内 TLS 冒烟测试：启动启用 SSL 的应用，以信任全部证书的 SSL 客户端
 * 在同一条 TLS 连接上发送多个请求，验证 TLS 握手与 keep-alive 连接复用。
 */
public class TlsSmokeTest {

    private static int passed = 0;

    /**
     * TLS 冒烟测试入口：生成自签名密钥库，启动启用 SSL 的应用，
     * 在单条 TLS 连接上依次发送 keep-alive 与 close 请求，验证 TLS 握手与连接复用。
     */
    public static void main(String[] args) throws Exception {
        // 生成自签名密钥库
        Path keystore = Path.of(System.getProperty("java.io.tmpdir"), "summer-tls-test.p12");
        generateKeystore(keystore);

        // 设置 SSL 系统属性（系统属性优先级最高，由 Environment 读取）
        System.setProperty("server.port", "0");
        System.setProperty("server.ssl.enabled", "true");
        System.setProperty("server.ssl.keystore", keystore.toString());
        System.setProperty("server.ssl.keystorepassword", "summerpw");
        System.setProperty("server.ssl.keystoretype", "PKCS12");

        SummerApplication app = SummerApplication.run(Application.class, args);
        int port = app.webServer().port();
        Thread.sleep(500);

        // 信任全部证书的 SSL 客户端（仅用于测试）
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{ trustAll() }, new SecureRandom());

        try (SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.startHandshake();
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            // 请求 1：keep-alive
            sendRequest(out, "GET", "/");
            String resp1 = readResponse(in);
            expect("tls req1 status 200", true, resp1.startsWith("HTTP/1.1 200"));
            expect("tls req1 keep-alive header", true, resp1.toLowerCase().contains("connection: keep-alive"));

            // 请求 2：复用同一 TLS 连接
            sendRequest(out, "GET", "/hello/world");
            String resp2 = readResponse(in);
            expect("tls req2 status 200 (connection reused)", true, resp2.startsWith("HTTP/1.1 200"));

            // 请求 3：Connection: close
            out.write(("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            String resp3 = readResponse(in);
            expect("tls req3 status 200", true, resp3.startsWith("HTTP/1.1 200"));
            expect("tls req3 connection closed", true, resp3.toLowerCase().contains("connection: close"));

            // 服务端应关闭连接
            int afterClose = in.read();
            expect("tls server closed connection after close", true, afterClose == -1);
        } finally {
            app.webServer().stop();
            app.context().close();
        }
        System.out.println();
        System.out.println("TLS smoke test: " + passed + " assertions passed");
    }

    /** 调用 keytool 生成自签名 PKCS12 密钥库（每次重新生成以保证干净状态）。 */
    private static void generateKeystore(Path keystore) throws Exception {
        Files.deleteIfExists(keystore);
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? ".exe" : "";
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool" + exe).toString();
        int exitCode = new ProcessBuilder(keytool,
                "-genkeypair", "-alias", "summer", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "365", "-dname", "CN=localhost",
                "-keystore", keystore.toString(),
                "-storetype", "PKCS12", "-storepass", "summerpw", "-keypass", "summerpw")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start().waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("keytool failed with exit code " + exitCode);
        }
    }

    /** 创建信任全部证书的 TrustManager（仅用于测试）。 */
    private static X509TrustManager trustAll() {
        return new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }

    static void sendRequest(OutputStream out, String method, String path) throws Exception {
        StringBuilder h = new StringBuilder();
        h.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        h.append("Host: 127.0.0.1\r\n");
        h.append("Connection: keep-alive\r\n");
        h.append("\r\n");
        out.write(h.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * 读取完整的 HTTP 响应：先按 CRLFCRLF 分隔读取响应头，再按 Content-Length 读取响应体，
     * 返回头部与正文的拼接结果。
     */
    static String readResponse(BufferedInputStream in) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int[] win = new int[4];
        int n = 0, b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            win[n++ & 3] = b;
            if (n >= 4) {
                if (win[(n - 4) & 3] == '\r' && win[(n - 3) & 3] == '\n'
                        && win[(n - 2) & 3] == '\r' && win[(n - 1) & 3] == '\n') break;
            }
        }
        String headers = buf.toString(StandardCharsets.UTF_8);
        int contentLength = 0;
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring(15).trim());
            }
        }
        byte[] body = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int r = in.read(body, read, contentLength - read);
            if (r == -1) break;
            read += r;
        }
        return headers + new String(body, StandardCharsets.UTF_8);
    }

    static void expect(String label, Object expected, Object actual) {
        boolean ok = java.util.Objects.equals(expected, actual);
        if (ok) { passed++; }
        else { System.out.println("  FAIL " + label + ": expected=" + expected + " actual=" + actual); }
    }
}
