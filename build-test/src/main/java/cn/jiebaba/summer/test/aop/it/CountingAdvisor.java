package cn.jiebaba.summer.test.aop.it;

import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.aop.MethodInterceptor;
import cn.jiebaba.summer.core.aop.ProxyAdvisor;

import java.util.List;

@Component
public class CountingAdvisor implements ProxyAdvisor {
    public int count = 0;

    @Override
    public boolean advises(Class<?> beanClass) {
        return beanClass == AdvisedTarget.class || beanClass == PingedImpl.class;
    }

    @Override
    public List<MethodInterceptor> interceptors() {
        return List.of(jp -> { count++; return jp.proceed(); });
    }
}