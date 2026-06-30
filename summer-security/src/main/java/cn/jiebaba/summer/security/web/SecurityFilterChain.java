package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.WebRequest;

import java.util.List;

/**
 * A security filter chain. v1 supports a single chain that matches all requests;
 * {@code matches()} is reserved for future multi-chain support (see assumptions).
 * <p>Mirrors {@code org.springframework.security.web.SecurityFilterChain}.
 */
public final class SecurityFilterChain {

    private final List<Filter> filters;

    public SecurityFilterChain(List<Filter> filters) {
        this.filters = List.copyOf(filters);
    }

    /** v1: a single chain matches every request. */
    public boolean matches(WebRequest request) {
        return true;
    }

    /** Whether this chain should apply to the given method+path (reserved for multi-chain). */
    public boolean matches(HttpMethod method, String path) {
        return true;
    }

    /** The ordered filters; an empty list means security is effectively disabled. */
    public List<Filter> filters() {
        return filters;
    }

    public boolean isEnabled() {
        return !filters.isEmpty();
    }
}
