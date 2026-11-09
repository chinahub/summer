package cn.jiebaba.summer.admin;

import cn.jiebaba.summer.admin.testsupport.MySqlTestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 生产单 JAR 集成测试：对真实打包产物 {@code summer-admin-1.0.0-boot.jar} 做端到端验收。
 *
 * <p>验证点：
 * <ul>
 *   <li>JAR 结构：含 {@code BOOT-INF/classes/static/index.html} 与带内容 hash 的静态资源；</li>
 *   <li>启动真实 {@code java -jar} 进程（独立端口 18089，仅连接隔离测试库
 *       {@code summer_admin_test}），轮询 {@code /api/health} 等待就绪；</li>
 *   <li>{@code /} 返回前端页面、hash 资源可访问、{@code /api/health} 返回 UP；</li>
 *   <li>未知 {@code /api/not-found} 返回 404 且不回退为 index.html。</li>
 * </ul>
 * 进程在 {@code @AfterAll} 中无条件关闭（先 destroy，超时后 forcibly），绝不遗留。
 * 由 failsafe 在 package 之后运行；凭据只从进程环境变量读取。
 */
class ProductionJarIT {

    /** 独立端口，避开开发端口 8080 与其他任务端口。 */
    private static final int PORT = 18089;
    private static final String BASE = "http://127.0.0.1:" + PORT;

    /** 测试进程标准输出落盘文件，位于 target 下，不进入版本库。 */
    private static final Path LOG_FILE = Path.of("target", "it-production-jar.log");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private static Process process;
    private static String hashedAsset;

    /**
     * 启动前准备：解析 JAR 路径并校验静态资源条目、重建隔离测试库、
     * 以测试环境变量拉起真实进程并等待健康检查就绪。
     */
    @BeforeAll
    static void startJar() throws Exception {
        Path jar = locateJar();
        hashedAsset = assertJarContents(jar);
        MySqlTestDatabase.reset();
        ProcessBuilder builder = new ProcessBuilder(javaBinary(), "-jar", jar.toAbsolutePath().toString());
        builder.environment().put("SERVER_PORT", String.valueOf(PORT));
        builder.environment().put("SUMMER_DATASOURCE_URL", MySqlTestDatabase.testJdbcUrl());
        builder.environment().put("SUMMER_SECURITY_JWT_SECRET",
                "it-production-jar-test-only-jwt-secret-0123456789");
        requireEnv(builder, "SUMMER_DATASOURCE_USERNAME");
        requireEnv(builder, "SUMMER_DATASOURCE_PASSWORD");
        builder.redirectErrorStream(true);
        builder.redirectOutput(LOG_FILE.toFile());
        process = builder.start();
        try {
            awaitHealth();
        } catch (Exception e) {
            stopQuietly();
            throw e;
        }
    }

    /** 无条件关闭测试进程：先 destroy，10 秒未退出则强制终止，并断言进程已结束。 */
    @AfterAll
    static void stopJar() throws Exception {
        stopQuietly();
    }

    @Test
    void indexHtmlServedAtRoot() throws Exception {
        HttpResponse<String> response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("id=\"root\""),
                "/ 必须返回前端 index.html: " + abbreviate(response.body()));
        String contentType = response.headers().firstValue("content-type").orElse("");
        assertTrue(contentType.contains("html"), "content-type 应为 html: " + contentType);
    }

    @Test
    void hashedAssetServed() throws Exception {
        assertNotNull(hashedAsset, "JAR 中应存在 hash 静态资源");
        HttpResponse<String> response = get("/" + hashedAsset);
        assertEquals(200, response.statusCode(), "hash 资源应可访问: " + hashedAsset);
    }

    @Test
    void healthReturnsUp() throws Exception {
        HttpResponse<String> response = get("/api/health");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"UP\""), "health 应返回 UP: " + response.body());
    }

    @Test
    void unknownApiDoesNotFallBackToIndex() throws Exception {
        HttpResponse<String> response = get("/api/not-found");
        // 匿名请求先经 JWT 过滤器返回 401（认证后无可匹配路由时为 404）；
        // 断言核心：绝不回退为 index.html
        assertTrue(response.statusCode() == 401 || response.statusCode() == 404,
                "未知 API 应返回 401/404: " + response.statusCode());
        assertFalse(response.body().contains("id=\"root\""),
                "未知 API 不得回退为前端页面: " + abbreviate(response.body()));
    }

    /**
     * 解析可执行 JAR 路径：优先取 failsafe 注入的 {@code it.jar}，
     * 否则回退到基于 user.dir 的 target 目录约定路径。
     */
    private static Path locateJar() {
        String property = System.getProperty("it.jar");
        Path jar = property != null && !property.isBlank()
                ? Path.of(property)
                : Path.of(System.getProperty("user.dir"), "target", "summer-admin-1.0.0-boot.jar");
        assertTrue(Files.isRegularFile(jar), "可执行 JAR 不存在（须在 package 之后运行）: " + jar);
        return jar;
    }

    /**
     * 校验 JAR 内含生产前端产物：{@code BOOT-INF/classes/static/index.html} 必须存在，
     * 且至少存在一个带内容 hash 的 js 资源。返回其中一个 hash 资源名供 HTTP 探测。
     */
    private static String assertJarContents(Path jar) throws IOException {
        String asset = null;
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            JarEntry index = jarFile.getJarEntry("BOOT-INF/classes/static/index.html");
            assertNotNull(index, "JAR 缺少 BOOT-INF/classes/static/index.html");
            String indexHtml = new String(jarFile.getInputStream(index).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(indexHtml.contains("id=\"root\""), "index.html 内容异常");
            List<String> hashed = new ArrayList<>();
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("BOOT-INF/classes/static/") && name.endsWith(".js")) {
                    String file = name.substring("BOOT-INF/classes/static/".length());
                    if (file.matches("[^/]+\\.[0-9a-f]{8,}\\.js")) {
                        hashed.add(file);
                    }
                }
            }
            assertFalse(hashed.isEmpty(), "JAR 中缺少 hash 静态资源（前端未打入产物）");
            asset = hashed.get(0);
        }
        return asset;
    }

    /** 轮询健康检查直至就绪：最多 60 秒；进程提前退出时输出日志尾部辅助定位。 */
    private static void awaitHealth() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        IOException last = null;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                fail("测试进程提前退出（exit=" + process.exitValue() + "），日志尾部："
                        + tailLog());
            }
            try {
                HttpResponse<String> response = get("/api/health");
                if (response.statusCode() == 200 && response.body().contains("\"UP\"")) {
                    return;
                }
            } catch (IOException e) {
                last = e;
            }
            Thread.sleep(500);
        }
        fail("健康检查超时 60 秒未就绪: " + (last == null ? "" : last.getMessage())
                + "，日志尾部：" + tailLog());
    }

    /** 关闭进程：destroy 后等待 10 秒，仍未退出则 destroyForcibly，最终断言已退出。 */
    private static void stopQuietly() throws Exception {
        if (process == null) {
            return;
        }
        Process p = process;
        process = null;
        p.destroy();
        if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            assertTrue(p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS), "进程强制终止后仍未退出");
        }
        assertFalse(p.isAlive(), "测试进程必须已结束");
    }

    /** 进程环境变量必须存在才允许继续：把父进程（Maven JVM）的测试凭据透传给 JAR 进程。 */
    private static void requireEnv(ProcessBuilder builder, String name) {
        String value = System.getenv(name);
        assertNotNull(value, "环境变量缺失: " + name);
        builder.environment().put(name, value);
    }

    /** 当前 JVM 的 java 可执行路径，保证子进程使用与构建一致的 JDK。 */
    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString();
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 截断长文本，避免断言消息刷屏。 */
    private static String abbreviate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    /** 读取进程日志尾部（最多 20 行）用于失败诊断。 */
    private static String tailLog() throws IOException {
        if (!Files.isRegularFile(LOG_FILE)) {
            return "<无日志文件>";
        }
        List<String> lines = Files.readAllLines(LOG_FILE, StandardCharsets.UTF_8);
        return String.join("\n", lines.subList(Math.max(0, lines.size() - 20), lines.size()));
    }
}
