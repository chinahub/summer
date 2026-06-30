package cn.jiebaba.summer.web.bind;

import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;
import cn.jiebaba.summer.web.routing.RouteMatch;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

/**
 * Strategy for resolving a handler-method parameter, evaluated before the built-in
 * binding in {@link HandlerMethodInvoker}. Lets extension modules (e.g. security,
 * which injects the authenticated principal) contribute parameter resolution
 * without {@code summer-web} depending on them.
 */
public interface HandlerMethodArgumentResolver {
    boolean supportsParameter(Parameter parameter);

    Object resolveArgument(Parameter parameter, Type genericType, RouteMatch match,
                           WebRequest request, WebResponse response) throws Exception;
}
