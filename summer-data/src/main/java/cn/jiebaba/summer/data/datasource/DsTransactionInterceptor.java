package cn.jiebaba.summer.data.datasource;

import cn.jiebaba.summer.core.aop.MethodInterceptor;
import cn.jiebaba.summer.core.aop.ProceedingJoinPoint;
import cn.jiebaba.summer.core.aop.ProxyAdvisor;
import cn.jiebaba.summer.core.scanner.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Intercepts methods annotated with {@link DSTransactional}. Begins a
 * multi-datasource transaction scope, commits on success, rolls back on failure.
 */
public final class DsTransactionInterceptor implements MethodInterceptor, ProxyAdvisor {

    private final DsTransactionManager transactionManager;

    public DsTransactionInterceptor(DsTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public boolean advises(Class<?> beanClass) {
        if (beanClass.isAnnotationPresent(DSTransactional.class)) return true;
        for (Method m : beanClass.getMethods()) {
            if (m.isAnnotationPresent(DSTransactional.class)) return true;
            if (AnnotationUtils.findAnnotation(m, DSTransactional.class) != null) return true;
        }
        return false;
    }

    @Override
    public List<MethodInterceptor> interceptors() {
        return List.of(this);
    }

    @Override
    public int order() {
        return 150; // between DsInterceptor(200) and TransactionInterceptor(100)
    }

    @Override
    public Object invoke(ProceedingJoinPoint jp) throws Throwable {
        DSTransactional tx = resolve(jp.getMethod(), jp.getTarget());
        if (tx == null) {
            return jp.proceed();
        }
        transactionManager.begin();
        try {
            Object result = jp.proceed();
            transactionManager.commit();
            return result;
        } catch (Throwable t) {
            if (shouldRollback(t, tx)) {
                transactionManager.rollback();
            }
            throw t;
        } finally {
            transactionManager.end();
        }
    }

    private DSTransactional resolve(Method method, Object target) {
        DSTransactional tx = method.getAnnotation(DSTransactional.class);
        if (tx == null) tx = AnnotationUtils.findAnnotation(method, DSTransactional.class);
        if (tx != null) return tx;
        Class<?> targetClass = target != null ? target.getClass() : method.getDeclaringClass();
        return AnnotationUtils.findAnnotation(targetClass, DSTransactional.class);
    }

    private boolean shouldRollback(Throwable t, DSTransactional tx) {
        for (Class<?> no : tx.noRollbackFor()) {
            if (no.isInstance(t)) return false;
        }
        if (tx.rollbackFor().length > 0) {
            for (Class<?> rb : tx.rollbackFor()) {
                if (rb.isInstance(t)) return true;
            }
            return false;
        }
        return t instanceof RuntimeException || t instanceof Error;
    }
}