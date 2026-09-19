package cn.jiebaba.summer.core.logging.slf4j;

import org.slf4j.ILoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 由 {@link java.util.logging.Logger} 支撑的 {@link ILoggerFactory}。每个 logger 名称
 * 映射到对应的 JUL logger，从而使 Summer 的单一日志管道（由 {@code LoggingInitializer}
 * 配置的 handlers、levels 与 formatters）接管所有输出。
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
