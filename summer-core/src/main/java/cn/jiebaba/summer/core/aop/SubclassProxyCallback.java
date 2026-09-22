package cn.jiebaba.summer.core.aop;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 每个代理类对应的分派状态：目标方法（及其原始注解）、匹配的 bridge 方法，
 * 以及拦截器/切面集合。注册到生成的代理类上，并在每次调用时由
 * {@link SubclassProxyFactory#intercept} 查找。
 */
final class SubclassProxyCallback {

    private final Class<?> targetClass;
    private final Method[] targetMethods;
    private final Method[] bridgeMethods;
    private final List<MethodInterceptor> interceptors;
    private final List<Advice> advices;

    SubclassProxyCallback(Class<?> targetClass, Method[] targetMethods, Method[] bridgeMethods,
                          List<MethodInterceptor> interceptors, List<Advice> advices) {
        this.targetClass = targetClass;
        this.targetMethods = targetMethods;
        this.bridgeMethods = bridgeMethods;
        this.interceptors = interceptors;
        this.advices = advices;
    }

    Object dispatch(int methodIndex, Object proxy, Object[] args) throws Throwable {
        Method method = targetMethods[methodIndex];
        List<MethodInterceptor> chain = AdvisedProxyFactory.buildChain(targetClass, method, interceptors, advices);
        Method bridge = bridgeMethods[methodIndex];
        AdvisedProxyFactory.TailInvoker tail = a -> {
            try {
                return bridge.invoke(proxy, a);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        AdvisedProxyFactory.ReflectiveMethodInvocation inv = new AdvisedProxyFactory.ReflectiveMethodInvocation(proxy, proxy, method, args, chain, tail);
        return inv.proceed();
    }
}
