package cn.jiebaba.summer.core.aop;

/** Around 风格拦截器：包裹调用，调用 jp.proceed() 继续。 */
@FunctionalInterface
public interface MethodInterceptor {
    Object invoke(ProceedingJoinPoint jp) throws Throwable;

    /** 值越小越先执行（外层）。默认 {@link Integer#MAX_VALUE}（最内层）。 */
    default int order() { return Integer.MAX_VALUE; }
}
