package cn.jiebaba.summer.core.logging.slf4j;

import org.slf4j.spi.MDCAdapter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Summer SLF4J 绑定的线程级 MDC 适配器。MDC 状态按线程保存，因此 {@code MDC.put/get}
 * 可在程序中使用。它是一个极简、无依赖的适配器（不挂钩 JUL formatter），
 * 对应一种无附加功能的绑定。
 */
final class SummerMdcAdapter implements MDCAdapter {

    private final ThreadLocal<Map<String, String>> context =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>());
    private final ThreadLocal<Map<String, Deque<String>>> stacks =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>());

    @Override
    public void put(String key, String value) {
        if (key != null) context.get().put(key, value);
    }

    @Override
    public String get(String key) {
        return key == null ? null : context.get().get(key);
    }

    @Override
    public void remove(String key) {
        if (key != null) context.get().remove(key);
    }

    @Override
    public void clear() {
        context.get().clear();
    }

    @Override
    public Map<String, String> getCopyOfContextMap() {
        return new LinkedHashMap<>(context.get());
    }

    @Override
    public void setContextMap(Map<String, String> contextMap) {
        Map<String, String> map = context.get();
        map.clear();
        if (contextMap != null) map.putAll(contextMap);
    }

    @Override
    public void pushByKey(String key, String value) {
        if (key != null) stacks.get().computeIfAbsent(key, k -> new ArrayDeque<>()).push(value);
    }

    @Override
    public String popByKey(String key) {
        if (key == null) return null;
        Deque<String> deque = stacks.get().get(key);
        return deque == null ? null : deque.poll();
    }

    @Override
    public Deque<String> getCopyOfDequeByKey(String key) {
        if (key == null) return new ArrayDeque<>();
        Deque<String> deque = stacks.get().get(key);
        return deque == null ? new ArrayDeque<>() : new ArrayDeque<>(deque);
    }

    @Override
    public void clearDequeByKey(String key) {
        if (key != null) stacks.get().remove(key);
    }
}
