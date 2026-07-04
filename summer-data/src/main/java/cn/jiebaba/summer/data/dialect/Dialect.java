package cn.jiebaba.summer.data.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** SQL 方言抽象，主要用于分页与标识符引号处理。 */
public interface Dialect {
    String name();
    /** 向 builder 追加分页 SQL，并按正确顺序添加绑定参数。 */
    void appendPagination(StringBuilder sql, long offset, long size, List<Object> params);
    /** 为标识符加引号（如 `name` 或 "name"）。 */
    default String quote(String identifier) { return identifier; }
    /** 该数据库用于存储 JSON 的原生列类型（如 "jsonb"、"json"、"CLOB"）。 */
    default String jsonColumnType() { return "json"; }
    /** 将 JSON 文本值绑定到原生 JSON 列。 */
    default void setJsonParameter(PreparedStatement ps, int index, String json) throws SQLException {
        ps.setObject(index, json, Types.OTHER);
    }
    /** 将 JSON 列读回为文本。 */
    default String getJsonResult(ResultSet rs, int index) throws SQLException {
        return rs.getString(index);
    }
    /** 从 JDBC 驱动类名推断方言，未知时返回 null。 */
    static Dialect fromDriver(String driverClassName) {
        if (driverClassName == null || driverClassName.isBlank()) return null;
        String d = driverClassName.toLowerCase();
        if (d.contains("postgresql")) return new PostgreSqlDialect();
        if (d.contains("mysql") || d.contains("mariadb") || d.contains("h2")) return new MySqlDialect();
        if (d.contains("oracle")) return new OracleDialect();
        if (d.contains("sqlserver")) return new SqlServerDialect();
        return null;
    }

    /** 从驱动类名检测方言，回退到 JDBC URL。 */
    static Dialect detect(String driverClassName, String url) {
        Dialect byDriver = fromDriver(driverClassName);
        if (byDriver != null) return byDriver;
        return fromUrl(url);
    }

    /** 从 JDBC URL 推断方言。 */
    static Dialect fromUrl(String url) {
        if (url == null || url.isBlank()) return new PostgreSqlDialect();
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:postgresql:")) return new PostgreSqlDialect();
        if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) return new MySqlDialect();
        if (lower.startsWith("jdbc:oracle:")) return new OracleDialect();
        if (lower.startsWith("jdbc:sqlserver:")) return new SqlServerDialect();
        return new PostgreSqlDialect();
    }
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
