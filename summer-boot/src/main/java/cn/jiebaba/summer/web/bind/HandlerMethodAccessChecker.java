package cn.jiebaba.summer.web.bind;

import java.lang.reflect.Method;

/**
 * 由调度器在路由匹配之后、处理器调用之前执行的访问检查。实现类通过抛出异常来拒绝访问
 * （调度器将异常映射为 HTTP 状态码）。借此安全模块可强制方法级注解校验，
 * 而 {@code summer-web} 无需依赖安全模块。
 */
@FunctionalInterface
public interface HandlerMethodAccessChecker {
    void check(Method handlerMethod) throws Exception;
}
