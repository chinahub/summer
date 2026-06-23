import io.summer.boot.SummerApplication;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SayTest {
    static int passed = 0;
    public static void main(String[] args) throws Exception {
        SummerApplication app = SummerApplication.run(io.summer.sample.Application.class, args);
        Thread.sleep(500);
        int port = app.webServer().port();
        try {
            // POST /say/ body={"name":"summer"}
            String body = "{\"name\":\"summer\"}";
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.setSoTimeout(5000);
                OutputStream out = s.getOutputStream();
                String req = "POST /say/ HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "Connection: close\r\n\r\n"
                    + body;
                out.write(req.getBytes(StandardCharsets.UTF_8));
                out.flush();
                String resp = readAll(s.getInputStream());
                System.out.println("[POST /say/] response:");
                System.out.println(resp);
                expect("status 200", true, resp.startsWith("HTTP/1.1 200"));
                expect("body contains hello:summer", true, resp.contains("\"hello\":\"summer\""));
            }

            // POST /say/ body={"name":"world"}
            body = "{\"name\":\"world\"}";
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.setSoTimeout(5000);
                OutputStream out = s.getOutputStream();
                String req = "POST /say/ HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "Connection: close\r\n\r\n"
                    + body;
                out.write(req.getBytes(StandardCharsets.UTF_8));
                out.flush();
                String resp = readAll(s.getInputStream());
                System.out.println("[POST /say/ name=world] body:");
                System.out.println(resp.substring(resp.indexOf("\r\n\r\n")+4));
                expect("body hello:world", true, resp.contains("\"hello\":\"world\""));
            }

            // POST /say/ missing name -> hello:null
            body = "{\"age\":18}";
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.setSoTimeout(5000);
                OutputStream out = s.getOutputStream();
                String req = "POST /say/ HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "Connection: close\r\n\r\n"
                    + body;
                out.write(req.getBytes(StandardCharsets.UTF_8));
                out.flush();
                String resp = readAll(s.getInputStream());
                System.out.println("[POST /say/ no name] body:");
                System.out.println(resp.substring(resp.indexOf("\r\n\r\n")+4));
                expect("missing name -> hello:null", true, resp.contains("\"hello\":null"));
            }
        } finally {
            app.webServer().stop();
            app.context().close();
        }
        System.out.println();
        System.out.println("SayTest: " + passed + " assertions passed");
    }

    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        in.transferTo(buf);
        return buf.toString(StandardCharsets.UTF_8);
    }

    static void expect(String label, Object expected, Object actual) {
        boolean ok = java.util.Objects.equals(expected, actual);
        if (ok) { passed++; }
        else { System.out.println("  FAIL " + label + ": expected=" + expected + " actual=" + actual); }
    }
}