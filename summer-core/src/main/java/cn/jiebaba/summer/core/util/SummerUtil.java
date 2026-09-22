package cn.jiebaba.summer.core.util;

import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * 运行中 {@link ApplicationContext} 的静态外观，灵感来自各类框架为 IoC 查找提供的便捷访问器。
 * 提供从应用代码任意位置进行的 bean <em>get / register / unregister</em> 操作，无需注入上下文。
 *
 * <p>上下文由 {@code SummerApplication.run()} 通过 {@link #setContext} 绑定；在此之前调用任意
 * 访问器都会抛出 {@link IllegalStateException}。bean 注册由
 * {@link ApplicationContext#registerBean(String, Object)} 支撑，注销由
 * {@link ApplicationContext#unregisterBean(String)} 支撑。
 */
public final class SummerUtil {

    private SummerUtil() {}

    private static volatile ApplicationContext context;

    /** 框架管理：绑定运行中的应用上下文。 */
    public static void setContext(ApplicationContext context) {
        SummerUtil.context = context;
    }

    /** 返回已绑定的上下文；框架未启动时抛出异常。 */
    public static ApplicationContext getContext() {
        ApplicationContext ctx = context;
        if (ctx == null) {
            throw new IllegalStateException(
                    "SummerUtil context is not initialized; start the application via SummerApplication.run() first");
        }
        return ctx;
    }

    /** 清除已绑定的上下文。用于测试 / 关闭。 */
    public static void clearContext() {
        context = null;
    }

    // ---- 获取 -----------------------------------------------------------------

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

    // ---- 注册 ------------------------------------------------------------

    /** 将已有对象以给定名称注册为单例 Bean。 */
    public static void registerBean(String name, Object bean) {
        getContext().registerBean(name, bean);
    }

    /** 以去首字母大写的类名将已有对象注册为单例 Bean。 */
    public static void registerBean(Object bean) {
        if (bean == null) throw new IllegalArgumentException("bean must not be null");
        registerBean(DefaultApplicationContext.decapitalize(bean.getClass().getSimpleName()), bean);
    }

    // ---- 注销 ----------------------------------------------------------

    /** 移除以给定名称注册的 Bean；移除成功返回 {@code true}。 */
    public static boolean unregisterBean(String name) {
        return getContext().unregisterBean(name);
    }

    /** 移除所有可赋值为给定类型的 Bean；返回移除数量。 */
    public static int unregisterBean(Class<?> type) {
        String[] names = getBeanNamesForType(type);
        int removed = 0;
        for (String name : names) {
            if (unregisterBean(name)) removed++;
        }
        return removed;
    }
}
