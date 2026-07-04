package cn.jiebaba.summer.core.aop;

import java.util.List;

/**
 * 内置拦截器（如 {@code @Transactional}）的 SPI。容器会询问每个已注册的
 * {@code ProxyAdvisor} 是否代理某个 bean 类，并将返回的拦截器应用到该 bean 的代理上。
 */
public interface ProxyAdvisor {
    boolean advises(Class<?> beanClass);
    List<MethodInterceptor> interceptors();
}
