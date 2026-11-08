package cn.jiebaba.summer.admin.system.config;

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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数管理真实 HTTP 测试：匿名 401 / 无权限 403、分页与过滤、键唯一（409）、
 * 空键 400、CRUD 全流程，以及内置初始化标记 sys.init.marker 的 API 级保护
 * （列表不暴露、详情 404、修改/删除 409）。凭据只从环境变量读取。
 */
class ConfigApiTest {

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
    void listRequiresTokenAndPermission() throws Exception {
        HttpResponse<String> anonymous = get("/api/system/configs/list", null);
        assertEquals(401, anonymous.statusCode());
        assertRShape(anonymous, 401);

        String token = loginToken(USER, USER_PASSWORD);
        HttpResponse<String> forbidden = get("/api/system/configs/list", token);
        assertEquals(403, forbidden.statusCode());
        assertRShape(forbidden, 403);
    }

    @Test
    void adminListExcludesMarkerAndPaginates() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);

        HttpResponse<String> first = get("/api/system/configs/list?pageNum=1&pageSize=2", token);
        assertEquals(200, first.statusCode(), "参数列表失败: " + first.body());
        Map<?, ?> table = (Map<?, ?>) json(first).get("data");
        assertNotNull(table, "参数列表必须返回 TableData: " + first.body());
        // 种子 4 条参数（含初始化标记），标记被排除后 total=3
        assertEquals(3, ((Number) table.get("total")).intValue());
        assertEquals(2, ((List<?>) table.get("rows")).size());
        assertNoMarker(first.body());

        HttpResponse<String> second = get("/api/system/configs/list?pageNum=2&pageSize=2", token);
        assertEquals(200, second.statusCode());
        Map<?, ?> pageTwo = (Map<?, ?>) json(second).get("data");
        assertEquals(3, ((Number) pageTwo.get("total")).intValue());
        assertEquals(1, ((List<?>) pageTwo.get("rows")).size());
        assertNoMarker(second.body());
    }

    @Test
    void createValidatesAndEnforcesUniqueKey() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);

        HttpResponse<String> blankKey = postJson("/api/system/configs",
                "{\"configName\":\"空键\",\"configKey\":\"  \"}", token);
        assertEquals(400, blankKey.statusCode());
        assertRShape(blankKey, 400);

        String body = "{\"configName\":\"会话超时\",\"configKey\":\"sys.session.timeout\","
                + "\"configValue\":\"30\"}";
        long configId = -1;
        try {
            HttpResponse<String> created = postJson("/api/system/configs", body, token);
            assertEquals(200, created.statusCode(), "新增参数失败: " + created.body());
            configId = ((Number) json(created).get("data")).longValue();

            HttpResponse<String> duplicate = postJson("/api/system/configs",
                    "{\"configName\":\"重复\",\"configKey\":\"sys.session.timeout\"}", token);
            assertEquals(409, duplicate.statusCode(), "键名必须唯一");
            assertRShape(duplicate, 409);

            HttpResponse<String> seedDuplicate = postJson("/api/system/configs",
                    "{\"configName\":\"抢种子键\",\"configKey\":\"sys.user.initPassword\"}", token);
            assertEquals(409, seedDuplicate.statusCode(), "种子键同样唯一");
        } finally {
            purgeConfig(configId);
        }
    }

    @Test
    void updateAndDeleteRoundTrip() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        long configId = -1;
        try {
            HttpResponse<String> created = postJson("/api/system/configs",
                    "{\"configName\":\"临时参数\",\"configKey\":\"sys.api.temp\",\"configValue\":\"1\"}",
                    token);
            assertEquals(200, created.statusCode());
            configId = ((Number) json(created).get("data")).longValue();

            HttpResponse<String> updated = putJson("/api/system/configs",
                    "{\"configId\":" + configId + ",\"configName\":\"临时参数改\","
                            + "\"configKey\":\"sys.api.temp\",\"configValue\":\"2\"}", token);
            assertEquals(200, updated.statusCode(), "修改参数失败: " + updated.body());

            HttpResponse<String> detail = get("/api/system/configs/" + configId, token);
            assertEquals(200, detail.statusCode());
            Map<?, ?> data = (Map<?, ?>) json(detail).get("data");
            assertEquals("临时参数改", data.get("configName"));
            assertEquals("2", data.get("configValue"));

            HttpResponse<String> deleted = delete("/api/system/configs/" + configId, token);
            assertEquals(200, deleted.statusCode());

            HttpResponse<String> missing = get("/api/system/configs/" + configId, token);
            assertEquals(404, missing.statusCode());
            assertRShape(missing, 404);
        } finally {
            purgeConfig(configId);
        }
    }

    @Test
    void initMarkerProtectedOverApi() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        // 种子约定：sys.init.marker 落在 config_id=100
        long markerId = 100L;

        HttpResponse<String> update = putJson("/api/system/configs",
                "{\"configId\":" + markerId + ",\"configName\":\"篡改\","
                        + "\"configKey\":\"sys.init.marker\",\"configValue\":\"0\"}", token);
        assertEquals(409, update.statusCode(), "初始化标记不允许修改");
        assertRShape(update, 409);

        HttpResponse<String> delete = delete("/api/system/configs/" + markerId, token);
        assertEquals(409, delete.statusCode(), "初始化标记不允许删除");
        assertRShape(delete, 409);

        HttpResponse<String> detail = get("/api/system/configs/" + markerId, token);
        assertEquals(404, detail.statusCode(), "初始化标记不作为业务数据暴露");

        // 改名抢注标记键同样被拒绝
        HttpResponse<String> rename = putJson("/api/system/configs",
                "{\"configId\":1,\"configName\":\"登录验证码开关\","
                        + "\"configKey\":\"sys.init.marker\"}", token);
        assertEquals(409, rename.statusCode(), "普通参数不得改名为初始化标记键");
    }

    // ---- 辅助方法 -------------------------------------------------------

    /** 断言列表响应中不得出现初始化标记键。 */
    private static void assertNoMarker(String body) {
        assertFalse(body.contains("sys.init.marker"), "列表不得暴露初始化标记: " + body);
    }

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

    /** 物理清理测试参数（仅 summer_admin_test），-1 表示无需清理。 */
    private static void purgeConfig(long configId) throws Exception {
        if (configId < 0) {
            return;
        }
        try (Connection conn = testConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM sys_config WHERE config_id = ?")) {
            ps.setLong(1, configId);
            ps.executeUpdate();
        }
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
