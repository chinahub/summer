package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.WebRequest;

import java.util.List;

/**
 * 安全过滤器链。v1 支持匹配所有请求的单条链；
 * {@code matches()} 为未来多链支持预留（见 assumptions）。
 * <p>对应 {@code org.springframework.security.web.SecurityFilterChain}。
 */
public final class SecurityFilterChain {

    private final List<Filter> filters;

    public SecurityFilterChain(List<Filter> filters) {
        this.filters = List.copyOf(filters);
    }

    /** v1：单条链匹配所有请求。 */
    public boolean matches(WebRequest request) {
        return true;
    }

    /** 该链是否应用于给定 method+path（为多链预留）。 */
    public boolean matches(HttpMethod method, String path) {
        return true;
    }

    /** 有序过滤器列表；为空表示安全实际被禁用。 */
    public List<Filter> filters() {
        return filters;
    }

    public boolean isEnabled() {
        return !filters.isEmpty();
    }
}
