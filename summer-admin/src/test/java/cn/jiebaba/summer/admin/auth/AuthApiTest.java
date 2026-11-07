package cn.jiebaba.summer.admin.auth;

import cn.jiebaba.summer.admin.SummerAdminApplication;
import cn.jiebaba.summer.admin.testsupport.MySqlTestDatabase;
import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.core.json.Json;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证链路真实 HTTP 测试：以随机端口、test 配置（验证码关闭 + 测试专用 JWT 密钥）
 * 拉起完整应用，经真实请求验证登录成功/失败、停用账号、无令牌 401、无权限 403、
 * 登出需令牌、登录日志写入与敏感信息脱敏。R 形错误契约（401/403）经
 * ApiErrorResponseFilter 规范化验证；测试只解码令牌 payload 的 sub/authorities/iat/exp/typ，
 * 不检查也不记录真实密钥。
 */
class AuthApiTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String USER = "user";
    private static final String USER_PASSWORD = "user123";

    private static SummerApplication app;
    private static HttpClient http;
    private static int port;

    /** 启动应用：先重建隔离测试库，再以随机端口、test 配置拉起。 */
    @BeforeAll
    static void startApplication() {
        MySqlTestDatabase.reset();
        app = SummerApplication.run(SummerAdminApplication.class,
                new String[]{"--server.port=0", "--summer.profiles.active=test"});
        port = app.webServer().port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** 关闭应用：停止 Web 服务并关闭 IoC 容器。 */
    @AfterAll
    static void stopApplication() {
        if (app != null) {
            app.webServer().stop();
            app.context().close();
            app = null;
        }
    }

    @Test
    void loginSuccessReturnsSnakeCaseTokenFields() throws Exception {
        HttpResponse<String> response = postJson("/api/auth/login",
                loginBody(ADMIN, ADMIN_PASSWORD), null);
        assertEquals(200, response.statusCode());
        Map<String, Object> body = json(response);
        assertEquals(200, code(body));
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertNotNull(data, "登录成功必须返回 data: " + response.body());
        assertNotNull(data.get("access_token"), "data 必须含 access_token");
        assertEquals("Bearer", data.get("token_type"));
        assertEquals(7200, ((Number) data.get("expires_in")).intValue());
        // 响应绝不回显密码
        assertFalse(response.body().contains(ADMIN_PASSWORD), "响应不得包含密码: " + response.body());
    }

    @Test
    void issuedTokenCarriesSubjectAuthoritiesAndTtlClaims() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        Map<?, ?> payload = decodePayload(token);
        assertEquals(ADMIN, payload.get("sub"));
        assertEquals("access", payload.get("typ"));
        long iat = ((Number) payload.get("iat")).longValue();
        long exp = ((Number) payload.get("exp")).longValue();
        assertEquals(7200L, exp - iat, "默认 TTL 必须为 7200 秒");
        List<?> authorities = (List<?>) payload.get("authorities");
        assertTrue(authorities.contains("system:user:list"), "权限标识应进入 authorities claim");
        assertTrue(authorities.contains("ROLE_admin"), "角色应进入 authorities claim");
    }

    @Test
    void wrongPasswordReturns401RAndWritesLoginLog() throws Exception {
        String wrongPassword = "wrong-pass-7f3a";
        HttpResponse<String> response = postJson("/api/auth/login",
                loginBody(ADMIN, wrongPassword), null);
        assertEquals(401, response.statusCode());
        assertRShape(response, 401);
        assertFalse(response.body().contains(wrongPassword), "401 响应不得包含密码: " + response.body());
        // 失败登录写 sys_login_log（status=1），消息不得包含密码
        List<String> msgs = loginLogMessages(ADMIN, "1");
        assertFalse(msgs.isEmpty(), "密码错误必须写登录日志");
        for (String msg : msgs) {
            assertFalse(msg.contains(wrongPassword), "登录日志不得包含密码: " + msg);
        }
    }

    @Test
    void disabledAccountReturns401AndWritesLoginLog() throws Exception {
        setUserStatus(2L, "1");
        try {
            HttpResponse<String> response = postJson("/api/auth/login",
                    loginBody(USER, USER_PASSWORD), null);
            assertEquals(401, response.statusCode());
            assertRShape(response, 401);
            assertFalse(response.body().contains(USER_PASSWORD), "401 响应不得包含密码: " + response.body());
            List<String> msgs = loginLogMessages(USER, "1");
            assertTrue(msgs.stream().anyMatch(m -> m.contains("停用")),
                    "停用账号必须记录停用消息，实际: " + msgs);
        } finally {
            setUserStatus(2L, "0");
        }
    }

    @Test
    void userinfoRequiresTokenAndReturnsProfileWithoutPassword() throws Exception {
        HttpResponse<String> anonymous = get("/api/auth/userinfo", null);
        assertEquals(401, anonymous.statusCode());
        assertRShape(anonymous, 401);

        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        HttpResponse<String> response = get("/api/auth/userinfo", token);
        assertEquals(200, response.statusCode());
        Map<?, ?> data = (Map<?, ?>) json(response).get("data");
        assertNotNull(data);
        Map<?, ?> user = (Map<?, ?>) data.get("user");
        assertNotNull(user, "userinfo 必须含 user: " + response.body());
        assertEquals(ADMIN, user.get("userName"));
        assertFalse(user.containsKey("password"), "userinfo 不得返回密码字段");
        List<?> roles = (List<?>) data.get("roles");
        assertTrue(roles.contains("admin"), "角色集合应含 admin: " + response.body());
        List<?> permissions = (List<?>) data.get("permissions");
        assertTrue(permissions.contains("system:user:list"), "权限集合应含菜单权限");
        assertFalse(permissions.contains("ROLE_admin"), "权限集合不得混入角色标识");
    }

    @Test
    void routersRequiresTokenAndReturnsMenus() throws Exception {
        HttpResponse<String> anonymous = get("/api/auth/routers", null);
        assertEquals(401, anonymous.statusCode());
        assertRShape(anonymous, 401);

        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        HttpResponse<String> response = get("/api/auth/routers", token);
        assertEquals(200, response.statusCode());
        List<?> routers = (List<?>) json(response).get("data");
        assertNotNull(routers);
        assertFalse(routers.isEmpty(), "admin 应返回动态路由");
    }

    @Test
    void logoutRequiresTokenAndWritesLoginLog() throws Exception {
        HttpResponse<String> anonymous = postJson("/api/auth/logout", "{}", null);
        assertEquals(401, anonymous.statusCode());
        assertRShape(anonymous, 401);

        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        HttpResponse<String> response = postJson("/api/auth/logout", "{}", token);
        assertEquals(200, response.statusCode());
        assertEquals(200, code(json(response)));
        List<String> msgs = loginLogMessages(ADMIN, "0");
        assertTrue(msgs.stream().anyMatch(m -> m.contains("退出")),
                "登出必须记录操作日志，实际: " + msgs);
    }

    @Test
    void endpointWithoutPermissionReturns403R() throws Exception {
        // 普通用户 user 仅被授予系统管理目录（无按钮权限），访问角色列表应为 403
        String token = loginToken(USER, USER_PASSWORD);
        HttpResponse<String> response = get("/api/system/roles/list", token);
        assertEquals(403, response.statusCode());
        assertRShape(response, 403);
    }

    @Test
    void captchaEndpointExposesSwitch() throws Exception {
        HttpResponse<String> response = get("/api/auth/captcha", null);
        assertEquals(200, response.statusCode());
        Map<?, ?> data = (Map<?, ?>) json(response).get("data");
        assertNotNull(data, "验证码接口必须返回 data: " + response.body());
        // 测试配置关闭验证码：开关对前端可见
        assertEquals(Boolean.FALSE, data.get("captchaEnabled"));
    }

    // ---- 辅助方法 -------------------------------------------------------

    /** 登录并返回 access_token；失败时带响应体断言。 */
    private static String loginToken(String username, String password) throws Exception {
        HttpResponse<String> response = postJson("/api/auth/login", loginBody(username, password), null);
        assertEquals(200, response.statusCode(), "登录失败: " + response.body());
        Map<?, ?> data = (Map<?, ?>) json(response).get("data");
        return (String) data.get("access_token");
    }

    private static String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    /** 断言响应体为 R 形错误（{code,msg}，不含框架 timestamp/path 字段）。 */
    private static void assertRShape(HttpResponse<String> response, int expectedCode) {
        Map<String, Object> body = json(response);
        assertEquals(expectedCode, code(body));
        assertNotNull(body.get("msg"), "R 形错误必须含 msg: " + response.body());
        assertFalse(body.containsKey("timestamp"), "R 格式不得含框架 timestamp 字段: " + response.body());
        assertFalse(body.containsKey("path"), "R 格式不得含框架 path 字段: " + response.body());
    }

    /** 手工解码 JWT payload（不验签、不接触密钥），仅用于断言 claims。 */
    private static Map<?, ?> decodePayload(String token) {
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "令牌必须是紧凑 JWS 三段结构");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return (Map<?, ?>) Json.parse(new String(payload, StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postJson(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(HttpResponse<String> response) {
        return (Map<String, Object>) Json.parse(response.body());
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }

    /** 查询指定用户与状态的登录日志消息列表（只碰 summer_admin_test）。 */
    private static List<String> loginLogMessages(String userName, String status) throws Exception {
        try (Connection conn = testConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT msg FROM sys_login_log WHERE user_name = ? AND status = ?")) {
            ps.setString(1, userName);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> msgs = new java.util.ArrayList<>();
                while (rs.next()) {
                    msgs.add(rs.getString(1));
                }
                return msgs;
            }
        }
    }

    /** 直接改测试库用户状态（仅 summer_admin_test），用后即恢复。 */
    private static void setUserStatus(long userId, String status) throws Exception {
        try (Connection conn = testConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE sys_user SET status = ? WHERE user_id = ?")) {
            ps.setString(1, status);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    private static Connection testConnection() throws Exception {
        return DriverManager.getConnection(MySqlTestDatabase.testJdbcUrl(),
                requiredEnv("SUMMER_DATASOURCE_USERNAME"), requiredEnv("SUMMER_DATASOURCE_PASSWORD"));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }
}
