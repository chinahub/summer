package cn.jiebaba.summer.admin.system.notice;

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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知公告真实 HTTP 测试：管理端 system:notice:* 权限（匿名 401 / 无权限 403）、
 * 草稿仅管理端可见、普通已认证用户只看已发布公告、发布状态切换与校验规则。
 * 凭据只从环境变量读取。
 */
class NoticeApiTest {

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
    void managementListRequiresPermissionButPublishedListOnlyAuth() throws Exception {
        HttpResponse<String> anonymous = get("/api/system/notices/list", null);
        assertEquals(401, anonymous.statusCode());
        assertRShape(anonymous, 401);

        String userToken = loginToken(USER, USER_PASSWORD);
        HttpResponse<String> forbidden = get("/api/system/notices/list", userToken);
        assertEquals(403, forbidden.statusCode(), "管理端列表应要求 system:notice:query");
        assertRShape(forbidden, 403);

        // 普通已认证用户可访问已发布列表（种子含 1 条已发布公告）
        HttpResponse<String> published = get("/api/system/notices/published/list", userToken);
        assertEquals(200, published.statusCode(), "已发布列表应对已认证用户开放: " + published.body());
        Map<?, ?> table = (Map<?, ?>) json(published).get("data");
        assertEquals(1, ((Number) table.get("total")).intValue());
    }

    @Test
    void draftVisibleToManagementOnlyUntilPublished() throws Exception {
        String admin = loginToken(ADMIN, ADMIN_PASSWORD);
        String user = loginToken(USER, USER_PASSWORD);
        long noticeId = -1;
        try {
            HttpResponse<String> created = postJson("/api/system/notices",
                    "{\"noticeTitle\":\"接口草稿\",\"noticeType\":\"1\","
                            + "\"noticeContent\":\"草稿内容\",\"status\":\"2\"}", admin);
            assertEquals(200, created.statusCode(), "新增公告失败: " + created.body());
            noticeId = ((Number) json(created).get("data")).longValue();

            // 管理端可见草稿
            HttpResponse<String> management = get("/api/system/notices/list?status=2", admin);
            assertEquals(200, management.statusCode());
            assertTrue(management.body().contains("接口草稿"), "管理端应可见草稿");

            // 普通用户看不到草稿
            HttpResponse<String> published = get("/api/system/notices/published/list", user);
            assertFalse(published.body().contains("接口草稿"), "草稿不得进入已发布列表");

            // 发布后普通用户可见
            HttpResponse<String> publish = putJson("/api/system/notices",
                    "{\"noticeId\":" + noticeId + ",\"noticeTitle\":\"接口草稿\","
                            + "\"noticeType\":\"1\",\"status\":\"0\"}", admin);
            assertEquals(200, publish.statusCode(), "发布公告失败: " + publish.body());
            HttpResponse<String> afterPublish = get("/api/system/notices/published/list", user);
            assertTrue(afterPublish.body().contains("接口草稿"), "发布后应进入已发布列表");

            // 关闭后再次退出已发布列表
            HttpResponse<String> close = putJson("/api/system/notices",
                    "{\"noticeId\":" + noticeId + ",\"noticeTitle\":\"接口草稿\","
                            + "\"noticeType\":\"1\",\"status\":\"1\"}", admin);
            assertEquals(200, close.statusCode());
            HttpResponse<String> afterClose = get("/api/system/notices/published/list", user);
            assertFalse(afterClose.body().contains("接口草稿"), "关闭后不得留在已发布列表");
        } finally {
            purgeNotice(noticeId);
        }
    }

    @Test
    void validationRules() throws Exception {
        String admin = loginToken(ADMIN, ADMIN_PASSWORD);

        HttpResponse<String> blankTitle = postJson("/api/system/notices",
                "{\"noticeTitle\":\"  \"}", admin);
        assertEquals(400, blankTitle.statusCode());
        assertRShape(blankTitle, 400);

        HttpResponse<String> badStatus = postJson("/api/system/notices",
                "{\"noticeTitle\":\"坏状态\",\"status\":\"9\"}", admin);
        assertEquals(400, badStatus.statusCode(), "非法状态应 400");
        assertRShape(badStatus, 400);

        HttpResponse<String> noId = putJson("/api/system/notices",
                "{\"noticeTitle\":\"无ID\"}", admin);
        assertEquals(400, noId.statusCode(), "修改缺 ID 应 400");

        HttpResponse<String> missing = putJson("/api/system/notices",
                "{\"noticeId\":999999,\"noticeTitle\":\"不存在\"}", admin);
        assertEquals(404, missing.statusCode());
        assertRShape(missing, 404);

        HttpResponse<String> missingDelete = delete("/api/system/notices/999999", admin);
        assertEquals(404, missingDelete.statusCode());
        assertRShape(missingDelete, 404);
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

    /** 物理清理测试公告（仅 summer_admin_test），-1 表示无需清理。 */
    private static void purgeNotice(long noticeId) throws Exception {
        if (noticeId < 0) {
            return;
        }
        try (Connection conn = testConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM sys_notice WHERE notice_id = ?")) {
            ps.setLong(1, noticeId);
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
