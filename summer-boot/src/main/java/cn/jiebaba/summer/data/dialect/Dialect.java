package cn.jiebaba.summer.data.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/** SQL 方言抽象，主要用于分页与标识符引号处理。 */
public interface Dialect {

    Logger LOG = Logger.getLogger(Dialect.class.getName());

    /**
     * 跨数据库共享的保留字/易冲突关键字超集（小写）。命中时标识符需加引号转义。
     * 集合刻意宽松：误判无害（转义后与裸名解析到同一对象），漏判则会生成非法 SQL。
     */
    Set<String> RESERVED_WORDS = Set.of(
            "all", "alter", "and", "any", "as", "asc", "audit", "between", "by", "case",
            "check", "cluster", "column", "comment", "connect", "create", "current", "data",
            "database", "date", "default", "delete", "desc", "distinct", "drop", "else",
            "end", "exclusive", "exists", "explain", "fetch", "foreign", "from", "full",
            "function", "grant", "group", "having", "in", "index", "inner", "insert",
            "into", "is", "join", "key", "left", "level", "like", "limit", "mode", "not",
            "null", "offset", "on", "only", "or", "order", "outer", "partition", "primary",
            "procedure", "range", "references", "resource", "revoke", "right", "row",
            "rows", "schema", "select", "sequence", "session", "share", "size", "some",
            "start", "table", "then", "time", "transaction", "trigger", "type", "union",
            "unique", "update", "user", "value", "values", "view", "when", "where", "with");

    String name();

    /** 向 builder 追加分页 SQL，并按正确顺序添加绑定参数。 */
    void appendPagination(StringBuilder sql, long offset, long size, List<Object> params);

    /**
     * 按需转义标识符：命中保留字、含非法字符或以非字母/下划线开头时加引号，
     * 否则原样返回（保持裸名的大小写隐式解析行为，如 Oracle 未加引号自动转大写）。
     * 点号分段（schema.table）逐段转义。
     */
    default String quote(String identifier) {
        if (identifier == null || identifier.isEmpty()) return identifier;
        if (!identifier.contains(".")) {
            return needsQuoting(identifier) ? escapeQuote(identifier) : identifier;
        }
        String[] parts = identifier.split("\\.", -1);
        StringBuilder sb = new StringBuilder(identifier.length() + 8);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(needsQuoting(parts[i]) ? escapeQuote(parts[i]) : parts[i]);
        }
        return sb.toString();
    }

    /**
     * 无条件加引号转义（各方言的引号语法与大写规则不同）：
     * Oracle 双引号且转大写（与未加引号标识符的隐式大写存储一致）、
     * SQL Server 方括号、MySQL 反引号、PostgreSQL 双引号。
     */
    String escapeQuote(String identifier);

    /** 判断标识符是否需要转义：非常规字符/首字符非法/命中保留字超集。 */
    private static boolean needsQuoting(String identifier) {
        char first = identifier.charAt(0);
        if (!Character.isLetter(first) && first != '_') return true;
        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$' && c != '#') return true;
        }
        return RESERVED_WORDS.contains(identifier.toLowerCase());
    }

    /** 该数据库用于存储 JSON 的原生列类型（如 "jsonb"、"json"、"CLOB"）。 */
    default String jsonColumnType() { return "json"; }

    /**
     * 生成"存在则更新、不存在则插入"完整语句（upsert）：以 {@code conflictColumns}
     * 判定冲突（通常为主键），占位符顺序与 {@code insertColumns} 一致（由调用方按该顺序绑定值）。
     *
     * @param table           表名（原始名，方言内部自行转义）
     * @param insertColumns   插入列（原始名，含冲突列）
     * @param conflictColumns 冲突判定列（原始名，通常为主键）
     * @param updateColumns   冲突时更新的列（原始名，不含冲突列；为空则冲突时不做任何更新）
     * @return 完整 SQL（含 {@code ?} 占位符）
     */
    default String upsert(String table, List<String> insertColumns, List<String> conflictColumns, List<String> updateColumns) {
        throw new UnsupportedOperationException(name() + " 方言暂不支持 upsert");
    }

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
        if (d.contains("sqlite")) return new SqliteDialect();
        if (d.contains("mysql") || d.contains("mariadb")) return new MySqlDialect();
        if (d.contains("h2")) return new H2Dialect();
        if (d.contains("oracle")) return new OracleDialect();
        if (d.contains("sqlserver")) return new SqlServerDialect();
        LOG.warning("Unknown driver class '" + driverClassName + "', falling back to PostgreSQL dialect");
        return new PostgreSqlDialect();
    }

    /** 从驱动类名检测方言，回退到 JDBC URL。 */
    static Dialect detect(String driverClassName, String url) {
        Dialect byDriver = fromDriver(driverClassName);
        if (byDriver != null) return byDriver;
        return fromUrl(url);
    }

    /** 从 JDBC URL 推断方言；URL 为空回退 PostgreSQL（未配置数据源的正常场景，不告警）。 */
    static Dialect fromUrl(String url) {
        if (url == null || url.isBlank()) return new PostgreSqlDialect();
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:postgresql:")) return new PostgreSqlDialect();
        if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) return new MySqlDialect();
        if (lower.startsWith("jdbc:h2:")) return new H2Dialect();
        if (lower.startsWith("jdbc:sqlite:")) return new SqliteDialect();
        if (lower.startsWith("jdbc:oracle:")) return new OracleDialect();
        if (lower.startsWith("jdbc:sqlserver:")) return new SqlServerDialect();
        LOG.warning("Unknown JDBC URL '" + url + "', falling back to PostgreSQL dialect");
        return new PostgreSqlDialect();
    }

    static Dialect of(String name) {
        if (name == null) return new PostgreSqlDialect();
        return switch (name.toLowerCase()) {
            case "mysql", "mariadb" -> new MySqlDialect();
            case "h2" -> new H2Dialect();
            case "sqlite" -> new SqliteDialect();
            case "postgres", "postgresql", "pg" -> new PostgreSqlDialect();
            case "oracle" -> new OracleDialect();
            case "sqlserver", "mssql" -> new SqlServerDialect();
            default -> {
                LOG.warning("Unknown dialect name '" + name + "', falling back to PostgreSQL dialect");
                yield new PostgreSqlDialect();
            }
        };
    }
}
