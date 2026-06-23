package cn.jiebaba.summer.core.aop;

import java.lang.reflect.Method;

/** Runtime info about an intercepted method invocation. */
public interface JoinPoint {
    Object getThis();
    Object getTarget();
    Method getMethod();
    Object[] getArgs();
    String getSignature();
}
