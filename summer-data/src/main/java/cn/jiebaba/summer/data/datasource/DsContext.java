package cn.jiebaba.summer.data.datasource;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-local routing context for {@link DynamicDataSource}. Holds a stack of
 * datasource names so that nested {@code @DS} calls can override and restore.
 * When empty, {@link DynamicDataSource} uses its default (primary) datasource.
 */
public final class DsContext {

    public static final String MASTER = "master";
    public static final String SLAVE = "slave";

    private static final ThreadLocal<Deque<String>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private DsContext() {}

    /** Push a datasource name onto the routing stack. */
    public static void push(String name) {
        STACK.get().push(name);
    }

    /** Pop the top datasource name, restoring the previous one. */
    public static void pop() {
        Deque<String> stack = STACK.get();
        stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }

    /** Returns the current routing key, or {@code null} if none set (use default). */
    public static String current() {
        Deque<String> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /** Returns true if a routing key is set. */
    public static boolean isActive() {
        return !STACK.get().isEmpty();
    }

    /** Clears any routing state for the current thread. */
    public static void clear() {
        STACK.remove();
    }
}