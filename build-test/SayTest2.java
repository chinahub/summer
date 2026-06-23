import io.summer.boot.SummerApplication;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SayTest2 {
    public static void main(String[] args) throws Exception {
        SummerApplication app = SummerApplication.run(io.summer.sample.Application.class, args);
        Thread.sleep(500);
        int port = app.webServer().port();

        // 打印所有注册的路由
        System.out.println("Registered routes:");
        app.webServer(); // just to trigger
        // 试 /say (不带尾斜杠)
        test(port, "POST", "/say", "{\"name\":\"summer\"}");
        // 试 /say/ (带尾斜杠)
        test(port, "POST", "/say/", "{\"name\":\"summer\"}");

        app.webServer().stop();
        app.context().close();
    }
    static void test(int port, String method, String path, String body) throws Exception {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            String req = method + " " + path + " HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "Connection: close\r\n\r\n" + body;
            out.write(req.getBytes(StandardCharsets.UTF_8));
            out.flush();
            String resp = readAll(s.getInputStream());
            String status = resp.substring(0, resp.indexOf("\r\n"));
            String respBody = resp.contains("\r\n\r\n") ? resp.substring(resp.indexOf("\r\n\r\n")+4) : "";
            System.out.println("[" + method + " " + path + "] " + status);
            System.out.println("  " + respBody);
        }
    }
    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        in.transferTo(buf);
        return buf.toString(StandardCharsets.UTF_8);
    }
}