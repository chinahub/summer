package cn.jiebaba.summer.web.cors;

import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChain;
import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.HttpStatus;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.util.List;
import java.util.regex.Pattern;

/**
 * CORS（跨源资源共享）过滤器。在路由分派之前处理跨域请求：对预检（OPTIONS）请求
 * 直接返回 CORS 预检响应并短路后续链路，对实际请求补充跨域响应头后继续分派。
 * 未启用（{@link CorsProperties#enabled()} 为 false）时透传至后续过滤器。
 *
 * <p>该过滤器须先于安全过滤器执行，使 CORS 预检不触发认证（预检请求不携带凭证）。
 */
public final class CorsFilter implements Filter {

    private static final String ORIGIN = "Origin";
    private static final String VARY = "Vary";
    private static final String ACRM = "Access-Control-Request-Method";
    private static final String ACRH = "Access-Control-Request-Headers";

    private final CorsProperties config;

    public CorsFilter(CorsProperties config) {
        this.config = config;
    }

    /**
     * 处理跨域请求：未启用或无 Origin 头时直接放行；来源未获允许时返回 403；
     * 预检请求补充预检响应头并以 204 短路；实际请求补充跨域头后继续链路。
     *
     * @param request  当前 Web 请求
     * @param response 当前 Web 响应
     * @param chain    过滤器链
     * @throws Exception 后续过滤器或终端处理器抛出的异常
     */
    @Override
    public void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception {
        if (!config.enabled()) {
            chain.doFilter(request, response);
            return;
        }
        String origin = request.header(ORIGIN);
        if (origin == null || origin.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        String allowOrigin = resolveAllowOrigin(origin);
        if (allowOrigin == null) {
            reject(response);
            return;
        }
        response.header("Access-Control-Allow-Origin", allowOrigin);
        addVary(response, ORIGIN);
        if (config.allowCredentials()) {
            response.header("Access-Control-Allow-Credentials", "true");
        }
        if (isPreFlight(request)) {
            handlePreFlight(request, response);
            return;
        }
        if (!config.exposedHeaders().isEmpty()) {
            response.header("Access-Control-Expose-Headers", join(config.exposedHeaders()));
        }
        chain.doFilter(request, response);
    }

    /** 判断是否为 CORS 预检请求：OPTIONS 方法且携带 Access-Control-Request-Method 头。 */
    private static boolean isPreFlight(WebRequest request) {
        return request.method() == HttpMethod.OPTIONS && request.header(ACRM) != null;
    }

    /** 处理预检请求：补充允许的方法、请求头与缓存时长，置 204 并短路后续链路。 */
    private void handlePreFlight(WebRequest request, WebResponse response) {
        String methods = config.allowedMethods().isEmpty()
                ? request.header(ACRM)
                : join(config.allowedMethods());
        if (methods != null && !methods.isBlank()) {
            response.header("Access-Control-Allow-Methods", methods);
        }
        String headers = resolveAllowedHeaders(request);
        if (headers != null) {
            response.header("Access-Control-Allow-Headers", headers);
            addVary(response, ACRH);
        }
        response.header("Access-Control-Max-Age", String.valueOf(config.maxAge()));
        response.status(HttpStatus.NO_CONTENT.code());
    }

    /** 解析允许的请求头：配置值优先，否则回显预检请求中的 Access-Control-Request-Headers。 */
    private String resolveAllowedHeaders(WebRequest request) {
        if (!config.allowedHeaders().isEmpty()) {
            return join(config.allowedHeaders());
        }
        String requested = request.header(ACRH);
        return (requested == null || requested.isBlank()) ? null : requested;
    }

    /**
     * 解析允许的来源：未配置任何来源/模式时默认放行全部；通配且未启用凭证时返回 *；
     * 精确命中或通配且启用凭证时回显具体来源；否则尝试 allowed-origin-patterns 通配匹配。
     */
    private String resolveAllowOrigin(String origin) {
        List<String> origins = config.allowedOrigins();
        if (origins.isEmpty() && config.allowedOriginPatterns().isEmpty()) {
            return config.allowCredentials() ? origin : "*";
        }
        boolean wildcard = origins.contains("*");
        if (wildcard && !config.allowCredentials()) {
            return "*";
        }
        if (origins.contains(origin) || wildcard) {
            return origin;
        }
        for (String pattern : config.allowedOriginPatterns()) {
            if (matchesPattern(pattern, origin)) {
                return origin;
            }
        }
        return null;
    }

    /** 简单通配匹配：将 * 转为正则 .*，其余字符按字面转义，大小写不敏感。 */
    private static boolean matchesPattern(String pattern, String origin) {
        String[] parts = pattern.split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) regex.append(".*");
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE).matcher(origin).matches();
    }

    /** 拒绝跨域请求：返回 403 且不附带任何 CORS 头（浏览器将阻止读取响应）。 */
    private static void reject(WebResponse response) {
        response.status(HttpStatus.FORBIDDEN.code());
    }

    /** 以逗号拼接列表项。 */
    private static String join(List<String> items) {
        return String.join(", ", items);
    }

    /** 追加 Vary 头值：已存在则合并，避免覆盖既有值。 */
    private static void addVary(WebResponse response, String value) {
        String existing = response.header(VARY);
        if (existing == null || existing.isBlank()) {
            response.header(VARY, value);
        } else if (!containsToken(existing, value)) {
            response.header(VARY, existing + ", " + value);
        }
    }

    /** 检查逗号分隔的头值列表是否已包含某 token（大小写不敏感）。 */
    private static boolean containsToken(String headerValue, String token) {
        for (String part : headerValue.split(",")) {
            if (part.trim().equalsIgnoreCase(token)) return true;
        }
        return false;
    }
}
