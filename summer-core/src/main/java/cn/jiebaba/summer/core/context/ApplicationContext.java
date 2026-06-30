package cn.jiebaba.summer.core.context;

import cn.jiebaba.summer.core.env.Environment;

import java.lang.annotation.Annotation;
import java.util.Map;

public interface ApplicationContext {
    Object getBean(String name);
    <T> T getBean(String name, Class<T> requiredType);
    <T> T getBean(Class<T> requiredType);
    <T> T getBean(Class<T> requiredType, String qualifier);
    boolean containsBean(String name);
    Class<?> getType(String name);
    String[] getBeanNamesForType(Class<?> type);
    <T> Map<String, T> getBeansOfType(Class<T> type);
    <T> Map<String, T> getBeansWithAnnotation(Class<? extends Annotation> annotationType);
    Environment getEnvironment();
    boolean isRunning();
    void close();

    /**
     * Register an existing object as a singleton bean under the given name so it can be
     * resolved by {@link #getBean(String)} and discovered by {@link #containsBean(String)}.
     * A {@link cn.jiebaba.summer.core.context.BeansException} is thrown if a bean with the
     * same name already exists; unregister it first to replace it.
     */
    void registerBean(String name, Object bean);

    /**
     * Remove the bean definition and singleton instance registered under the given name,
     * invoking its destroy lifecycle callbacks (PreDestroy / DisposableBean / destroy method).
     * Returns {@code true} if a bean was removed, {@code false} if no such bean existed.
     */
    boolean unregisterBean(String name);
}