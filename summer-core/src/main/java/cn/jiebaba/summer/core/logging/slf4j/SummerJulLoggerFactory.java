package cn.jiebaba.summer.core.logging.slf4j;

import org.slf4j.ILoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link ILoggerFactory} backed by {@link java.util.logging.Logger}. Each logger
 * name maps to the corresponding JUL logger so that Summer's single logging
 * pipeline (handlers, levels and formatters configured by {@code LoggingInitializer})
 * owns all output.
 */
final class SummerJulLoggerFactory implements ILoggerFactory {

    private final ConcurrentMap<String, SummerJulLogger> cache = new ConcurrentHashMap<>();

    @Override
    public SummerJulLogger getLogger(String name) {
        if (name == null || name.isEmpty()) {
            name = "org.slf4j.default";
        }
        final String key = name;
        return cache.computeIfAbsent(key,
                n -> new SummerJulLogger(java.util.logging.Logger.getLogger(n)));
    }
}