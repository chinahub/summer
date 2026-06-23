package cn.jiebaba.summer.sample;

import cn.jiebaba.summer.boot.SummerApplication;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * In-process HTTP keep-alive smoke test: sends two requests on the same TCP
 * connection and verifies both get responses (connection reuse).
 */
public class KeepAliveSmokeTest {

    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        SummerApplication app = SummerApplication.run(Application.class, args);
        int port = app.webServer().port();
        Thread.sleep(500);
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            // Request 1 with keep-alive
            sendRequest(out, "GET", "/", null);
            String resp1 = readResponse(in);
            expect("req1 status 200", true, resp1.startsWith("HTTP/1.1 200"));
            expect("req1 keep-alive header", true, resp1.toLowerCase().contains("connection: keep-alive"));

            // Request 2 on the SAME connection
            sendRequest(out, "GET", "/hello/world", null);
            String resp2 = readResponse(in);
            expect("req2 status 200 (connection reused)", true, resp2.startsWith("HTTP/1.1 200"));

            // Request 3 with Connection: close
            out.write(("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            String resp3 = readResponse(in);
            expect("req3 status 200", true, resp3.startsWith("HTTP/1.1 200"));
            expect("req3 connection closed", true, resp3.toLowerCase().contains("connection: close"));

            // connection should now be closed by server
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

    static String readResponse(BufferedInputStream in) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int[] win = new int[4];
        int n = 0, b;
        // read headers
        while ((b = in.read()) != -1) {
            buf.write(b);
            win[n++ & 3] = b;
            if (n >= 4) {
                if (win[(n - 4) & 3] == '\r' && win[(n - 3) & 3] == '\n'
                        && win[(n - 2) & 3] == '\r' && win[(n - 1) & 3] == '\n') break;
            }
        }
        String headers = buf.toString(StandardCharsets.UTF_8);
        // parse Content-Length
        int contentLength = 0;
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring(15).trim());
            }
        }
        // read body
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