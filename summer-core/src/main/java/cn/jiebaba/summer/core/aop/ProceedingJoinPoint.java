package cn.jiebaba.summer.core.aop;

/** 可继续到下一个拦截器或目标方法的 JoinPoint。 */
public interface ProceedingJoinPoint extends JoinPoint {
    Object proceed() throws Throwable;
    Object proceed(Object[] args) throws Throwable;
}
