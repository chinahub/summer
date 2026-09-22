package cn.jiebaba.summer.core.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 为目标 bean 创建 JDK 动态代理，将一串 {@link MethodInterceptor}（来自切面与内置拦截器）
 * 应用到匹配的方法上。代理会实现目标的所有接口。
 */
public final class AdvisedProxyFactory {

    private AdvisedProxyFactory() {}

    public static boolean needsProxy(Class<?> targetClass, List<MethodInterceptor> interceptors, List<Advice> advices) {
        if (targetClass == null) return false;
        if (targetClass.getInterfaces().length == 0) return false;
        if (!interceptors.isEmpty()) return true;
        for (Method m : targetClass.getMethods()) {
            for (Advice a : advices) {
                if (PointcutMatcher.matches(a.pointcut(), targetClass, m)) return true;
            }
        }
        return false;
    }

    /**
     * 构造被增强的代理对象：组装内置拦截器与匹配的 advice 拦截链后生成代理实例。
     */
    public static Object createProxy(Object target, Class<?>[] interfaces,
                                     List<MethodInterceptor> interceptors, List<Advice> advices) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> target.getClass().getName() + "$AdvisedProxy";
                    case "hashCode" -> System.identityHashCode(target);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException();
                };
            }
            List<MethodInterceptor> chain = buildChain(target.getClass(), method, interceptors, advices);
            ReflectiveMethodInvocation inv = new ReflectiveMethodInvocation(
                    proxy, target, method, args, chain);
            return inv.proceed();
        };
        return Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                interfaces.length > 0 ? interfaces : target.getClass().getInterfaces(),
                handler);
    }

    /** 构建拦截器链（内置拦截器 + 匹配的 advice），按 order 排序。 */
    static List<MethodInterceptor> buildChain(Class<?> targetClass, Method method,
                                              List<MethodInterceptor> interceptors, List<Advice> advices) {
        List<MethodInterceptor> chain = new ArrayList<>(interceptors);
        for (Advice a : advices) {
            if (!PointcutMatcher.matches(a.pointcut(), targetClass, method)) continue;
            MethodInterceptor mi = toInterceptor(a);
            if (mi != null) chain.add(mi);
        }
        chain.sort(Comparator.comparingInt(MethodInterceptor::order));
        return chain;
    }

    private static MethodInterceptor toInterceptor(Advice a) {
        return switch (a.kind()) {
            case AROUND -> (jp) -> invokeAdvice(a, jp);
            case BEFORE -> (jp) -> { invokeAdvice(a, jp); return jp.proceed(); };
            case AFTER -> (jp) -> { try { return jp.proceed(); } finally { invokeAdvice(a, jp); } };
            case AFTER_RETURNING -> (jp) -> { Object ret = jp.proceed(); invokeAdvice(a, jp, ret); return ret; };
            case AFTER_THROWING -> (jp) -> {
                try { return jp.proceed(); }
                catch (Throwable t) { invokeAdvice(a, jp, t); throw t; }
            };
        };
    }

    private static Object invokeAdvice(Advice a, ProceedingJoinPoint jp) throws Throwable {
        return invokeAdvice(a, jp, null);
    }

    private static Object invokeAdvice(Advice a, ProceedingJoinPoint jp, Object extra) throws Throwable {
        Method m = a.adviceMethod();
        Class<?>[] params = m.getParameterTypes();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (params[i] == ProceedingJoinPoint.class || params[i] == JoinPoint.class) args[i] = jp;
            else if (extra != null && params[i].isInstance(extra)) args[i] = extra;
            else args[i] = null;
        }
        try {
            return m.invoke(a.aspectBean(), args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** 拦截器链尾：以给定参数调用目标方法。 */
    @FunctionalInterface
    interface TailInvoker {
        Object invoke(Object[] args) throws Throwable;
    }

    /** 反射式链调用，终止于目标方法（或自定义尾节点）。 */
    static final class ReflectiveMethodInvocation implements ProceedingJoinPoint {
        private final Object proxy;
        private final Object target;
        private final Method method;
        private Object[] args;
        private final List<MethodInterceptor> chain;
        private final TailInvoker tail;
        private int current = 0;

        ReflectiveMethodInvocation(Object proxy, Object target, Method method, Object[] args,
                                   List<MethodInterceptor> chain) {
            this(proxy, target, method, args, chain, null);
        }

        ReflectiveMethodInvocation(Object proxy, Object target, Method method, Object[] args,
                                   List<MethodInterceptor> chain, TailInvoker tail) {
            this.proxy = proxy; this.target = target; this.method = method;
            this.args = args; this.chain = chain; this.tail = tail;
        }

        @Override
        public Object proceed() throws Throwable {
            if (current < chain.size()) {
                MethodInterceptor next = chain.get(current++);
                return next.invoke(this);
            }
            if (tail != null) return tail.invoke(this.args);
            try {
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Override
        public Object proceed(Object[] args) throws Throwable {
            this.args = args;
            return proceed();
        }

        @Override public Object getThis() { return proxy; }
        @Override public Object getTarget() { return target; }
        @Override public Method getMethod() { return method; }
        @Override public Object[] getArgs() { return args; }
        @Override public String getSignature() {
            return target.getClass().getName() + "." + method.getName();
        }
    }
}
