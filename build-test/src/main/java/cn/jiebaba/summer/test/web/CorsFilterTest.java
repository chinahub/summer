package cn.jiebaba.summer.test.web;

import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.web.cors.CorsFilter;
import cn.jiebaba.summer.web.cors.CorsProperties;
import cn.jiebaba.summer.web.filter.FilterChain;
import cn.jiebaba.summer.web.http.RawHttpRequest;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 验证 CorsFilter 行为：未启用/无 Origin 透传、预检短路、实际请求补头、来源拒绝、
 * 通配与模式匹配、凭证回显、默认放行，以及 CorsProperties 配置绑定。
 */
public class CorsFilterTest {

    private WebRequest request(String method, String path, String... headers) throws Exception {
        StringBuilder raw = new StringBuilder(method).append(' ').append(path)
                .append(" HTTP/1.1\r\nHost: x\r\n");
        for (int i = 0; i + 1 < headers.length; i += 2) {
            raw.append(headers[i]).append(": ").append(headers[i + 1]).append("\r\n");
        }
        raw.append("\r\n");
        return new WebRequest(RawHttpRequest.parse(
                new ByteArrayInputStream(raw.toString().getBytes(StandardCharsets.UTF_8)), 8192, 8388608));
    }

    private WebResponse response() {
        return new WebResponse(Channels.newChannel(new ByteArrayOutputStream()));
    }

    /** 构造过滤器链：终端处理器记录是否被调用。 */
    private static FilterChain chain(AtomicBoolean called) {
        return new FilterChain(List.of(), (req, res) -> called.set(true));
    }

    private static CorsProperties props(boolean enabled, List<String> origins, List<String> patterns,
                                        List<String> methods, List<String> headers, boolean credentials) {
        return new CorsProperties(enabled, origins, patterns, methods, headers, List.of(), credentials, 1800);
    }

    @Test
    void disabledPassthrough() throws Exception {
        CorsFilter filter = new CorsFilter(props(false, List.of(), List.of(), List.of(), List.of(), false));
        AtomicBoolean called = new AtomicBoolean();
        WebResponse res = response();
        filter.doFilter(request("GET", "/a", "Origin", "https://x.com"), res, chain(called));
        Assertions.assertTrue(called.get(), "未启用时应透传至后续链路");
        Assertions.assertNull(res.header("Access-Control-Allow-Origin"));
    }

    @Test
    void noOriginPassthrough() throws Exception {
        CorsFilter filter = new CorsFilter(props(true, List.of("https://x.com"), List.of(), List.of(), List.of(), false));
        AtomicBoolean called = new AtomicBoolean();
        WebResponse res = response();
        filter.doFilter(request("GET", "/a"), res, chain(called));
        Assertions.assertTrue(called.get(), "无 Origin 头时按非跨域请求放行");
        Assertions.assertNull(res.header("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightShortCircuits() throws Exception {
        CorsFilter filter = new CorsFilter(props(true, List.of("https://x.com"), List.of(),
                List.of("GET", "POST"), List.of("Content-Type"), false));
        AtomicBoolean called = new AtomicBoolean();
        WebResponse res = response();
        filter.doFilter(request("OPTIONS", "/a", "Origin", "https://x.com",
                "Access-Control-Request-Method", "POST",
                "Access-Control-Request-Headers", "X-K"), res, chain(called));
        Assertions.assertFalse(called.get(), "预检请求应短路，不进入路由");
        Assertions.assertEquals(204, res.status());
        Assertions.assertEquals("https://x.com", res.header("Access-Control-Allow-Origin"));
        Assertions.assertEquals("GET, POST", res.header("Access-Control-Allow-Methods"));
        Assertions.assertEquals("Content-Type", res.header("Access-Control-Allow-Headers"));
        Assertions.assertEquals("1800", res.header("Access-Control-Max-Age"));
        Assertions.assertTrue(res.header("Vary").contains("Origin"));
    }

    @Test
    void preflightReflectsRequestedHeaders() throws Exception {
        CorsFilter filter = new CorsFilter(props(true, List.of("https://x.com"), List.of(),
                List.of(), List.of(), false));
        WebResponse res = response();
        filter.doFilter(request("OPTIONS", "/a", "Origin", "https://x.com",
                "Access-Control-Request-Method", "POST",
                "Access-Control-Request-Headers", "Authorization, X-K"), res, chain(new AtomicBoolean()));
        Assertions.assertEquals("Authorization, X-K", res.header("Access-Control-Allow-Headers"));
    }

    @Test
    void actualRequestAddsHeaders() throws Exception {
        CorsFilter filter = new CorsFilter(props(true, List.of("https://x.com"), List.of(),
                List.of(), List.of(), false));
        AtomicBoolean called = new AtomicBoolean();
        WebResponse res = response();
        filter.doFilter(request("GET", "/a", "Origin", "https://x.com"), res, chain(called));
        Assertions.assertTrue(called.get(), "实际请求应继续链路");
        Assertions.assertEquals("https://x.com", res.header("Access-Control-Allow-Origin"));
        Assertions.assertFalse(res.header("Vary") == null, "应设置 Vary 头");
    }

    @Test
    void disallowedOriginRejected() throws Exception {
        CorsFilter filter = new CorsFilter(props(true, List.of("https://x.com"), List.of(),
                List.of(), List.of(), false));
        AtomicBoolean called = new AtomicBoolean();
        WebResponse res = response();
        filter.doFilter(request("GET", "/a", "Origin", "https://evil.com"), res, chain(called));
        Assertions.assertFalse(called.get(), "未授权来源应被拒绝");
        Assertions.assertEquals(403, res.status());
        Assertions.assertNull(res.header("Access-Control-Allow-Origin"));
    }

    @Test
    void wildcardWithoutCredentials() throws Exception {
        CorsFilter filter = new CorsFilter(props(true, List.of("*"), List.of(), List.of(), List.of(), false));
        WebResponse res = response();
        filter.doFilter(request("GET", "/a", "Origin", "https://any.com"), res, chain(new AtomicBoolean()));
        Assertions.assertEquals("*", res.header("Access-Control-Allow-Origin"));
    }

    @Test
    void wildcardEchoesOriginWithCredentials() throws Exception {
        CorsFilter filter = new CorsFilter(props(true, List.of("*"), List.of(), List.of(), List.of(), true));
        WebResponse res = response();
        filter.doFilter(request("GET", "/a", "Origin", "https://any.com"), res, chain(new AtomicBoolean()));
        Assertions.assertEquals("https://any.com", res.header("Access-Control-Allow-Origin"));
        Assertions.assertEquals("true", res.header("Access-Control-Allow-Credentials"));
    }

    @Test
    void originPatternMatches() throws Exception {
        CorsFilter filter = new CorsFilter(props(true, List.of(), List.of("https://*.example.com"),
                List.of(), List.of(), false));
        WebResponse res = response();
        filter.doFilter(request("GET", "/a", "Origin", "https://sub.example.com"), res, chain(new AtomicBoolean()));
        Assertions.assertEquals("https://sub.example.com", res.header("Access-Control-Allow-Origin"));
    }

    @Test
    void defaultAllowAllWhenNoOrigins() throws Exception {
        CorsFilter filter = new CorsFilter(props(true, List.of(), List.of(), List.of(), List.of(), false));
        WebResponse res = response();
        filter.doFilter(request("GET", "/a", "Origin", "https://any.com"), res, chain(new AtomicBoolean()));
        Assertions.assertEquals("*", res.header("Access-Control-Allow-Origin"));
    }

    @Test
    void propertiesBoundFromEnvironment() {
        System.setProperty("summer.web.cors.enabled", "true");
        System.setProperty("summer.web.cors.allowed-origins", "https://a.com,https://b.com");
        System.setProperty("summer.web.cors.allowed-methods", "GET,POST");
        System.setProperty("summer.web.cors.allow-credentials", "true");
        System.setProperty("summer.web.cors.max-age", "600");
        try {
            CorsProperties p = CorsProperties.from(new Environment());
            Assertions.assertTrue(p.enabled());
            Assertions.assertEquals(List.of("https://a.com", "https://b.com"), p.allowedOrigins());
            Assertions.assertEquals(List.of("GET", "POST"), p.allowedMethods());
            Assertions.assertTrue(p.allowCredentials());
            Assertions.assertEquals(600L, p.maxAge());
        } finally {
            System.clearProperty("summer.web.cors.enabled");
            System.clearProperty("summer.web.cors.allowed-origins");
            System.clearProperty("summer.web.cors.allowed-methods");
            System.clearProperty("summer.web.cors.allow-credentials");
            System.clearProperty("summer.web.cors.max-age");
        }
    }
}
