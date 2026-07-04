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
 * 通过手写字节码生成 class 文件来创建 CGLIB 风格的子类代理（零第三方依赖）。
 * 代理会覆写每个 public/protected 且非 final 的方法以委托给 {@link #intercept}；
 * 一个 {@code $$summer$super$<name>} bridge（{@code invokespecial super.<name>}）
 * 用于打破自调用递归。采用单对象模型：代理实例即 bean 本身。
 */
public final class SubclassProxyFactory {

    private static final WeakHashMap<Class<?>, SubclassProxyCallback> CALLBACKS = new WeakHashMap<>();

    private SubclassProxyFactory() {}

    /**
     * 通过生成目标类的子类代理创建增强实例，覆写可拦截方法并转发到拦截器。
     */
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

    /** 每个生成的覆写方法所调用的入口。 */
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

    /** 收集层级中 public/protected、非 final、非 static 的方法，以及 Object 的 toString/hashCode/equals。 */
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
