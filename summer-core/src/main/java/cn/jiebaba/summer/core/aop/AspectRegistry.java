package cn.jiebaba.summer.core.aop;

import cn.jiebaba.summer.core.annotation.Order;
import cn.jiebaba.summer.core.scanner.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 从 {@link Aspect @Aspect} Bean 中收集通知。 */
public final class AspectRegistry {

    private final List<Advice> advices = new ArrayList<>();

    public void registerAspect(Object aspectBean) {
        Class<?> type = aspectBean.getClass();
        java.util.Map<String, String> pointcuts = new java.util.HashMap<>();
        for (Method m : type.getDeclaredMethods()) {
            Pointcut pc = m.getAnnotation(Pointcut.class);
            if (pc != null) pointcuts.put(m.getName(), pc.value());
        }
        for (Method m : type.getDeclaredMethods()) {
            registerAdviceMethod(aspectBean, m, pointcuts);
        }
        advices.sort(Comparator.comparingInt(Advice::order));
    }

    /**
     * 登记一个 @Aspect 通知方法：解析其切点表达式并按通知类型（before/after/around）归类。
     */
    private void registerAdviceMethod(Object aspectBean, Method m, java.util.Map<String, String> pointcuts) {
        Around around = m.getAnnotation(Around.class);
        Before before = m.getAnnotation(Before.class);
        After after = m.getAnnotation(After.class);
        AfterReturning afterRet = m.getAnnotation(AfterReturning.class);
        AfterThrowing afterThrow = m.getAnnotation(AfterThrowing.class);
        String expr = null;
        Advice.Kind kind = null;
        if (around != null) { expr = around.value(); kind = Advice.Kind.AROUND; }
        else if (before != null) { expr = before.value(); kind = Advice.Kind.BEFORE; }
        else if (after != null) { expr = after.value(); kind = Advice.Kind.AFTER; }
        else if (afterRet != null) { expr = afterRet.value(); kind = Advice.Kind.AFTER_RETURNING; }
        else if (afterThrow != null) { expr = afterThrow.value(); kind = Advice.Kind.AFTER_THROWING; }
        if (expr == null) return;
        // 支持引用具名 @Pointcut 方法
        if (pointcuts.containsKey(expr)) expr = pointcuts.get(expr);
        int order = Integer.MAX_VALUE;
        Order orderAnn = AnnotationUtils.findAnnotation(m, Order.class);
        if (orderAnn != null) order = orderAnn.value();
        advices.add(new Advice(kind, aspectBean, m, expr, order));
    }

    public List<Advice> advices() { return advices; }

    public List<Advice> matching(Class<?> targetClass, Method method) {
        List<Advice> result = new ArrayList<>();
        for (Advice a : advices) {
            if (PointcutMatcher.matches(a.pointcut(), targetClass, method)) result.add(a);
        }
        return result;
    }

    public boolean hasAdviceFor(Class<?> targetClass) {
        if (advices.isEmpty()) return false;
        for (Method m : targetClass.getMethods()) {
            for (Advice a : advices) {
                if (PointcutMatcher.matches(a.pointcut(), targetClass, m)) return true;
            }
        }
        return false;
    }
}
