package cn.jiebaba.summer.web.filter;

import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

/**
 * 通用请求过滤器，在路由分派之前执行。过滤器可检查请求，或通过写出响应且
 * <em>不</em> 调用 {@link FilterChain#doFilter(WebRequest, WebResponse)} 来短路请求。
 * 这是安全（及其他横切关注点）实现的 Web 层抽象，使 {@code summer-web}
 * 不依赖任何安全模块。
 */
@FunctionalInterface
public interface Filter {
    void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception;
}
