package cn.jiebaba.summer.admin.system.user;

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
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户管理与个人中心真实 HTTP 测试：以随机端口、test 配置拉起完整应用，
 * 覆盖管理端权限（匿名 401 / 无权限 403）、用户 CRUD 与关系分配、
 * user_id=1 与自身保护、重置密码、个人资料白名单更新、修改密码验旧；
 * 所有响应递归检查不得包含 password/captcha/Authorization 等敏感字段。
 */
class UserApiTest {

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
    void userListRequiresTokenAndPermission() throws Exception {
        HttpResponse<String> anonymous = get("/api/system/users/list", null);
        assertEquals(401, anonymous.statusCode());
        assertRShape(anonymous, 401);

        // 普通用户 user 仅被授予系统管理目录（无按钮权限），访问用户列表应为 403
        String token = loginToken(USER, USER_PASSWORD);
        HttpResponse<String> forbidden = get("/api/system/users/list", token);
        assertEquals(403, forbidden.statusCode());
        assertRShape(forbidden, 403);
    }

    @Test
    void adminListReturnsRowsWithAssembledNamesAndNoSecrets() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        HttpResponse<String> response = get("/api/system/users/list?pageNum=1&pageSize=10", token);

        assertEquals(200, response.statusCode());
        Map<String, Object> body = json(response);
        assertEquals(200, code(body));
        assertNoSecrets(response.body());

        Map<?, ?> table = (Map<?, ?>) body.get("data");
        assertNotNull(table, "用户列表必须返回 TableData: " + response.body());
        assertEquals(2, ((Number) table.get("total")).intValue());
        List<?> rows = (List<?>) table.get("rows");
        Map<?, ?> admin = rows.stream()
                .map(r -> (Map<?, ?>) r)
                .filter(r -> "admin".equals(r.get("userName")))
                .findFirst().orElseThrow();
        assertEquals("研发部门", admin.get("deptName"));
        assertEquals(List.of("超级管理员"), admin.get("roleNames"));
        assertEquals(List.of("总经理"), admin.get("postNames"));
        assertFalse(admin.containsKey("password"), "用户行不得含密码: " + response.body());
    }

    @Test
    void adminGetByIdReturnsVoAndDeniedForUnknown() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);

        HttpResponse<String> found = get("/api/system/users/1", token);
        assertEquals(200, found.statusCode());
        assertNoSecrets(found.body());
        Map<?, ?> vo = (Map<?, ?>) json(found).get("data");
        assertEquals("admin", vo.get("userName"));
        assertEquals("研发部门", vo.get("deptName"));

        HttpResponse<String> missing = get("/api/system/users/999999", token);
        assertEquals(404, missing.statusCode());
        assertRShape(missing, 404);
    }

    @Test
    void createThenLoginWithNewPassword() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        String body = "{\"userName\":\"api_created\",\"nickName\":\"接口创建\","
                + "\"password\":\"secret66\",\"deptId\":2,"
                + "\"roleIds\":[2],\"postIds\":[4]}";
        try {
            HttpResponse<String> created = postJson("/api/system/users", body, token);
            assertEquals(200, created.statusCode(), "创建用户失败: " + created.body());
            assertNoSecrets(created.body());
            // 创建成功必须返回用户 ID（数字类型，非对象）
            assertNotNull(json(created).get("data"), "创建成功必须返回用户 ID: " + created.body());

            // 新用户可以直接登录（密码已 BCrypt 入库）
            String newToken = loginToken("api_created", "secret66");
            HttpResponse<String> profile = get("/api/system/profile", newToken);
            assertEquals(200, profile.statusCode());

            // 登录名唯一：重复创建 409
            HttpResponse<String> dup = postJson("/api/system/users", body, token);
            assertEquals(409, dup.statusCode());
            assertRShape(dup, 409);
        } finally {
            purgeUser("api_created");
        }
    }

    @Test
    void updateReplacesRelationsAndReturnsLatestVo() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        String createBody = "{\"userName\":\"api_update\",\"nickName\":\"更新前\","
                + "\"password\":\"secret66\",\"deptId\":2,"
                + "\"roleIds\":[2],\"postIds\":[4]}";
        try {
            postJson("/api/system/users", createBody, token);
            long userId = userIdOf("api_update");

            String updateBody = "{\"userId\":" + userId + ",\"nickName\":\"更新后\","
                    + "\"roleIds\":[1,2],\"postIds\":[1]}";
            HttpResponse<String> updated = putJson("/api/system/users", updateBody, token);
            assertEquals(200, updated.statusCode(), "更新失败: " + updated.body());

            HttpResponse<String> vo = get("/api/system/users/" + userId, token);
            assertEquals(200, vo.statusCode());
            assertNoSecrets(vo.body());
            Map<?, ?> data = (Map<?, ?>) json(vo).get("data");
            assertEquals("更新后", data.get("nickName"));
            Set<String> roleIds = new HashSet<>();
            for (Object id : (List<?>) data.get("roleIds")) {
                roleIds.add(String.valueOf(id));
            }
            assertEquals(Set.of("1", "2"), roleIds);
            List<?> postIds = (List<?>) data.get("postIds");
            assertEquals(1, postIds.size());
            assertEquals("1", String.valueOf(postIds.get(0)));
        } finally {
            purgeUser("api_update");
        }
    }

    @Test
    void deleteSuperAdminOrSelfReturns409() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);

        HttpResponse<String> superAdmin = delete("/api/system/users/1", token);
        assertEquals(409, superAdmin.statusCode());
        assertRShape(superAdmin, 409);

        // admin 即 user_id=1，删除自身同样 409
        HttpResponse<String> self = delete("/api/system/users/1", token);
        assertEquals(409, self.statusCode());
    }

    @Test
    void statusChangeGuardsSuperAdmin() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);

        HttpResponse<String> response = putJson("/api/system/users/1/status",
                "{\"status\":\"1\"}", token);
        assertEquals(409, response.statusCode());
        assertRShape(response, 409);
    }

    @Test
    void resetPasswordGuardsSelfAndAllowsOtherUser() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        String createBody = "{\"userName\":\"api_reset\",\"nickName\":\"重置对象\","
                + "\"password\":\"secret66\",\"deptId\":2,\"roleIds\":[2]}";
        try {
            postJson("/api/system/users", createBody, token);
            long userId = userIdOf("api_reset");

            HttpResponse<String> self = putJson("/api/system/users/1/password",
                    "{\"password\":\"hacked66\"}", token);
            assertEquals(409, self.statusCode(), "不允许经管理端重置超级管理员密码");

            HttpResponse<String> ok = putJson("/api/system/users/" + userId + "/password",
                    "{\"password\":\"reset66xx\"}", token);
            assertEquals(200, ok.statusCode(), "重置密码失败: " + ok.body());
            assertNoSecrets(ok.body());

            loginToken("api_reset", "reset66xx");
        } finally {
            purgeUser("api_reset");
        }
    }

    @Test
    void profileRequiresAuthAndReturnsOwnDataOnly() throws Exception {
        HttpResponse<String> anonymous = get("/api/system/profile", null);
        assertEquals(401, anonymous.statusCode());
        assertRShape(anonymous, 401);

        String token = loginToken(USER, USER_PASSWORD);
        HttpResponse<String> response = get("/api/system/profile", token);
        assertEquals(200, response.statusCode());
        assertNoSecrets(response.body());
        Map<?, ?> data = (Map<?, ?>) json(response).get("data");
        assertEquals(USER, data.get("userName"));
        assertFalse(data.containsKey("password"), "个人资料不得含密码: " + response.body());
    }

    @Test
    void profileUpdateAppliesWhitelist() throws Exception {
        String token = loginToken(USER, USER_PASSWORD);
        String body = "{\"nickName\":\"普通用户改\",\"email\":\"new@jiebaba.cn\","
                + "\"phonenumber\":\"13800000000\",\"sex\":\"1\"}";

        HttpResponse<String> updated = putJson("/api/system/profile", body, token);
        assertEquals(200, updated.statusCode(), "资料更新失败: " + updated.body());

        HttpResponse<String> profile = get("/api/system/profile", token);
        Map<?, ?> data = (Map<?, ?>) json(profile).get("data");
        assertEquals("普通用户改", data.get("nickName"));
        assertEquals("new@jiebaba.cn", data.get("email"));
        assertEquals("普通角色", ((List<?>) data.get("roleNames")).get(0));
        // user 归属部门 3，状态仍为 0：白名单字段之外一律不动
        assertEquals("0", data.get("status"));
    }

    @Test
    void changePasswordVerifiesOldThenNewPasswordWorks() throws Exception {
        String createBody = "{\"userName\":\"api_pwd\",\"nickName\":\"改密对象\","
                + "\"password\":\"secret66\",\"deptId\":2,\"roleIds\":[2]}";
        String adminToken = loginToken(ADMIN, ADMIN_PASSWORD);
        try {
            postJson("/api/system/users", createBody, adminToken);
            String token = loginToken("api_pwd", "secret66");

            HttpResponse<String> wrong = putJson("/api/system/profile/password",
                    "{\"oldPassword\":\"bad-old\",\"newPassword\":\"newpass66\"}", token);
            assertEquals(409, wrong.statusCode());
            assertRShape(wrong, 409);

            HttpResponse<String> ok = putJson("/api/system/profile/password",
                    "{\"oldPassword\":\"secret66\",\"newPassword\":\"newpass66\"}", token);
            assertEquals(200, ok.statusCode(), "修改密码失败: " + ok.body());
            assertNoSecrets(ok.body());

            // 旧密码失效、新密码可登录
            HttpResponse<String> oldLogin = postJson("/api/auth/login",
                    loginBody("api_pwd", "secret66"), null);
            assertEquals(401, oldLogin.statusCode());
            loginToken("api_pwd", "newpass66");
        } finally {
            purgeUser("api_pwd");
        }
    }

    @Test
    void managementEndpointsRejectUserWithoutPermission() throws Exception {
        String token = loginToken(USER, USER_PASSWORD);

        HttpResponse<String> list = get("/api/system/users/list", token);
        assertEquals(403, list.statusCode(), "查询用户管理应 403");
        assertRShape(list, 403);

        HttpResponse<String> create = postJson("/api/system/users",
                "{\"userName\":\"x\",\"password\":\"secret66\",\"deptId\":2}", token);
        assertEquals(403, create.statusCode(), "新增用户应 403");
        assertRShape(create, 403);

        HttpResponse<String> update = putJson("/api/system/users",
                "{\"userId\":2,\"nickName\":\"x\"}", token);
        assertEquals(403, update.statusCode(), "修改用户应 403");
        assertRShape(update, 403);
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

    /** 敏感字段扫描：响应体任何位置不得出现密码、验证码或令牌头。 */
    private static void assertNoSecrets(String body) {
        String lower = body.toLowerCase();
        assertFalse(lower.contains("\"password\""), "响应不得包含 password 字段: " + body);
        assertFalse(lower.contains("captcha"), "响应不得包含 captcha: " + body);
        assertFalse(body.contains("Authorization"), "响应不得回显 Authorization: " + body);
    }

    private static long userIdOf(String userName) throws Exception {
        try (Connection conn = testConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT user_id FROM sys_user WHERE user_name = ?")) {
            ps.setString(1, userName);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "用户必须存在: " + userName);
                return rs.getLong(1);
            }
        }
    }

    /** 物理清理测试用户及其关系（仅 summer_admin_test），先清关系再删用户。 */
    private static void purgeUser(String userName) throws Exception {
        long userId = userIdOf(userName);
        try (Connection conn = testConnection();
             PreparedStatement rel = conn.prepareStatement("DELETE FROM sys_user_role WHERE user_id = ?");
             PreparedStatement post = conn.prepareStatement("DELETE FROM sys_user_post WHERE user_id = ?");
             PreparedStatement user = conn.prepareStatement("DELETE FROM sys_user WHERE user_id = ?")) {
            rel.setLong(1, userId);
            rel.executeUpdate();
            post.setLong(1, userId);
            post.executeUpdate();
            user.setLong(1, userId);
            user.executeUpdate();
        }
    }

    private static HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, null, token);
    }

    private static HttpResponse<String> delete(String path, String token) throws Exception {
        return send("DELETE", path, null, token);
    }

    private static HttpResponse<String> postJson(String path, String body, String token) throws Exception {
        return send("POST", path, body, token);
    }

    private static HttpResponse<String> putJson(String path, String body, String token) throws Exception {
        return send("PUT", path, body, token);
    }

    private static HttpResponse<String> send(String method, String path, String body, String token)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5));
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
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

    private static Connection testConnection() throws SQLException {
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
