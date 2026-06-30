package cn.jiebaba.summer.web.bind;

import java.lang.reflect.Method;

/**
 * Access check run by the dispatcher after route matching, before handler
 * invocation. Implementations throw to deny (the dispatcher maps the exception
 * to an HTTP status). Lets security enforce method-level annotations without
 * {@code summer-web} depending on the security module.
 */
@FunctionalInterface
public interface HandlerMethodAccessChecker {
    void check(Method handlerMethod) throws Exception;
}
