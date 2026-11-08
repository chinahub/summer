package cn.jiebaba.summer.admin.common.log;

import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChain;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

/**
 * 管理端请求上下文过滤器：把当前 {@link WebRequest} 绑定到虚拟线程
 * {@link AdminRequestContext}，finally 中无条件清理，保证操作日志切面
 * 能拿到请求 URL/方式/IP，且上下文绝不泄漏到同一线程池的后续请求。
 */
@Component
public class AdminRequestContextFilter implements Filter {

    @Override
    public void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception {
        AdminRequestContext.set(request);
        try {
            chain.doFilter(request, response);
        } finally {
            AdminRequestContext.clear();
        }
    }
}
