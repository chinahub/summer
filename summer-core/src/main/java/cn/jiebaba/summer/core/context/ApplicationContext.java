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
     * 将一个已有对象以给定名称注册为单例 bean，使其可由 {@link #getBean(String)} 解析、
     * 由 {@link #containsBean(String)} 发现。若已存在同名 bean 则抛出
     * {@link cn.jiebaba.summer.core.context.BeansException}；如需替换请先注销。
     */
    void registerBean(String name, Object bean);

    /**
     * 移除以给定名称注册的 bean 定义与单例实例，并调用其销毁生命周期回调
     * （PreDestroy / DisposableBean / destroy method）。若移除了 bean 返回 {@code true}，
     * 若不存在该 bean 返回 {@code false}。
     */
    boolean unregisterBean(String name);
}
