package cn.jiebaba.summer.data.support;

/**
 * A parameter carrier produced by {@link SqlBuilder} that pairs a raw value
 * with its {@link TypeHandler}. It carries only the handler reference (no JDBC
 * calls), keeping {@code SqlBuilder} free of JDBC and unit-testable. The actual
 * binding happens in {@link SqlExecutor}.
 */
public record JdbcValue(Object value, TypeHandler handler) {}