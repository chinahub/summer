package cn.jiebaba.summer.data.dialect;

import java.util.List;

/** SQL dialect abstraction, primarily for pagination and identifier quoting. */
public interface Dialect {
    String name();
    /** Append pagination SQL to the builder and add bound params in the correct order. */
    void appendPagination(StringBuilder sql, long offset, long size, List<Object> params);
    /** Quote an identifier (e.g. `name` or "name"). */
    default String quote(String identifier) { return identifier; }
    static Dialect of(String name) {
        if (name == null) return new PostgreSqlDialect();
        return switch (name.toLowerCase()) {
            case "mysql", "mariadb", "h2" -> new MySqlDialect();
            case "postgres", "postgresql", "pg" -> new PostgreSqlDialect();
            case "oracle" -> new OracleDialect();
            case "sqlserver", "mssql" -> new SqlServerDialect();
            default -> new PostgreSqlDialect();
        };
    }
}
