package cn.jiebaba.summer.security.web.csrf;

import cn.jiebaba.summer.security.web.JsonEscape;
import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChain;
import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.HttpStatus;
import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * CSRF 过滤器：对安全方法（GET/HEAD/OPTIONS/TRACE）放行并确保令牌 Cookie 已下发，
 * 对非安全方法（POST/PUT/DELETE/PATCH）校验请求头或参数中的令牌与 Cookie 中的令牌是否一致，
 * 不一致时返回 403。对应 Spring Security 的 {@code CsrfFilter}。
 *
 * <p>采用双重提交 Cookie 模式：令牌写入 {@code httpOnly=false} 的 Cookie，
 * 客户端读取后通过请求头（默认 {@code X-XSRF-TOKEN}）回传。
 */
public final class CsrfFilter implements Filter {

    /** 视为安全、不需要 CSRF 校验的 HTTP 方法。 */
    private static final Set<HttpMethod> SAFE_METHODS =
            Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE);

    private final CsrfTokenRepository repository;

    public CsrfFilter(CsrfTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * 过滤器入口：加载或生成 CSRF 令牌并以请求属性暴露；安全方法直接放行，
     * 非安全方法比对回传令牌与存储令牌，校验失败时返回 403。
     *
     * @param request   当前 Web 请求
     * @param response  当前 Web 响应
     * @param chain     过滤器链
     * @throws Exception 后续过滤器或终端处理器抛出的异常
     */
    @Override
    public void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception {
        CsrfToken csrfToken = repository.loadToken(request);
        boolean missingToken = csrfToken == null;
        if (missingToken) {
            csrfToken = repository.generateToken(request);
            repository.saveToken(csrfToken, request, response);
        }
        request.setAttribute(CsrfToken.class.getName(), csrfToken);
        request.setAttribute(csrfToken.parameterName(), csrfToken);

        if (SAFE_METHODS.contains(request.method())) {
            chain.doFilter(request, response);
            return;
        }
        String actualToken = request.header(csrfToken.headerName());
        if (actualToken == null) {
            actualToken = request.query(csrfToken.parameterName());
        }
        if (!constantTimeEquals(csrfToken.token(), actualToken)) {
            reject(response, missingToken ? "CSRF token missing" : "CSRF token mismatch");
            return;
        }
        chain.doFilter(request, response);
    }

    /** 返回 403 Forbidden 并写出 JSON 错误体，与安全过滤器链的错误响应风格一致。 */
    private static void reject(WebResponse response, String message) {
        response.status(HttpStatus.FORBIDDEN.code());
        response.contentType(MediaType.APPLICATION_JSON_UTF8);
        response.body("{\"status\":403,\"error\":\"Forbidden\",\"message\":\""
                + JsonEscape.escape(message) + "\"}");
    }

    /** 常量时间比较两个令牌，避免时序侧信道泄露；任一为 {@code null} 时返回 {@code false}。 */
    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
