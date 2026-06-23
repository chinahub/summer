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
}
