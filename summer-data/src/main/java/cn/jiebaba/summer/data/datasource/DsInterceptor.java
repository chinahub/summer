package cn.jiebaba.summer.data.datasource;

import cn.jiebaba.summer.core.aop.MethodInterceptor;
import cn.jiebaba.summer.core.aop.ProceedingJoinPoint;
import cn.jiebaba.summer.core.aop.ProxyAdvisor;
import cn.jiebaba.summer.core.scanner.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Intercepts methods annotated with {@link DS}, {@link Master}, or {@link Slave}
 * and pushes the datasource name onto {@link DsContext} for the duration of the
 * call, restoring the previous routing key afterwards.
 */
public final class DsInterceptor implements MethodInterceptor, ProxyAdvisor {

    @Override
    public boolean advises(Class<?> beanClass) {
        if (beanClass.isAnnotationPresent(DS.class)
                || beanClass.isAnnotationPresent(Master.class)
                || beanClass.isAnnotationPresent(Slave.class)) {
            return true;
        }
        for (Method m : beanClass.getMethods()) {
            if (hasDsAnnotation(m)) return true;
        }
        return false;
    }

    @Override
    public List<MethodInterceptor> interceptors() {
        return List.of(this);
    }

    @Override
    public int order() {
        return 200; // outer to @Transactional so the routing is set before tx begin
    }

    @Override
    public Object invoke(ProceedingJoinPoint jp) throws Throwable {
        String dsName = resolveDsName(jp.getMethod(), jp.getTarget());
        if (dsName == null) {
            return jp.proceed();
        }
        DsContext.push(dsName);
        try {
            return jp.proceed();
        } finally {
            DsContext.pop();
        }
    }

    private String resolveDsName(Method method, Object target) {
        DS ds = method.getAnnotation(DS.class);
        if (ds == null) ds = AnnotationUtils.findAnnotation(method, DS.class);
        if (ds != null) return ds.value();

        if (method.isAnnotationPresent(Master.class)
                || AnnotationUtils.findAnnotation(method, Master.class) != null) {
            return DsContext.MASTER;
        }
        if (method.isAnnotationPresent(Slave.class)
                || AnnotationUtils.findAnnotation(method, Slave.class) != null) {
            return DsContext.SLAVE;
        }

        // check class-level
        Class<?> targetClass = target != null ? target.getClass() : method.getDeclaringClass();
        DS classDs = AnnotationUtils.findAnnotation(targetClass, DS.class);
        if (classDs != null) return classDs.value();
        if (AnnotationUtils.findAnnotation(targetClass, Master.class) != null) return DsContext.MASTER;
        if (AnnotationUtils.findAnnotation(targetClass, Slave.class) != null) return DsContext.SLAVE;

        return null;
    }

    private boolean hasDsAnnotation(Method m) {
        return m.isAnnotationPresent(DS.class)
                || m.isAnnotationPresent(Master.class)
                || m.isAnnotationPresent(Slave.class)
                || AnnotationUtils.findAnnotation(m, DS.class) != null
                || AnnotationUtils.findAnnotation(m, Master.class) != null
                || AnnotationUtils.findAnnotation(m, Slave.class) != null;
    }
}