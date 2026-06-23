package cn.jiebaba.summer.data.transaction;

import cn.jiebaba.summer.core.aop.MethodInterceptor;
import cn.jiebaba.summer.core.aop.ProceedingJoinPoint;
import cn.jiebaba.summer.core.aop.ProxyAdvisor;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Built-in interceptor for {@link Transactional @Transactional}. Advises any bean
 * that declares a @Transactional method. Wraps the invocation in a
 * begin/commit/rollback block using {@link TransactionManager}.
 */
public final class TransactionInterceptor implements MethodInterceptor, ProxyAdvisor {

    private final TransactionManager transactionManager;

    public TransactionInterceptor(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public boolean advises(Class<?> beanClass) {
        for (Method m : beanClass.getMethods()) {
            if (m.isAnnotationPresent(Transactional.class)) return true;
            Class<?> c = m.getDeclaringClass();
            if (c.isAnnotationPresent(Transactional.class)) return true;
        }
        return beanClass.isAnnotationPresent(Transactional.class);
    }

    @Override
    public List<MethodInterceptor> interceptors() {
        return List.of(this);
    }

    @Override
    public int order() {
        return 100; // outer-ish: transactions wrap most other advice
    }

    @Override
    public Object invoke(ProceedingJoinPoint jp) throws Throwable {
        Method method = jp.getMethod();
        Transactional tx = resolveTransactional(method, jp.getTarget());
        if (tx == null) {
            return jp.proceed();
        }
        boolean began = transactionManager.begin();
        try {
            Object result = jp.proceed();
            if (began) transactionManager.commit();
            return result;
        } catch (Throwable t) {
            if (began && shouldRollback(t, tx)) {
                transactionManager.rollback();
            }
            throw t;
        } finally {
            if (began) transactionManager.end(true);
        }
    }

    private Transactional resolveTransactional(Method method, Object target) {
        Transactional tx = method.getAnnotation(Transactional.class);
        if (tx != null) return tx;
        Class<?> declaring = method.getDeclaringClass();
        if (declaring.isAnnotationPresent(Transactional.class)) return declaring.getAnnotation(Transactional.class);
        // the annotation may be on the target class's overriding method (not the interface method)
        if (target != null) {
            try {
                Method impl = target.getClass().getMethod(method.getName(), method.getParameterTypes());
                if (impl.isAnnotationPresent(Transactional.class)) return impl.getAnnotation(Transactional.class);
                if (impl.getDeclaringClass().isAnnotationPresent(Transactional.class)) return impl.getDeclaringClass().getAnnotation(Transactional.class);
            } catch (NoSuchMethodException ignore) {}
        }
        return null;
    }

    private boolean shouldRollback(Throwable t, Transactional tx) {
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
