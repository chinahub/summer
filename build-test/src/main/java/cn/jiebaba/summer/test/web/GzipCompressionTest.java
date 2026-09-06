package cn.jiebaba.summer.test.web;

import cn.jiebaba.summer.web.server.WebServerProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

import java.nio.charset.StandardCharsets;

/** gzip 响应压缩的判定与执行测试（{@link WebServerProperties.Compression}）。 */
public class GzipCompressionTest {

    private static final WebServerProperties.Compression CFG =
            new WebServerProperties.Compression(true, List.of("application/json", "text/*"), 10);

    @Test
    public void mimeTypeMatching() {
        Assertions.assertTrue(CFG.matches("application/json"));
        Assertions.assertTrue(CFG.matches("application/json; charset=utf-8"));
        Assertions.assertTrue(CFG.matches("text/html"));
        Assertions.assertTrue(CFG.matches("TEXT/PLAIN"));
        Assertions.assertFalse(CFG.matches("image/png"));
        Assertions.assertFalse(CFG.matches(null));
    }

    @Test
    public void applyCompressesMatchingBody() throws Exception {
        byte[] body = "0123456789".repeat(10).getBytes(StandardCharsets.UTF_8);
        byte[] gz = CFG.apply("application/json", body, true);
        Assertions.assertNotNull(gz);
        Assertions.assertTrue(gz.length < body.length);
        // gzip 可解压还原为原文
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            byte[] restored = in.readAllBytes();
            Assertions.assertArrayEquals(body, restored);
        }
    }

    @Test
    public void applySkipsWhenAnyConditionUnmet() {
        byte[] body = "0123456789".repeat(10).getBytes(StandardCharsets.UTF_8);
        Assertions.assertNull(CFG.apply("image/png", body, true), "mime 不匹配不压缩");
        Assertions.assertNull(CFG.apply("application/json", body, false), "客户端不接受 gzip 不压缩");
        Assertions.assertNull(CFG.apply(null, body, true), "无 Content-Type 不压缩");
        Assertions.assertNull(CFG.apply("application/json", new byte[0], true), "空响应体不压缩");

        WebServerProperties.Compression disabled =
                new WebServerProperties.Compression(false, List.of("application/json"), 0);
        Assertions.assertNull(disabled.apply("application/json", body, true), "未启用压缩");

        WebServerProperties.Compression largeThreshold =
                new WebServerProperties.Compression(true, List.of("application/json"), 1000);
        Assertions.assertNull(largeThreshold.apply("application/json", body, true), "未达到最小长度不压缩");
    }
}
