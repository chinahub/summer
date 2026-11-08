package cn.jiebaba.summer.admin.dashboard;

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
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 首页汇总真实 HTTP 测试：匿名 401；任何已认证用户（含无管理权限的 user）
 * 均可访问 /api/dashboard/summary 并拿到真实四项计数（种子：用户 2、角色 2、
 * 部门 4、已发布公告 1）。凭据只从环境变量读取。
 */
class DashboardApiTest {

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
    void summaryRequiresAuthentication() throws Exception {
        HttpResponse<String> anonymous = get("/api/dashboard/summary", null);
        assertEquals(401, anonymous.statusCode());
    }

    @Test
    void summaryAvailableToAnyAuthenticatedUserWithRealCounts() throws Exception {
        // 普通用户无任何管理权限，但首页汇总只要求登录
        String userToken = loginToken(USER, USER_PASSWORD);
        HttpResponse<String> response = get("/api/dashboard/summary", userToken);
        assertEquals(200, response.statusCode(), "已认证用户应可访问汇总: " + response.body());

        Map<?, ?> data = (Map<?, ?>) json(response).get("data");
        assertNotNull(data, "汇总必须返回 data: " + response.body());
        assertEquals(2, ((Number) data.get("userCount")).longValue(), "用户数: " + response.body());
        assertEquals(2, ((Number) data.get("roleCount")).longValue(), "角色数: " + response.body());
        assertEquals(4, ((Number) data.get("deptCount")).longValue(), "部门数: " + response.body());
        assertEquals(1, ((Number) data.get("noticeCount")).longValue(), "已发布公告数: " + response.body());
        assertEquals(Set.of("userCount", "roleCount", "deptCount", "noticeCount"), data.keySet(),
                "汇总只含四项计数，不得泄露其他字段: " + response.body());
    }

    @Test
    void summaryConsistentForAdmin() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        HttpResponse<String> response = get("/api/dashboard/summary", token);
        assertEquals(200, response.statusCode());

        Map<?, ?> data = (Map<?, ?>) json(response).get("data");
        assertEquals(2, ((Number) data.get("userCount")).longValue());
        assertEquals(2, ((Number) data.get("roleCount")).longValue());
        assertEquals(4, ((Number) data.get("deptCount")).longValue());
        assertEquals(1, ((Number) data.get("noticeCount")).longValue());
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

    private static HttpResponse<String> postJson(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(HttpResponse<String> response) {
        return (Map<String, Object>) Json.parse(response.body());
    }
}
