package cn.jiebaba.summer.core.aop;

import java.util.List;

/**
 * SPI for built-in interceptors (e.g. {@code @Transactional}). The container
 * asks every registered {@code ProxyAdvisor} whether it advises a bean class,
 * and applies the returned interceptors to that bean's proxy.
 */
public interface ProxyAdvisor {
    boolean advises(Class<?> beanClass);
    List<MethodInterceptor> interceptors();
}
