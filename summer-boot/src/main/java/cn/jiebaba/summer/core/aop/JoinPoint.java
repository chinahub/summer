package cn.jiebaba.summer.core.aop;

import java.lang.reflect.Method;

/** 被拦截方法调用的运行时信息。 */
public interface JoinPoint {
    Object getThis();
    Object getTarget();
    Method getMethod();
    Object[] getArgs();
    String getSignature();
}
