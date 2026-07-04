package cn.jiebaba.summer.test;

import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.sample.Application;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 进程内 HTTP keep-alive 冒烟测试：在同一条 TCP 连接上发送多个请求，
 * 验证均能收到响应（连接复用）。
 */
public class KeepAliveSmokeTest {

    private static int passed = 0;

    /**
     * keep-alive 冒烟测试入口：启动应用，在单条 TCP 连接上依次发送 keep-alive
     * 与 close 请求，验证连接复用与服务端正确关闭。
     */
    public static void main(String[] args) throws Exception {
        SummerApplication app = SummerApplication.run(Application.class, args);
        int port = app.webServer().port();
        Thread.sleep(500);
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            // 请求 1：keep-alive
            sendRequest(out, "GET", "/", null);
            String resp1 = readResponse(in);
            expect("req1 status 200", true, resp1.startsWith("HTTP/1.1 200"));
            expect("req1 keep-alive header", true, resp1.toLowerCase().contains("connection: keep-alive"));

            // 请求 2：复用同一连接
            sendRequest(out, "GET", "/hello/world", null);
            String resp2 = readResponse(in);
            expect("req2 status 200 (connection reused)", true, resp2.startsWith("HTTP/1.1 200"));

            // 请求 3：Connection: close
            out.write(("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            String resp3 = readResponse(in);
            expect("req3 status 200", true, resp3.startsWith("HTTP/1.1 200"));
            expect("req3 connection closed", true, resp3.toLowerCase().contains("connection: close"));

            // 此时连接应由服务端关闭
            int afterClose = in.read();
            expect("server closed connection after close", true, afterClose == -1);
        } finally {
            app.webServer().stop();
            app.context().close();
        }
        System.out.println();
        System.out.println("Keep-alive smoke test: " + passed + " assertions passed");
    }

    static void sendRequest(OutputStream out, String method, String path, String body) throws Exception {
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
        // 读取响应头
        while ((b = in.read()) != -1) {
            buf.write(b);
            win[n++ & 3] = b;
            if (n >= 4) {
                if (win[(n - 4) & 3] == '\r' && win[(n - 3) & 3] == '\n'
                        && win[(n - 2) & 3] == '\r' && win[(n - 1) & 3] == '\n') break;
            }
        }
        String headers = buf.toString(StandardCharsets.UTF_8);
        // 解析 Content-Length
        int contentLength = 0;
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring(15).trim());
            }
        }
        // 读取响应体
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
