package cn.jiebaba.summer.core.aop.bytecode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Defines generated proxy classes; parent is the target class's loader. */
final class ProxyClassLoader extends ClassLoader {

    private final Map<String, Class<?>> cache = new ConcurrentHashMap<>();

    ProxyClassLoader(ClassLoader parent) {
        super(parent);
    }

    Class<?> define(String name, byte[] bytes) {
        return cache.computeIfAbsent(name, n -> {
            Class<?> klass = defineClass(n, bytes, 0, bytes.length);
            resolveClass(klass);
            return klass;
        });
    }
}