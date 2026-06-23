package cn.jiebaba.summer.core.aop;

/** Around-style interceptor: wrap the invocation, call jp.proceed() to continue. */
@FunctionalInterface
public interface MethodInterceptor {
    Object invoke(ProceedingJoinPoint jp) throws Throwable;

    /** Lower runs first (outer). Default {@link Integer#MAX_VALUE} (innermost). */
    default int order() { return Integer.MAX_VALUE; }
}
