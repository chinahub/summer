package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.WebRequest;

import java.util.List;

/**
 * 安全过滤器链。每条链绑定一个请求匹配模式（{@code securityMatcher}）与一组过滤器；
 * {@link #matches(WebRequest)} 按 method+path 判定该链是否处理当前请求，从而支持多条链
 * 按 {@link #order()} 优先级依次匹配--首条匹配的链胜出。
 * <p>对应 {@code org.springframework.security.web.SecurityFilterChain}。
 */
public final class SecurityFilterChain {

    private final String pattern;
    private final HttpMethod method;
    private final int order;
    private final List<Filter> filters;

    /** 构建匹配所有请求的链（兼容单链场景），优先级默认为兜底（最低）。 */
    public SecurityFilterChain(List<Filter> filters) {
        this("/**", null, Integer.MAX_VALUE, filters);
    }

    /**
     * 构建绑定指定请求匹配模式与优先级的安全过滤器链。
     *
     * @param pattern Ant 风格路径模式，如 {@code /api/**}；为空按 {@code /**} 处理
     * @param method  限定 HTTP 方法；{@code null} 表示不限方法
     * @param order    链优先级，值小者优先匹配（类似 Spring 的 {@code @Order}）
     * @param filters  有序过滤器列表；为空表示安全实际被禁用
     */
    public SecurityFilterChain(String pattern, HttpMethod method, int order, List<Filter> filters) {
        this.pattern = (pattern == null || pattern.isBlank()) ? "/**" : pattern;
        this.method = method;
        this.order = order;
        this.filters = List.copyOf(filters);
    }

    /** 该链是否应用于当前请求：方法与路径均需匹配。 */
    public boolean matches(WebRequest request) {
        return matches(request.method(), request.path());
    }

    /** 该链是否应用于给定 method+path：方法限定与 Ant 路径匹配共同决定。 */
    public boolean matches(HttpMethod requestMethod, String requestPath) {
        if (method != null && method != requestMethod) {
            return false;
        }
        return AntPathMatcher.match(pattern, requestPath);
    }

    /** 链优先级，值小者优先；未指定时为 {@link Integer#MAX_VALUE}（兜底链）。 */
    public int order() {
        return order;
    }

    /** 有序过滤器列表；为空表示安全实际被禁用。 */
    public List<Filter> filters() {
        return filters;
    }

    public boolean isEnabled() {
        return !filters.isEmpty();
    }
}
