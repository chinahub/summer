package cn.jiebaba.summer.core.util;

import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Static facade over the running {@link ApplicationContext}, inspired by the convenience
 * accessors frameworks expose for IoC lookups. Provides bean <em>get / register / unregister</em>
 * operations from anywhere in application code without injecting the context.
 *
 * <p>The context is bound by {@code SummerApplication.run()} via {@link #setContext}; calling any
 * accessor before that throws {@link IllegalStateException}. Bean registration is backed by
 * {@link ApplicationContext#registerBean(String, Object)} and unregistration by
 * {@link ApplicationContext#unregisterBean(String)}.
 */
public final class SummerUtil {

    private SummerUtil() {}

    private static volatile ApplicationContext context;

    /** Framework-managed: binds the running application context. */
    public static void setContext(ApplicationContext context) {
        SummerUtil.context = context;
    }

    /** Returns the bound context or throws if the framework has not been started. */
    public static ApplicationContext getContext() {
        ApplicationContext ctx = context;
        if (ctx == null) {
            throw new IllegalStateException(
                    "SummerUtil context is not initialized; start the application via SummerApplication.run() first");
        }
        return ctx;
    }

    /** Clears the bound context. Intended for tests / shutdown. */
    public static void clearContext() {
        context = null;
    }

    // ---- get -----------------------------------------------------------------

    public static Object getBean(String name) {
        return getContext().getBean(name);
    }

    public static <T> T getBean(String name, Class<T> requiredType) {
        return getContext().getBean(name, requiredType);
    }

    public static <T> T getBean(Class<T> requiredType) {
        return getContext().getBean(requiredType);
    }

    public static <T> T getBean(Class<T> requiredType, String qualifier) {
        return getContext().getBean(requiredType, qualifier);
    }

    public static boolean containsBean(String name) {
        return getContext().containsBean(name);
    }

    public static Class<?> getType(String name) {
        return getContext().getType(name);
    }

    public static String[] getBeanNamesForType(Class<?> type) {
        return getContext().getBeanNamesForType(type);
    }

    public static <T> Map<String, T> getBeansOfType(Class<T> type) {
        return getContext().getBeansOfType(type);
    }

    public static <T> Map<String, T> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
        return getContext().getBeansWithAnnotation(annotationType);
    }

    public static cn.jiebaba.summer.core.env.Environment getEnvironment() {
        return getContext().getEnvironment();
    }

    // ---- register ------------------------------------------------------------

    /** Register an existing object as a singleton bean under the given name. */
    public static void registerBean(String name, Object bean) {
        getContext().registerBean(name, bean);
    }

    /** Register an existing object as a singleton bean using its decapitalized class name. */
    public static void registerBean(Object bean) {
        if (bean == null) throw new IllegalArgumentException("bean must not be null");
        registerBean(DefaultApplicationContext.decapitalize(bean.getClass().getSimpleName()), bean);
    }

    // ---- unregister ----------------------------------------------------------

    /** Remove the bean registered under the given name. Returns {@code true} if removed. */
    public static boolean unregisterBean(String name) {
        return getContext().unregisterBean(name);
    }

    /** Remove all beans assignable to the given type. Returns the number of beans removed. */
    public static int unregisterBean(Class<?> type) {
        String[] names = getBeanNamesForType(type);
        int removed = 0;
        for (String name : names) {
            if (unregisterBean(name)) removed++;
        }
        return removed;
    }
}