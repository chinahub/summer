package cn.jiebaba.summer.web.filter;

import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.util.List;

/**
 * Sequentially invokes the remaining filters, then a {@link TerminalHandler} that
 * performs the actual route dispatch. Pure JDK, no servlet API.
 */
public final class FilterChain {

    private final List<Filter> filters;
    private final TerminalHandler terminal;
    private int index = 0;

    public FilterChain(List<Filter> filters, TerminalHandler terminal) {
        this.filters = filters;
        this.terminal = terminal;
    }

    /** Advance to the next filter, or to the terminal handler once filters are exhausted. */
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
