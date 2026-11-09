package cn.jiebaba.summer.admin.system.role;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 角色关系读回接口真实 HTTP 测试：以随机端口、test 配置拉起完整应用，
 * 覆盖 GET /api/system/roles/{roleId}/menus 与 /api/system/roles/{roleId}/depts
 * 的匿名 401、无权限 403、授权读回（与替换写入一致）、未知角色 404。
 */
class RoleApiTest {

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
    void relationReadBackRequiresTokenAndPermission() throws Exception {
        assertEquals(401, get("/api/system/roles/2/menus", null).statusCode());
        assertEquals(401, get("/api/system/roles/2/depts", null).statusCode());

        // 普通用户 user 无 system:role:query 权限：403
        String token = loginToken(USER, USER_PASSWORD);
        HttpResponse<String> forbiddenMenus = get("/api/system/roles/2/menus", token);
        assertEquals(403, forbiddenMenus.statusCode());
        assertRShape(forbiddenMenus, 403);
        HttpResponse<String> forbiddenDepts = get("/api/system/roles/2/depts", token);
        assertEquals(403, forbiddenDepts.statusCode());
        assertRShape(forbiddenDepts, 403);
    }

    @Test
    void adminReadsBackAssignedMenusAndCustomDepts() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        try {
            putJson("/api/system/roles/2/menus", "{\"menuIds\":[100,101]}", token);
            putJson("/api/system/roles/2/data-scope",
                    "{\"scopeType\":\"CUSTOM\",\"deptIds\":[2,3]}", token);

            HttpResponse<String> menus = get("/api/system/roles/2/menus", token);
            assertEquals(200, menus.statusCode(), "菜单读回失败: " + menus.body());
            assertEquals(Set.of(100L, 101L), idSet(menus));

            HttpResponse<String> depts = get("/api/system/roles/2/depts", token);
            assertEquals(200, depts.statusCode(), "部门读回失败: " + depts.body());
            assertEquals(Set.of(2L, 3L), idSet(depts));

            // 清空授权后读回为空集合
            putJson("/api/system/roles/2/menus", "{\"menuIds\":[]}", token);
            putJson("/api/system/roles/2/data-scope", "{\"scopeType\":\"ALL\"}", token);
            assertTrue(idSet(get("/api/system/roles/2/menus", token)).isEmpty());
            assertTrue(idSet(get("/api/system/roles/2/depts", token)).isEmpty());
        } finally {
            putJson("/api/system/roles/2/menus", "{\"menuIds\":[1]}", token);
            putJson("/api/system/roles/2/data-scope", "{\"scopeType\":\"ALL\"}", token);
        }
    }

    @Test
    void relationReadBackReturns404ForUnknownRole() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);

        HttpResponse<String> menus = get("/api/system/roles/999999/menus", token);
        assertEquals(404, menus.statusCode());
        assertRShape(menus, 404);

        HttpResponse<String> depts = get("/api/system/roles/999999/depts", token);
        assertEquals(404, depts.statusCode());
        assertRShape(depts, 404);
    }

    /** 提取 R<data> 中的数字 ID 集合（JSON 数值可能解析为 Integer/Long）。 */
    private static Set<Long> idSet(HttpResponse<String> response) {
        Map<String, Object> body = json(response);
        @SuppressWarnings("unchecked")
        List<Object> data = (List<Object>) body.get("data");
        assertNotNull(data, "data 必须为 ID 列表: " + response.body());
        Set<Long> ids = new HashSet<>();
        for (Object id : data) {
            ids.add(((Number) id).longValue());
        }
        return ids;
    }

    private static String loginToken(String username, String password) throws Exception {
        HttpResponse<String> response = postJson("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", null);
        assertEquals(200, response.statusCode(), "登录失败: " + response.body());
        Map<?, ?> data = (Map<?, ?>) json(response).get("data");
        return (String) data.get("access_token");
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
        return (Map<String, Object>) Json.parse(response.body(), Map.class);
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }
}
