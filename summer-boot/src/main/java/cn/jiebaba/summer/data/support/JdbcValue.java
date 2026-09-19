package cn.jiebaba.summer.data.support;

/**
 * 由 {@link SqlBuilder} 生成的参数载体，将原始值与其 {@link TypeHandler} 配对。
 * 它只持有 handler 引用（不做 JDBC 调用），使 {@code SqlBuilder} 不依赖 JDBC 且可单元测试。
 * 实际绑定发生在 {@link SqlExecutor} 中。
 */
public record JdbcValue(Object value, TypeHandler handler) {}
