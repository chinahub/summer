package cn.jiebaba.summer.data.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** SQL dialect abstraction, primarily for pagination and identifier quoting. */
public interface Dialect {
    String name();
    /** Append pagination SQL to the builder and add bound params in the correct order. */
    void appendPagination(StringBuilder sql, long offset, long size, List<Object> params);
    /** Quote an identifier (e.g. `name` or "name"). */
    default String quote(String identifier) { return identifier; }
    /** Native column type used to store JSON for this database (e.g. "jsonb", "json", "CLOB"). */
    default String jsonColumnType() { return "json"; }
    /** Bind a JSON text value to a native JSON column. */
    default void setJsonParameter(PreparedStatement ps, int index, String json) throws SQLException {
        ps.setObject(index, json, Types.OTHER);
    }
    /** Read a JSON column back as text. */
    default String getJsonResult(ResultSet rs, int index) throws SQLException {
        return rs.getString(index);
    }
    /** Infer the dialect from the JDBC driver class name, or null if unknown. */
    static Dialect fromDriver(String driverClassName) {
        if (driverClassName == null || driverClassName.isBlank()) return null;
        String d = driverClassName.toLowerCase();
        if (d.contains("postgresql")) return new PostgreSqlDialect();
        if (d.contains("mysql") || d.contains("mariadb") || d.contains("h2")) return new MySqlDialect();
        if (d.contains("oracle")) return new OracleDialect();
        if (d.contains("sqlserver")) return new SqlServerDialect();
        return null;
    }

    /** Detect the dialect from the driver class name, falling back to the JDBC URL. */
    static Dialect detect(String driverClassName, String url) {
        Dialect byDriver = fromDriver(driverClassName);
        if (byDriver != null) return byDriver;
        return fromUrl(url);
    }

    /** Infer the dialect from a JDBC URL. */
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