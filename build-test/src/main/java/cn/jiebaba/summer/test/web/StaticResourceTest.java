package cn.jiebaba.summer.test.web;

import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.WebResponse;
import cn.jiebaba.summer.web.resource.StaticResourceHandler;
import cn.jiebaba.summer.web.resource.StaticResourceProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link StaticResourceHandler} 单测：欢迎页映射、按扩展名 Content-Type、
 * 目录穿越防护、仅 GET、开关关闭语义。
 */
public class StaticResourceTest {

    private static WebResponse newResponse() {
        return new WebResponse(Channels.newChannel(new ByteArrayOutputStream()));
    }

    private static StaticResourceHandler handler(Path dir, boolean enabled) {
        return new StaticResourceHandler(
                new StaticResourceProperties(enabled, List.of("file:" + dir), "index.html"));
    }

    @Test
    @DisplayName("欢迎页与子目录资源正确响应")
    void servesWelcomeAndAssets() throws Exception {
        Path dir = Files.createTempDirectory("summer-static");
        Files.writeString(dir.resolve("index.html"), "<h1>hi</h1>");
        Files.writeString(dir.resolve("app.js"), "console.log(1)");
        Files.createDirectories(dir.resolve("assets"));
        Files.writeString(dir.resolve("assets/style.css"), "body{}");
        StaticResourceHandler handler = handler(dir, true);

        WebResponse index = newResponse();
        Assertions.assertTrue(handler.tryServe(HttpMethod.GET, "/", index));
        Assertions.assertEquals("<h1>hi</h1>", new String(index.body(), StandardCharsets.UTF_8));
        Assertions.assertTrue(index.header("Content-Type").startsWith("text/html"), index.header("Content-Type"));

        WebResponse js = newResponse();
        Assertions.assertTrue(handler.tryServe(HttpMethod.GET, "/app.js", js));
        Assertions.assertTrue(js.header("Content-Type").startsWith("text/javascript"), js.header("Content-Type"));

        WebResponse css = newResponse();
        Assertions.assertTrue(handler.tryServe(HttpMethod.GET, "/assets/style.css", css));
        Assertions.assertTrue(css.header("Content-Type").startsWith("text/css"), css.header("Content-Type"));
        Assertions.assertEquals("body{}", new String(css.body(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("未知资源不命中（调用方走 404）")
    void missReturnsFalse() throws Exception {
        Path dir = Files.createTempDirectory("summer-static");
        Files.writeString(dir.resolve("index.html"), "x");
        Assertions.assertFalse(handler(dir, true).tryServe(HttpMethod.GET, "/nope.txt", newResponse()));
    }

    @Test
    @DisplayName("目录穿越路径被拒绝")
    void traversalRejected() throws Exception {
        Path dir = Files.createTempDirectory("summer-static");
        Files.writeString(dir.resolve("index.html"), "x");
        Files.writeString(dir.resolveSibling("secret.txt"), "secret");
        StaticResourceHandler handler = handler(dir, true);
        Assertions.assertFalse(handler.tryServe(HttpMethod.GET, "/../secret.txt", newResponse()));
        Assertions.assertFalse(handler.tryServe(HttpMethod.GET, "/..\\secret.txt", newResponse()));
    }

    @Test
    @DisplayName("仅 GET 命中；关闭启用开关后全部不命中")
    void onlyGetAndRespectsEnabledFlag() throws Exception {
        Path dir = Files.createTempDirectory("summer-static");
        Files.writeString(dir.resolve("index.html"), "x");
        Assertions.assertFalse(handler(dir, true).tryServe(HttpMethod.POST, "/", newResponse()));
        Assertions.assertFalse(handler(dir, true).tryServe(HttpMethod.GET, null, newResponse()));
        Assertions.assertFalse(handler(dir, false).tryServe(HttpMethod.GET, "/", newResponse()));
    }

    @Test
    @DisplayName("配置解析：默认位置列表与欢迎页")
    void propertiesDefaults() {
        StaticResourceProperties props =
                StaticResourceProperties.from(new cn.jiebaba.summer.core.env.Environment());
        Assertions.assertTrue(props.enabled());
        Assertions.assertEquals("index.html", props.welcome());
        Assertions.assertTrue(props.locations().contains("classpath:/static"));
    }
}
