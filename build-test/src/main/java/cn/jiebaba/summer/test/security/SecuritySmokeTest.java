package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.boot.SummerApplication;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 端到端安全冒烟测试：在临时端口启动 {@link SecurityTestApp}，通过回环 HTTP
 * 验证登录、URL 级授权、方法级授权以及 {@code @AuthenticationPrincipal} 注入。
 *
 * <p>手动运行（不纳入构建的 TestRunner 扫描）：
 * <pre>java -cp ... cn.jiebaba.summer.test.security.SecuritySmokeTest</pre>
 */
public class SecuritySmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    /**
     * 安全冒烟测试入口：在临时端口启动应用，依次验证公开端点、登录、
     * /me、URL 级与方法级授权、token 篡改与未匹配路径等场景，结束后关闭服务。
     */
    public static void main(String[] args) throws Exception {
        // 使用临时端口，避免与 8080 上的其他服务冲突
        System.setProperty("server.port", "0");
        System.setProperty("summer.security.enabled", "true");
        System.setProperty("summer.security.jwt.secret", "test-jwt-secret-for-smoke-test-32-bytes!");
        System.setProperty("summer.security.jwt.access-token-ttl", "3600");
        SummerApplication app = SummerApplication.run(SecurityTestApp.class, args);
        int port = app.webServer().port();
        Thread.sleep(400);
        try {
            // 1. 公开端点，无 token
            expect(200, "public no-token", request(port, "GET", "/public/hello", null, null));

            // 2. 以 admin 身份登录
            Response login = request(port, "POST", "/login",
                    "{\"username\":\"admin\",\"password\":\"admin123\"}", null);
            expect(200, "admin login", login);
            String adminToken = extract(login.body, "\"accessToken\":\"([^\"]+)\"");
            check(adminToken != null, "admin token present");

            // 3. 使用错误凭据登录
            expect(401, "bad login", request(port, "POST", "/login",
                    "{\"username\":\"admin\",\"password\":\"wrong\"}", null));

            // 4. 无 token 访问 /me -> 401
            expect(401, "me no-token", request(port, "GET", "/me", null, null));

            // 5. 携带 admin token 访问 /me -> 200 且注入 principal
            Response me = request(port, "GET", "/me", null, adminToken);
            expect(200, "me admin", me);
            check(me.body.contains("\"username\":\"admin\""), "me principal injected");

            // 6. 无 token 访问 /admin/info -> 401（URL 规则）
            expect(401, "admin no-token", request(port, "GET", "/admin/info", null, null));

            // 7. 以 user 身份登录
            Response userLogin = request(port, "POST", "/login",
                    "{\"username\":\"user\",\"password\":\"user123\"}", null);
            expect(200, "user login", userLogin);
            String userToken = extract(userLogin.body, "\"accessToken\":\"([^\"]+)\"");
            check(userToken != null, "user token present");

            // 8. 以 user 访问 /admin/info -> 403（URL 规则要求 hasRole ADMIN）
            expect(403, "admin as user", request(port, "GET", "/admin/info", null, userToken));

            // 9. 以 admin 访问 /admin/info -> 200
            expect(200, "admin as admin", request(port, "GET", "/admin/info", null, adminToken));

            // 刷新令牌：用 refreshToken 换取新的访问令牌
            String adminRefresh = extract(login.body, "\"refreshToken\":\"([^\"]+)\"");
            check(adminRefresh != null, "admin refresh token present");
            Response refreshResp = request(port, "POST", "/refresh",
                    "{\"refreshToken\":\"" + adminRefresh + "\"}", null);
            expect(200, "refresh token ok", refreshResp);
            String refreshedAccess = extract(refreshResp.body, "\"accessToken\":\"([^\"]+)\"");
            check(refreshedAccess != null, "refreshed access token present");
            // 用刷新后的 access 令牌访问 /me 应成功
            expect(200, "me with refreshed token", request(port, "GET", "/me", null, refreshedAccess));
            // access 令牌不能当作 refreshToken 使用 -> 401
            expect(401, "access token cannot refresh", request(port, "POST", "/refresh",
                    "{\"refreshToken\":\"" + adminToken + "\"}", null));
            // 篡改的 refreshToken -> 401
            expect(401, "tampered refresh token", request(port, "POST", "/refresh",
                    "{\"refreshToken\":\"" + adminRefresh + "x\"}", null));

            // 10. 以 user 访问 /secret -> 403（方法级 @PreAuthorize，URL permitAll）
            expect(403, "secret as user", request(port, "GET", "/secret", null, userToken));

            // 11. 以 admin 访问 /secret -> 200
            expect(200, "secret as admin", request(port, "GET", "/secret", null, adminToken));

            // 12. 无 token 访问 /secret -> 401（方法要求认证）
            expect(401, "secret no-token", request(port, "GET", "/secret", null, null));

            // 13. 以 user 访问 /user/profile -> 200
            expect(200, "user profile as user", request(port, "GET", "/user/profile", null, userToken));

            // 14. 篡改的 token -> 401
            expect(401, "tampered token", request(port, "GET", "/me", null, adminToken + "x"));

            // 15. 未匹配路径 -> 404（默认 permitAll，而非 401）
            expect(404, "no-such-route 404", request(port, "GET", "/no-such-route", null, null));
        } finally {
            app.webServer().stop();
            app.context().close();
        }
        System.out.println("\nSecuritySmokeTest: " + passed + " passed, " + failed + " failed");
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
