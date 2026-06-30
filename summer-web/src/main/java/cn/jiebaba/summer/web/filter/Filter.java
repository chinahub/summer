package cn.jiebaba.summer.web.filter;

import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

/**
 * Generic request filter, evaluated before route dispatch. A filter may inspect
 * or short-circuit the request by writing a response and <em>not</em> calling
 * {@link FilterChain#doFilter(WebRequest, WebResponse)}. This is the web-layer
 * abstraction that security (and other cross-cutting concerns) implement; it
 * keeps {@code summer-web} free of any dependency on a security module.
 */
@FunctionalInterface
public interface Filter {
    void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception;
}
