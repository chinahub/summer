package cn.jiebaba.summer.admin;

import cn.jiebaba.summer.boot.SummerApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 应用启动冒烟测试：以随机端口拉起完整应用，断言 {@code GET /api/health}
 * 返回 200 且 JSON 体含 {@code "status":"UP"}。
 * <p>本测试仅依赖 summer-admin 自身的 POM 与源码，不读取仓库内任何其他模块，
 * 因此整个目录可复制到临时位置独立运行。
 */
class ApplicationSmokeTest {

    private static SummerApplication app;
    private static HttpClient http;

    /** 启动应用：随机端口（{@code --server.port=0}）避免与本地 8080 冲突，命令行参数优先级最高。 */
    @BeforeAll
    static void startApplication() {
        app = SummerApplication.run(SummerAdminApplication.class, new String[]{"--server.port=0"});
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** 关闭应用：停止 Web 服务并关闭 IoC 容器，释放随机端口。 */
    @AfterAll
    static void stopApplication() {
        if (app != null) {
            app.webServer().stop();
            app.context().close();
            app = null;
        }
    }

    @Test
    void healthReturnsUp() throws Exception {
        int port = app.webServer().port();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        // 序列化格式可能含空白，去除后断言 JSON 内容
        String compact = response.body().replaceAll("\\s+", "");
        assertTrue(compact.contains("\"status\":\"UP\""),
                "响应体应含 \"status\":\"UP\"，实际为: " + response.body());
    }
}
