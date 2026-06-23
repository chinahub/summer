package cn.jiebaba.summer.core.aop;

import java.lang.reflect.Method;

/** A registered advice (before/after/around) bound to a pointcut expression. */
public final class Advice {
    public enum Kind { BEFORE, AFTER, AFTER_RETURNING, AFTER_THROWING, AROUND }

    private final Kind kind;
    private final Object aspectBean;
    private final Method adviceMethod;
    private final String pointcutExpression;
    private final int order;

    public Advice(Kind kind, Object aspectBean, Method adviceMethod, String pointcutExpression, int order) {
        this.kind = kind;
        this.aspectBean = aspectBean;
        this.adviceMethod = adviceMethod;
        this.pointcutExpression = pointcutExpression;
        this.order = order;
        adviceMethod.setAccessible(true);
    }

    public Kind kind() { return kind; }
    public Object aspectBean() { return aspectBean; }
    public Method adviceMethod() { return adviceMethod; }
    public String pointcut() { return pointcutExpression; }
    public int order() { return order; }
}
