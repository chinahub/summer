package cn.jiebaba.summer.web.routing;

import cn.jiebaba.summer.web.http.HttpMethod;

import java.lang.reflect.Method;

public final class RouteMapping {
    private final HttpMethod httpMethod;
    private final RoutePattern pattern;
    private final Object handlerBean;
    private final Method handlerMethod;
    private final String[] produces;

    public RouteMapping(HttpMethod httpMethod, RoutePattern pattern, Object handlerBean,
                        Method handlerMethod, String[] produces) {
        this.httpMethod = httpMethod;
        this.pattern = pattern;
        this.handlerBean = handlerBean;
        this.handlerMethod = handlerMethod;
        this.produces = produces;
    }

    public HttpMethod httpMethod() { return httpMethod; }
    public RoutePattern pattern() { return pattern; }
    public Object handlerBean() { return handlerBean; }
    public Method handlerMethod() { return handlerMethod; }
    public String[] produces() { return produces; }

    @Override
    public String toString() {
        return httpMethod + " " + pattern.pattern() + " -> " + handlerMethod;
    }
}
