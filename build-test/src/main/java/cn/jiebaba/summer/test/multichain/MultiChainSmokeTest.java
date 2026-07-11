package cn.jiebaba.summer.test.multichain;

import cn.jiebaba.summer.boot.SummerApplication;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多 SecurityFilterChain 端到端冒烟测试：启动 {@link MultiChainTestApp}，验证 {@code /api/**}
 * 与兜底 {@code /**} 两条链按请求路径正确分流。
 *
 * <p>手动运行（不纳入 TestRunner 扫描）：
 * <pre>java -cp ... cn.jiebaba.summer.test.multichain.MultiChainSmokeTest</pre>
 */
public class MultiChainSmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    /**
     * 测试入口：启动应用后依次验证兜底链放行、{@code /api} 链鉴权、登录签发与角色级授权等场景，结束后关闭服务。
     */
    public static void main(String[] args) throws Exception {
        // 使用临时端口，避免与其他服务冲突
        System.setProperty("server.port", "0");
        System.setProperty("summer.security.enabled", "true");
        System.setProperty("summer.security.jwt.secret", "multi-chain-test-jwt-secret-32-bytes!");
        SummerApplication app = SummerApplication.run(MultiChainTestApp.class, args);
        int port = app.webServer().port();
        Thread.sleep(400);
        try {
            // 1. 兜底链：/public/hello 无需认证 -> 200
            expect(200, "public via fallback chain", request(port, "GET", "/public/hello", null, null));

            // 2. 兜底链：/info 非 /api 路径，未被 /api 链拦截 -> 200
            expect(200, "info via fallback chain", request(port, "GET", "/info", null, null));

            // 3. /api 链：/api/me 无 token -> 401（受保护）
            expect(401, "api me no-token", request(port, "GET", "/api/me", null, null));

            // 4. /api 链登录：admin/admin123 -> 200 + token
            Response login = request(port, "POST", "/api/login",
                    "{\"username\":\"admin\",\"password\":\"admin123\"}", null);
            expect(200, "admin login", login);
            String adminToken = extract(login.body, "\"accessToken\":\"([^\"]+)\"");
            check(adminToken != null, "admin token present");

            // 5. /api 链：带 token 访问 /api/me -> 200 且注入主体
            Response me = request(port, "GET", "/api/me", null, adminToken);
            expect(200, "api me admin", me);
            check(me.body.contains("\"username\":\"admin\""), "api principal injected");

            // 6. /api 链 + 方法级授权：admin 访问 /api/admin -> 200
            expect(200, "api admin as admin", request(port, "GET", "/api/admin", null, adminToken));

            // 7. 错误凭据 -> 401
            expect(401, "bad login", request(port, "POST", "/api/login",
                    "{\"username\":\"admin\",\"password\":\"wrong\"}", null));

            // 8. user 登录
            Response userLogin = request(port, "POST", "/api/login",
                    "{\"username\":\"user\",\"password\":\"user123\"}", null);
            expect(200, "user login", userLogin);
            String userToken = extract(userLogin.body, "\"accessToken\":\"([^\"]+)\"");
            check(userToken != null, "user token present");

            // 9. user 访问 /api/me -> 200
            expect(200, "api me as user", request(port, "GET", "/api/me", null, userToken));

            // 10. user 访问 /api/admin -> 403（方法级 @PreAuthorize ADMIN）
            expect(403, "api admin as user", request(port, "GET", "/api/admin", null, userToken));

            // 11. 篡改 token -> 401
            expect(401, "tampered token", request(port, "GET", "/api/me", null, adminToken + "x"));

            // 12. 再次确认兜底链：/info 仍 200（未被 /api 链影响）
            expect(200, "info still open", request(port, "GET", "/info", null, null));
        } finally {
            app.webServer().stop();
            app.context().close();
        }

        System.out.println();
        System.out.println("MultiChainSmokeTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    record Response(int status, String body) {}

    /**
     * 发起一次 HTTP 请求：组装请求行、头部（含 Content-Type/Content-Length 与可选
     * Authorization: Bearer）与请求体，读取完整响应并解析出状态码与响应正文。
     */
    static Response request(int port, String method, String path, String body, String bearer) throws Exception {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(8000);
            OutputStream out = s.getOutputStream();
            byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            StringBuilder h = new StringBuilder();
            h.append(method).append(' ').append(path).append(" HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n");
            if (body != null) {
                h.append("Content-Type: application/json\r\nContent-Length: ").append(bodyBytes.length).append("\r\n");
            }
            if (bearer != null) {
                h.append("Authorization: Bearer ").append(bearer).append("\r\n");
            }
            h.append("\r\n");
            out.write(h.toString().getBytes(StandardCharsets.UTF_8));
            if (body != null) out.write(bodyBytes);
            out.flush();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            s.getInputStream().transferTo(buf);
            String resp = buf.toString(StandardCharsets.UTF_8);
            int sp = resp.indexOf(' ');
            int sp2 = resp.indexOf(' ', sp + 1);
            int status = Integer.parseInt(resp.substring(sp + 1, sp2).trim());
            int idx = resp.indexOf("\r\n\r\n");
            String respBody = idx >= 0 ? resp.substring(idx + 4) : "";
            return new Response(status, respBody);
        }
    }

    static void expect(int expectedStatus, String label, Response resp) {
        check(resp.status() == expectedStatus, label + ": expected " + expectedStatus + " got " + resp.status()
                + " body=" + resp.body());
    }

    static void check(boolean condition, String label) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + label);
        } else {
            failed++;
            System.out.println("[FAIL] " + label);
        }
    }

    static String extract(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
