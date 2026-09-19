package cn.jiebaba.summer.data.datasource;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * {@link DynamicDataSource} 的线程局部路由上下文。维护数据源名称栈，
 * 使嵌套的 {@code @DS} 调用可覆盖并恢复。栈为空时，{@link DynamicDataSource}
 * 使用默认（主）数据源。
 */
public final class DsContext {

    public static final String MASTER = "master";
    public static final String SLAVE = "slave";

    private static final ThreadLocal<Deque<String>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private DsContext() {}

    /** 将数据源名称压入路由栈。 */
    public static void push(String name) {
        STACK.get().push(name);
    }

    /** 弹出栈顶数据源名称，恢复上一个。 */
    public static void pop() {
        Deque<String> stack = STACK.get();
        stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }

    /** 返回当前路由键，未设置时返回 {@code null}（使用默认）。 */
    public static String current() {
        Deque<String> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /** 是否已设置路由键。 */
    public static boolean isActive() {
        return !STACK.get().isEmpty();
    }

    /** 清除当前线程的路由状态。 */
    public static void clear() {
        STACK.remove();
    }
}
