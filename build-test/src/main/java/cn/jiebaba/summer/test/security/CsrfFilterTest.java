package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.security.web.csrf.CookieCsrfTokenRepository;
import cn.jiebaba.summer.security.web.csrf.CsrfFilter;
import cn.jiebaba.summer.security.web.csrf.CsrfProperties;
import cn.jiebaba.summer.security.web.csrf.CsrfToken;
import cn.jiebaba.summer.web.filter.FilterChain;
import cn.jiebaba.summer.web.http.RawHttpRequest;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 验证 CsrfFilter 与 CookieCsrfTokenRepository 行为：安全方法下发/复用令牌 Cookie、
 * 非安全方法缺失/不匹配令牌返回 403、令牌一致时放行、查询参数回退、Cookie 属性按配置写入、
 * 清除令牌，以及 CsrfProperties 配置绑定。
 */
public class CsrfFilterTest {

    private final CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withDefaults();
    private final CsrfFilter filter = new CsrfFilter(repository);

    /** 按方法、路径与成对传入的请求头构造 WebRequest。 */
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

    /** 构造写往内存流的 WebResponse，便于读取响应头。 */
    private WebResponse response() {
        return new WebResponse(Channels.newChannel(new ByteArrayOutputStream()));
    }

    /** 构造过滤器链：终端处理器记录是否被调用。 */
    private static FilterChain chain(AtomicBoolean called) {
        return new FilterChain(List.of(), (req, res) -> called.set(true));
    }

    /** 从 Set-Cookie 头中解析指定名称的 Cookie 值。 */
    private static String cookieValue(WebResponse res, String name) {
        String setCookie = res.header("Set-Cookie");
        Assert.assertNotNull(setCookie, "应下发 Set-Cookie 头");
        int start = setCookie.indexOf(name + "=") + name.length() + 1;
        int end = setCookie.indexOf(';', start);
        return end < 0 ? setCookie.substring(start) : setCookie.substring(start, end);
    }

    /** 通过一次安全（GET）请求取得生成的令牌值，用于构造后续可放行的非安全请求。 */
    private String issueToken(String path) throws Exception {
        WebResponse res = response();
        filter.doFilter(request("GET", path), res, chain(new AtomicBoolean()));
        return cookieValue(res, "XSRF-TOKEN");
    }

    @Test
    void safeMethodIssuesCookie() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        WebRequest req = request("GET", "/a");
        WebResponse res = response();
        filter.doFilter(req, res, chain(called));
        Assert.assertTrue(called.get(), "安全方法应放行至后续链路");
        Assert.assertNotNull(res.header("Set-Cookie"), "无 Cookie 时应下发新令牌");
        CsrfToken token = (CsrfToken) req.getAttribute("_csrf");
        Assert.assertNotNull(token, "令牌应作为请求属性暴露");
        Assert.assertEquals(token.token(), cookieValue(res, "XSRF-TOKEN"), "Cookie 值应与令牌一致");
    }

    @Test
    void safeMethodWithCookieDoesNotResave() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        WebRequest req = request("GET", "/a", "Cookie", "XSRF-TOKEN=existing-token");
        WebResponse res = response();
        filter.doFilter(req, res, chain(called));
        Assert.assertTrue(called.get(), "安全方法应放行");
        Assert.assertNull(res.header("Set-Cookie"), "已有 Cookie 时不应重复下发");
        CsrfToken token = (CsrfToken) req.getAttribute("_csrf");
        Assert.assertEquals("existing-token", token.token(), "应复用已有令牌");
    }

    @Test
    void unsafeWithoutTokenRejected() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        WebResponse res = response();
        filter.doFilter(request("POST", "/a"), res, chain(called));
        Assert.assertFalse(called.get(), "无令牌的非安全方法应被拦截");
        Assert.assertEquals(403, res.status());
    }

    @Test
    void unsafeWithMismatchedTokenRejected() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        WebResponse res = response();
        filter.doFilter(request("POST", "/a",
                "Cookie", "XSRF-TOKEN=real-token",
                "X-XSRF-TOKEN", "wrong-token"), res, chain(called));
        Assert.assertFalse(called.get(), "令牌不匹配应被拦截");
        Assert.assertEquals(403, res.status());
    }

    @Test
    void unsafeWithMatchingTokenAllowed() throws Exception {
        String token = issueToken("/data");
        AtomicBoolean called = new AtomicBoolean();
        WebResponse res = response();
        filter.doFilter(request("POST", "/data",
                "Cookie", "XSRF-TOKEN=" + token,
                "X-XSRF-TOKEN", token), res, chain(called));
        Assert.assertTrue(called.get(), "令牌一致应放行");
        Assert.assertTrue(res.status() != 403, "不应返回 403");
    }

    @Test
    void unsafeWithQueryTokenAllowed() throws Exception {
        String token = issueToken("/q");
        AtomicBoolean called = new AtomicBoolean();
        WebResponse res = response();
        // 令牌也可经查询参数 _csrf 回传（请求头缺失时的回退）
        filter.doFilter(request("POST", "/q?_csrf=" + token,
                "Cookie", "XSRF-TOKEN=" + token), res, chain(called));
        Assert.assertTrue(called.get(), "查询参数回传令牌一致应放行");
    }

    @Test
    void repositoryWritesConfiguredCookieAttributes() throws Exception {
        CookieCsrfTokenRepository repo = new CookieCsrfTokenRepository(
                "XSRF-TOKEN", "X-XSRF-TOKEN", "_csrf", true, true, "Strict", 600L);
        WebResponse res = response();
        repo.saveToken(new CsrfToken("X-XSRF-TOKEN", "_csrf", "abc"), request("GET", "/"), res);
        String setCookie = res.header("Set-Cookie");
        Assert.assertTrue(setCookie.contains("Max-Age=600"), "应写入 Max-Age");
        Assert.assertTrue(setCookie.contains("HttpOnly"), "应写入 HttpOnly");
        Assert.assertTrue(setCookie.contains("Secure"), "应写入 Secure");
        Assert.assertTrue(setCookie.contains("SameSite=Strict"), "应写入 SameSite");
    }

    @Test
    void repositoryClearsCookie() throws Exception {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withDefaults();
        WebResponse res = response();
        repo.saveToken(null, request("GET", "/"), res);
        String setCookie = res.header("Set-Cookie");
        Assert.assertTrue(setCookie.contains("Max-Age=0"), "清除令牌应设置 Max-Age=0");
    }

    @Test
    void propertiesBoundFromEnvironment() {
        System.setProperty("summer.security.csrf.enabled", "true");
        System.setProperty("summer.security.csrf.cookie.name", "CSRF-TOKEN");
        System.setProperty("summer.security.csrf.cookie.http-only", "true");
        System.setProperty("summer.security.csrf.cookie.same-site", "Strict");
        System.setProperty("summer.security.csrf.header-name", "X-CSRF-TOKEN");
        try {
            CsrfProperties p = CsrfProperties.from(new Environment());
            Assert.assertTrue(p.enabled());
            Assert.assertEquals("CSRF-TOKEN", p.cookieName());
            Assert.assertTrue(p.cookieHttpOnly());
            Assert.assertEquals("Strict", p.cookieSameSite());
            Assert.assertEquals("X-CSRF-TOKEN", p.headerName());
        } finally {
            System.clearProperty("summer.security.csrf.enabled");
            System.clearProperty("summer.security.csrf.cookie.name");
            System.clearProperty("summer.security.csrf.cookie.http-only");
            System.clearProperty("summer.security.csrf.cookie.same-site");
            System.clearProperty("summer.security.csrf.header-name");
        }
    }
}
