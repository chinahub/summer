package cn.jiebaba.summer.web.filter;

import cn.jiebaba.summer.web.http.WebRequest;

import java.util.List;

/**
 * 按请求选择适用的安全过滤器列表。这是多条 {@code SecurityFilterChain} 按请求分发的核心抽象：
 * 调度器对每个请求调用 {@link #selectFilters(WebRequest)}，得到该请求应执行的过滤器序列。
 * <p>web 层不依赖 security 层（包级解耦），故多链匹配逻辑由启动层（{@code SummerApplication}）装配本接口的实现完成。
 */
@FunctionalInterface
public interface FilterChainSelector {

    /**
     * 返回匹配当前请求的过滤器列表；空列表表示该请求不应用安全过滤器（直接进入路由分派）。
     *
     * @param request 当前请求
     * @return 该请求应执行的过滤器序列（不可为 {@code null}）
     */
    List<Filter> selectFilters(WebRequest request);
}
