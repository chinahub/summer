package cn.jiebaba.summer.core.aop;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Per-proxy-class dispatch state: the target methods (with their original
 * annotations), the matching bridge methods, and the interceptor/advice sets.
 * Registered against the generated proxy class and looked up by
 * {@link SubclassProxyFactory#intercept} on every invocation.
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
        AdvisedProxyFactory.TailInvoker tail = a -> bridge.invoke(proxy, a);
        AdvisedProxyFactory.ReflectiveMethodInvocation inv = new AdvisedProxyFactory.ReflectiveMethodInvocation(proxy, proxy, method, args, chain, tail);
        return inv.proceed();
    }
}