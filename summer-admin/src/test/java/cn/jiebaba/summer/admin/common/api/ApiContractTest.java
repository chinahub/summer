package cn.jiebaba.summer.admin.common.api;

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
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API 统一契约测试：以随机端口拉起完整应用，经真实 HTTP 断言所有错误响应
 * （业务 409、显式校验 400、框架校验 400、未知接口 404、未知异常 500）均为
 * {@code {code,msg}} 统一格式；500 响应不得泄露堆栈、SQL 或敏感请求体；
 * 非 /api 路径保持框架内置错误格式不被改写；/api 健康接口走 R 包装。
 * <p>401/403 真实请求断言按 brief 约定推迟到 Task 7 安全链启用后补充。
 */
class ApiContractTest {

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
    void healthEndpointUsesRShape() throws Exception {
        HttpResponse<String> response = get("/api/health");
        assertEquals(200, response.statusCode());
        Map<String, Object> body = json(response);
        assertEquals(200, code(body));
        // data.status=UP：健康语义保持不变，仅外层加 R 包装
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertNotNull(data, "健康接口应包 R 且 data 含 status=UP");
        assertEquals("UP", data.get("status"));
    }

    @Test
    void businessExceptionMapsTo409R() throws Exception {
        HttpResponse<String> response = get("/api/probe/business");
        assertEquals(409, response.statusCode());
        Map<String, Object> body = json(response);
        assertEquals(409, code(body));
        assertEquals("演示业务冲突", body.get("msg"));
        assertFalse(body.containsKey("timestamp"), "R 格式不得含框架 timestamp 字段: " + response.body());
    }

    @Test
    void explicitValidationMapsTo400R() throws Exception {
        HttpResponse<String> response = postJson("/api/probe/validated", "{\"name\":\"  \"}");
        assertEquals(400, response.statusCode());
        Map<String, Object> body = json(response);
        assertEquals(400, code(body));
        String msg = (String) body.get("msg");
        assertNotNull(msg);
        assertTrue(msg.contains("名称不能为空"), "msg 应含字段校验消息，实际: " + msg);
        assertFalse(body.containsKey("timestamp"), "R 格式不得含框架 timestamp 字段: " + response.body());
        assertFalse(body.containsKey("violations"), "R 格式不得含框架 violations 字段: " + response.body());
    }

    @Test
    void frameworkValidationErrorIsNormalizedTo400R() throws Exception {
        // 模拟框架注解校验失败（@Valid 触发框架内置 violations 格式），过滤器须规范为 R
        HttpResponse<String> response = postJson("/api/probe/framework-validation", "{}");
        assertEquals(400, response.statusCode());
        Map<String, Object> body = json(response);
        assertEquals(400, code(body));
        String msg = (String) body.get("msg");
        assertNotNull(msg);
        assertTrue(msg.contains("名称不能为空"), "msg 应保留字段校验消息，实际: " + msg);
        assertNotNull(body.get("msg"));
        assertFalse(body.containsKey("timestamp"), "R 格式不得含框架 timestamp 字段: " + response.body());
        assertFalse(body.containsKey("violations"), "框架内置 violations 体应被规范为 R: " + response.body());
    }

    @Test
    void unknownApiMapsTo404R() throws Exception {
        HttpResponse<String> response = get("/api/definitely-not-exists-4f3a");
        assertEquals(404, response.statusCode());
        Map<String, Object> body = json(response);
        assertEquals(404, code(body));
        assertNotNull(body.get("msg"));
        assertFalse(body.containsKey("timestamp"), "R 格式不得含框架 timestamp 字段: " + response.body());
        assertFalse(body.containsKey("path"), "R 格式不得含框架 path 字段: " + response.body());
    }

    @Test
    void unknownExceptionReturnsSafeGeneric500R() throws Exception {
        // 端点抛出带 SQL/敏感词细节的异常：响应只能是安全通用消息
        HttpResponse<String> response = get("/api/probe/boom");
        assertEquals(500, response.statusCode());
        Map<String, Object> body = json(response);
        assertEquals(500, code(body));
        String raw = response.body();
        assertFalse(raw.contains("SELECT password"), "500 响应不得泄露 SQL: " + raw);
        assertFalse(raw.contains("stackTrace"), "500 响应不得含堆栈: " + raw);
        assertFalse(raw.contains("RuntimeException"), "500 响应不得含异常类型: " + raw);
        assertFalse(raw.contains("probe-boom-secret"), "500 响应不得泄露异常细节: " + raw);
    }

    @Test
    void nonApiRouteMissKeepsFrameworkFormat() throws Exception {
        // 非 /api 路径的路由未命中：过滤器不得改写框架内置 404 格式
        HttpResponse<String> response = get("/probe-open/no-such-page");
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("timestamp"),
                "非 /api 路由未命中应保持框架格式（含 timestamp）: " + response.body());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postJson(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(HttpResponse<String> response) {
        return (Map<String, Object>) Json.parse(response.body());
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }
}
