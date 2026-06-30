package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.boot.SummerApplication;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * End-to-end security smoke test: starts {@link SecurityTestApp} on an ephemeral
 * port and exercises login, URL-level authorization, method-level authorization,
 * and {@code @AuthenticationPrincipal} injection over loopback HTTP.
 *
 * <p>Run manually (not part of the build's TestRunner sweep):
 * <pre>java -cp ... cn.jiebaba.summer.test.security.SecuritySmokeTest</pre>
 */
public class SecuritySmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        // ephemeral port to avoid clashing with anything else on 8080
        System.setProperty("server.port", "0");
        System.setProperty("summer.security.enabled", "true");
        System.setProperty("summer.security.jwt.secret", "test-jwt-secret-for-smoke-test-32-bytes!");
        System.setProperty("summer.security.jwt.access-token-ttl", "3600");
        SummerApplication app = SummerApplication.run(SecurityTestApp.class, args);
        int port = app.webServer().port();
        Thread.sleep(400);
        try {
            // 1. public endpoint, no token
            expect(200, "public no-token", request(port, "GET", "/public/hello", null, null));

            // 2. login as admin
            Response login = request(port, "POST", "/login",
                    "{\"username\":\"admin\",\"password\":\"admin123\"}", null);
            expect(200, "admin login", login);
            String adminToken = extract(login.body, "\"accessToken\":\"([^\"]+)\"");
            check(adminToken != null, "admin token present");

            // 3. login with bad credentials
            expect(401, "bad login", request(port, "POST", "/login",
                    "{\"username\":\"admin\",\"password\":\"wrong\"}", null));

            // 4. /me without token -> 401
            expect(401, "me no-token", request(port, "GET", "/me", null, null));

            // 5. /me with admin token -> 200 + principal
            Response me = request(port, "GET", "/me", null, adminToken);
            expect(200, "me admin", me);
            check(me.body.contains("\"username\":\"admin\""), "me principal injected");

            // 6. /admin/info without token -> 401 (URL rule)
            expect(401, "admin no-token", request(port, "GET", "/admin/info", null, null));

            // 7. login as user
            Response userLogin = request(port, "POST", "/login",
                    "{\"username\":\"user\",\"password\":\"user123\"}", null);
            expect(200, "user login", userLogin);
            String userToken = extract(userLogin.body, "\"accessToken\":\"([^\"]+)\"");
            check(userToken != null, "user token present");

            // 8. /admin/info as user -> 403 (URL rule hasRole ADMIN)
            expect(403, "admin as user", request(port, "GET", "/admin/info", null, userToken));

            // 9. /admin/info as admin -> 200
            expect(200, "admin as admin", request(port, "GET", "/admin/info", null, adminToken));

            // 10. /secret as user -> 403 (method-level @PreAuthorize, URL permitAll)
            expect(403, "secret as user", request(port, "GET", "/secret", null, userToken));

            // 11. /secret as admin -> 200
            expect(200, "secret as admin", request(port, "GET", "/secret", null, adminToken));

            // 12. /secret without token -> 401 (method requires authentication)
            expect(401, "secret no-token", request(port, "GET", "/secret", null, null));

            // 13. /user/profile as user -> 200
            expect(200, "user profile as user", request(port, "GET", "/user/profile", null, userToken));

            // 14. tampered token -> 401
            expect(401, "tampered token", request(port, "GET", "/me", null, adminToken + "x"));

            // 15. unmatched path -> 404 (permitAll default, not 401)
            expect(404, "no-such-route 404", request(port, "GET", "/no-such-route", null, null));
        } finally {
            app.webServer().stop();
            app.context().close();
        }
        System.out.println("\nSecuritySmokeTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    record Response(int status, String body) {}

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
