package cn.jiebaba.summer.test;

import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.sample.Application;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** 进程内冒烟测试：启动应用并通过回环地址遍历每个路由。 */
public class SmokeTest {
    /**
     * 冒烟测试入口：启动应用，通过回环地址对全部路由（GET/POST/PUT/DELETE 等）
     * 发起请求，结束后关闭 Web 服务与上下文。
     */
    public static void main(String[] args) throws Exception {
        SummerApplication app = SummerApplication.run(Application.class, args);
        int port = app.webServer().port();
        Thread.sleep(400);
        try {
            test(port, "GET", "/", null);
            test(port, "GET", "/?name=summer", null);
            test(port, "GET", "/hello/looming", null);
            test(port, "GET", "/users", null);
            test(port, "GET", "/users/1", null);
            test(port, "GET", "/users/999", null);
            test(port, "POST", "/users", "{\"name\":\"alice\",\"age\":30}");
            test(port, "PUT", "/users/2", "{\"name\":\"alice2\",\"age\":31}");
            test(port, "DELETE", "/users/2", null);
            test(port, "GET", "/users/greeting", null);
            test(port, "GET", "/no-such-route", null);
        } finally {
            app.webServer().stop();
            app.context().close();
        }
    }

    /**
     * 向指定端口发起一次 HTTP 请求：组装请求行、头部与请求体，读取完整响应并
     * 打印状态行与响应正文。
     */
    static void test(int port, String method, String path, String body) throws Exception {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            StringBuilder h = new StringBuilder();
            h.append(method).append(' ').append(path).append(" HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n");
            if (body != null) {
                h.append("Content-Type: application/json\r\nContent-Length: ").append(bodyBytes.length).append("\r\n");
            }
            h.append("\r\n");
            out.write(h.toString().getBytes(StandardCharsets.UTF_8));
            if (body != null) out.write(bodyBytes);
            out.flush();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            s.getInputStream().transferTo(buf);
            String resp = buf.toString(StandardCharsets.UTF_8);
            String statusLine = resp.substring(0, resp.indexOf("\r\n"));
            int idx = resp.indexOf("\r\n\r\n");
            String respBody = idx >= 0 ? resp.substring(idx + 4) : "";
            System.out.println("[" + method + " " + path + "] " + statusLine);
            System.out.println(respBody);
            System.out.println("----");
        }
    }
}
