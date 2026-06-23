package cn.jiebaba.summer.core.aop;

/** JoinPoint that can proceed to the next interceptor or the target method. */
public interface ProceedingJoinPoint extends JoinPoint {
    Object proceed() throws Throwable;
    Object proceed(Object[] args) throws Throwable;
}
