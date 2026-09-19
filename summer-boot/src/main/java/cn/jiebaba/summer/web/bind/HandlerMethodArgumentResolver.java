package cn.jiebaba.summer.web.bind;

import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;
import cn.jiebaba.summer.web.routing.RouteMatch;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

/**
 * 处理器方法参数的解析策略，在 {@link HandlerMethodInvoker} 的内置绑定之前执行。
 * 使扩展模块（如安全模块注入已认证主体）能贡献参数解析，
 * 而 {@code summer-web} 无需依赖它们。
 */
public interface HandlerMethodArgumentResolver {
    boolean supportsParameter(Parameter parameter);

    Object resolveArgument(Parameter parameter, Type genericType, RouteMatch match,
                           WebRequest request, WebResponse response) throws Exception;
}
