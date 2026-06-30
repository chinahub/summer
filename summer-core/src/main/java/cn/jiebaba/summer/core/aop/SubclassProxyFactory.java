package cn.jiebaba.summer.core.aop;

import cn.jiebaba.summer.core.aop.bytecode.ClassBuilder;
import cn.jiebaba.summer.core.aop.bytecode.Descriptor;
import cn.jiebaba.summer.core.context.BeansException;
import cn.jiebaba.summer.core.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Creates CGLIB-style subclass proxies by hand-generating class files (zero
 * third-party deps). The proxy overrides every public/protected non-final
 * method to delegate to {@link #intercept}; a {@code $$summer$super$<name>}
 * bridge ({@code invokespecial super.<name>}) breaks self-invocation recursion.
 * Uses a single-object model: the proxy instance is the bean itself.
 */
public final class SubclassProxyFactory {

    private static final WeakHashMap<Class<?>, SubclassProxyCallback> CALLBACKS = new WeakHashMap<>();

    private SubclassProxyFactory() {}

    public static Object create(Class<?> targetClass, Constructor<?> ctor, Object[] ctorArgs,
                                List<MethodInterceptor> interceptors, List<Advice> advices) {
        if (Modifier.isFinal(targetClass.getModifiers())) {
            throw new BeansException("Cannot create subclass proxy for final class: " + targetClass.getName());
        }
        List<Method> methods = collectMethods(targetClass);
        String factoryInternal = Descriptor.internalName(SubclassProxyFactory.class);
        Class<?> proxyClass = ClassBuilder.defineProxyClass(targetClass, methods, ctor, factoryInternal);

        try {
            Method[] bridgeMethods = new Method[methods.size()];
            for (int i = 0; i < methods.size(); i++) {
                Method m = methods.get(i);
                Method bridge = proxyClass.getDeclaredMethod(bridgeName(m.getName()), m.getParameterTypes());
                ReflectionUtils.makeAccessible(bridge);
                bridgeMethods[i] = bridge;
            }
            SubclassProxyCallback callback = new SubclassProxyCallback(targetClass,
                    methods.toArray(new Method[0]), bridgeMethods, interceptors, advices);
            synchronized (CALLBACKS) {
                CALLBACKS.put(proxyClass, callback);
            }
            Constructor<?> proxyCtor = proxyClass.getDeclaredConstructor(ctor.getParameterTypes());
            ReflectionUtils.makeAccessible(proxyCtor);
            return proxyCtor.newInstance(ctorArgs);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new BeansException("Failed to create subclass proxy for " + targetClass.getName(), cause);
        }
    }

    /** Entry point invoked by every generated override method. */
    public static Object intercept(Object proxy, int methodIndex, Object[] args) throws Throwable {
        SubclassProxyCallback callback;
        synchronized (CALLBACKS) {
            callback = CALLBACKS.get(proxy.getClass());
        }
        if (callback == null) {
            throw new IllegalStateException("No subclass proxy callback for " + proxy.getClass().getName());
        }
        return callback.dispatch(methodIndex, proxy, args);
    }

    private static String bridgeName(String methodName) {
        return "$$summer$super$" + methodName;
    }

    /** Collects public/protected non-final non-static methods up the hierarchy, plus Object toString/hashCode/equals. */
    static List<Method> collectMethods(Class<?> targetClass) {
        List<Method> methods = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Class<?> c = targetClass;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                int mod = m.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isFinal(mod) || Modifier.isPrivate(mod)) continue;
                if (m.isBridge() || m.isSynthetic()) continue;
                if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)) continue;
                if (seen.add(m.getName() + Descriptor.of(m))) methods.add(m);
            }
            c = c.getSuperclass();
        }
        addObjectMethod(methods, seen, "toString");
        addObjectMethod(methods, seen, "hashCode");
        addObjectMethod(methods, seen, "equals", Object.class);
        return methods;
    }

    private static void addObjectMethod(List<Method> methods, Set<String> seen,
                                        String name, Class<?>... params) {
        try {
            Method m = Object.class.getMethod(name, params);
            if (seen.add(m.getName() + Descriptor.of(m))) methods.add(m);
        } catch (NoSuchMethodException ignore) {
        }
    }
}