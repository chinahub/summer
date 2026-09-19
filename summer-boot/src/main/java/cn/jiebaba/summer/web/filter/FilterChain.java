package cn.jiebaba.summer.web.filter;

import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.util.List;

/**
 * 依次调用剩余过滤器，随后调用执行实际路由分派的 {@link TerminalHandler}。
 * 纯 JDK 实现，不依赖 Servlet API。
 */
public final class FilterChain {

    private final List<Filter> filters;
    private final TerminalHandler terminal;
    private int index = 0;

    public FilterChain(List<Filter> filters, TerminalHandler terminal) {
        this.filters = filters;
        this.terminal = terminal;
    }

    /** 前进到下一个过滤器，过滤器耗尽后调用终端处理器。 */
    public void doFilter(WebRequest request, WebResponse response) throws Exception {
        if (index < filters.size()) {
            Filter filter = filters.get(index);
            index++;
            filter.doFilter(request, response, this);
        } else {
            terminal.handle(request, response);
        }
    }

    @FunctionalInterface
    public interface TerminalHandler {
        void handle(WebRequest request, WebResponse response) throws Exception;
    }
}
