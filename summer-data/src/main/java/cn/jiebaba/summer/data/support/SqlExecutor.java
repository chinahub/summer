package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.metadata.TableFieldInfo;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.transaction.TransactionManager;
import cn.jiebaba.summer.data.datasource.DsTransactionManager;
import cn.jiebaba.summer.data.datasource.DynamicDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 在 {@link DataSource} 上执行 {@link SqlBuilder.Sql}，并通过 {@link TableInfo}
 * 将结果行映射回实体。当存在活动事务时（{@link TransactionManager#currentConnection()}），
 * 复用绑定连接且不关闭（由事务管理器持有）；否则每条语句借出并归还一个池化连接。
 */
public final class SqlExecutor {

    private static final Logger log = Logger.getLogger(SqlExecutor.class.getName());
    private static final int MAX_PARAMETER_LENGTH = 120;

    private final DataSource dataSource;
    private final Dialect dialect;

    public SqlExecutor(DataSource dataSource) {
        this(dataSource, Dialect.of(null));
    }

    public SqlExecutor(DataSource dataSource, Dialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public Dialect dialect() { return dialect; }

    /** 仅关闭非事务连接的连接句柄。 */
    private static final class Handle implements AutoCloseable {
        final Connection connection;
        final boolean transactional;
        Handle(Connection connection, boolean transactional) {
            this.connection = connection;
            this.transactional = transactional;
        }
        @Override public void close() throws SQLException {
            if (!transactional) connection.close();
        }
    }

    private Handle open() throws SQLException {
        // 1. 单数据源 @Transactional
        Connection tx = TransactionManager.currentConnection();
        if (tx != null) return new Handle(tx, true);
        // 2. 多数据源 @DSTransactional
        if (DsTransactionManager.isActive()) {
            Connection dsConn = DsTransactionManager.getConnection(dataSource);
            if (dsConn != null) return new Handle(dsConn, true);
            // 该数据源尚未借出连接 —— 现在借出（由 DsTransactionManager 持有）
            if (dataSource instanceof DynamicDataSource dds) {
                dsConn = DsTransactionManager.borrow(dds);
                return new Handle(dsConn, true);
            }
        }
        // 3. 无事务 —— 新建池化连接
        return new Handle(dataSource.getConnection(), false);
    }

    public UpdateResult updateWithGeneratedKey(SqlBuilder.Sql sql, TableInfo table) {
        try (Handle h = open();
             PreparedStatement ps = h.connection.prepareStatement(sql.sql(), Statement.RETURN_GENERATED_KEYS)) {
            long start = logBefore(sql);
            bind(ps, sql.params());
            int affected = ps.executeUpdate();
            Object generatedKey = null;
            if (table.idType() == IdType.AUTO) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) generatedKey = keys.getObject(1);
                } catch (SQLException ignore) {}
            }
            logUpdateResult(affected, start);
            return new UpdateResult(affected, generatedKey);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute update: " + sql.sql(), e);
        }
    }

    public int update(SqlBuilder.Sql sql) {
        try (Handle h = open();
             PreparedStatement ps = h.connection.prepareStatement(sql.sql())) {
            long start = logBefore(sql);
            bind(ps, sql.params());
            int affected = ps.executeUpdate();
            logUpdateResult(affected, start);
            return affected;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute update: " + sql.sql(), e);
        }
    }

    public <T> List<T> query(SqlBuilder.Sql sql, TableInfo table) {
        try (Handle h = open();
             PreparedStatement ps = h.connection.prepareStatement(sql.sql())) {
            long start = logBefore(sql);
            bind(ps, sql.params());
            try (ResultSet rs = ps.executeQuery()) {
                List<T> rows = mapRows(rs, table);
                logQueryResult(rows.size(), start);
                return rows;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute query: " + sql.sql(), e);
        }
    }

    public long count(SqlBuilder.Sql sql) {
        try (Handle h = open();
             PreparedStatement ps = h.connection.prepareStatement(sql.sql())) {
            long start = logBefore(sql);
            bind(ps, sql.params());
            try (ResultSet rs = ps.executeQuery()) {
                long count = rs.next() ? rs.getLong(1) : 0L;
                logQueryResult(count, start);
                return count;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute count: " + sql.sql(), e);
        }
    }

    private void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof JdbcValue jv) {
                jv.handler().setParameter(ps, i + 1, jv.value(), dialect);
            } else {
                ps.setObject(i + 1, param);
            }
        }
    }

    private long logBefore(SqlBuilder.Sql sql) {
        if (!log.isLoggable(Level.FINE)) return 0L;
        log.fine("==> Preparing: " + sql.sql());
        log.fine("==> Parameters: " + formatParameters(sql.params()));
        return System.nanoTime();
    }

    private void logUpdateResult(int affected, long start) {
        if (!log.isLoggable(Level.FINE)) return;
        log.fine("<== Updates: " + affected + elapsed(start));
    }

    private void logQueryResult(long total, long start) {
        if (!log.isLoggable(Level.FINE)) return;
        log.fine("<== Total: " + total + elapsed(start));
    }

    private String elapsed(long start) {
        if (start <= 0L) return "";
        long elapsedMicros = (System.nanoTime() - start) / 1_000L;
        if (elapsedMicros < 1_000L) return " (" + elapsedMicros + " µs)";
        return " (" + (elapsedMicros / 1_000L) + " ms)";
    }

    private String formatParameters(List<Object> params) {
        if (params == null || params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            Object param = unwrapParameter(params.get(i));
            if (param == null) {
                sb.append("null");
                continue;
            }
            sb.append(formatParameterValue(param))
                    .append('(').append(param.getClass().getSimpleName()).append(')');
        }
        return sb.toString();
    }

    private Object unwrapParameter(Object param) {
        return param instanceof JdbcValue jv ? jv.value() : param;
    }

    private String formatParameterValue(Object param) {
        if (param instanceof CharSequence text) {
            return abbreviate(text.toString());
        }
        if (param instanceof byte[] bytes) {
            return "<" + bytes.length + " bytes>";
        }
        return abbreviate(String.valueOf(param));
    }

    private String abbreviate(String value) {
        if (value.length() <= MAX_PARAMETER_LENGTH) return value;
        return value.substring(0, MAX_PARAMETER_LENGTH) + "...";
    }

    @SuppressWarnings("unchecked")
    /**
     * 将 {@link ResultSet} 的各行映射为实体列表，按列名匹配字段并做类型转换。
     */
    private <T> List<T> mapRows(ResultSet rs, TableInfo table) throws SQLException {
        List<T> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        Map<String, Integer> columnIndex = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
            columnIndex.put(meta.getColumnLabel(i).toLowerCase(), i);
        }
        while (rs.next()) {
            try {
                T entity = (T) table.entityType().getDeclaredConstructor().newInstance();
                for (TableFieldInfo f : table.fields()) {
                    Integer idx = columnIndex.get(f.column().toLowerCase());
                    if (idx == null) continue;
                    Object value;
                    if (f.typeHandler() != null) {
                        value = f.typeHandler().getResult(rs, idx, f.javaType(), dialect);
                    } else {
                        value = rs.getObject(idx);
                        if (value != null) value = coerce(value, f.javaType());
                    }
                    if (value != null) {
                        f.setValue(entity, value);
                    }
                }
                rows.add(entity);
            } catch (ReflectiveOperationException e) {
                throw new DataAccessException("Failed to map row to " + table.entityType(), e);
            }
        }
        return rows;
    }

    /**
     * 将值强制转换为目标类型：处理数字窄化、字符串、布尔、时间及枚举等跨类型映射；
     * 无法转换时保留原始值。
     */
    private static Object coerce(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (value instanceof BigDecimal bd) {
            // JDBC 驱动常将数值列以 BigDecimal 返回；通过 Number API 转换，
            // 而非 toString()+parse，后者在带小数位的值上会出错。
            if (targetType == int.class || targetType == Integer.class) return bd.intValue();
            if (targetType == long.class || targetType == Long.class) return bd.longValue();
            if (targetType == double.class || targetType == Double.class) return bd.doubleValue();
            if (targetType == float.class || targetType == Float.class) return bd.floatValue();
            if (targetType == short.class || targetType == Short.class) return bd.shortValue();
            if (targetType == byte.class || targetType == Byte.class) return bd.byteValue();
            if (targetType == String.class) return bd.toPlainString();
        }
        if (value instanceof BigInteger bi) {
            if (targetType == int.class || targetType == Integer.class) return bi.intValue();
            if (targetType == long.class || targetType == Long.class) return bi.longValue();
            if (targetType == double.class || targetType == Double.class) return bi.doubleValue();
            if (targetType == float.class || targetType == Float.class) return bi.floatValue();
            if (targetType == short.class || targetType == Short.class) return bi.shortValue();
            if (targetType == byte.class || targetType == Byte.class) return bi.byteValue();
            if (targetType == String.class) return bi.toString();
        }
        // 时间/日期跨类型映射（如 TIMESTAMP -> LocalDateTime）
        if (value instanceof java.sql.Timestamp ts) {
            if (targetType == LocalDateTime.class) return ts.toLocalDateTime();
            if (targetType == Instant.class) return ts.toInstant();
            if (targetType == LocalDate.class) return ts.toLocalDateTime().toLocalDate();
            if (targetType == LocalTime.class) return ts.toLocalDateTime().toLocalTime();
            if (targetType == OffsetDateTime.class) return ts.toInstant().atOffset(ZoneOffset.UTC);
            if (targetType == java.sql.Date.class) return new java.sql.Date(ts.getTime());
        }
        if (value instanceof java.sql.Date sd) {
            if (targetType == LocalDate.class) return sd.toLocalDate();
            if (targetType == java.sql.Timestamp.class) return new java.sql.Timestamp(sd.getTime());
        }
        if (value instanceof java.sql.Time st) {
            if (targetType == LocalTime.class) return st.toLocalTime();
            if (targetType == java.sql.Timestamp.class) return new java.sql.Timestamp(st.getTime());
        }
        if (value instanceof java.util.Date d) {
            if (targetType == LocalDateTime.class) return new java.sql.Timestamp(d.getTime()).toLocalDateTime();
            if (targetType == LocalDate.class) return new java.sql.Date(d.getTime()).toLocalDate();
            if (targetType == Instant.class) return d.toInstant();
            if (targetType == java.sql.Timestamp.class) return new java.sql.Timestamp(d.getTime());
        }
        if (value instanceof OffsetDateTime odt) {
            if (targetType == LocalDateTime.class) return odt.toLocalDateTime();
            if (targetType == Instant.class) return odt.toInstant();
        }
        if (value instanceof LocalDateTime ldt) {
            if (targetType == java.sql.Timestamp.class) return java.sql.Timestamp.valueOf(ldt);
            if (targetType == LocalDate.class) return ldt.toLocalDate();
        }
        if (value instanceof LocalDate ld) {
            if (targetType == java.sql.Date.class) return java.sql.Date.valueOf(ld);
        }
        if (targetType == BigDecimal.class) {
            if (value instanceof Number n) {
                if (value instanceof Long || value instanceof Integer
                        || value instanceof Short || value instanceof Byte) {
                    return BigDecimal.valueOf(n.longValue());
                }
                return BigDecimal.valueOf(n.doubleValue());
            }
            if (value instanceof String str) return new BigDecimal(str);
        }
        String s = value.toString();
        if (targetType == String.class) return s;
        try {
            if (targetType == int.class || targetType == Integer.class) return Integer.valueOf(s);
            if (targetType == long.class || targetType == Long.class) return Long.valueOf(s);
            if (targetType == double.class || targetType == Double.class) return Double.valueOf(s);
            if (targetType == float.class || targetType == Float.class) return Float.valueOf(s);
            if (targetType == boolean.class || targetType == Boolean.class) return Boolean.valueOf(s);
            if (targetType == short.class || targetType == Short.class) return Short.valueOf(s);
            if (targetType == byte.class || targetType == Byte.class) return Byte.valueOf(s);
            if (targetType.isEnum()) {
                return Enum.valueOf((Class<? extends Enum>) targetType, s);
            }
        } catch (NumberFormatException e) {
            // 值无法强制转换为目标数值类型；保留原始值，
            // 而非令整行失败（见 #2）。
            return value;
        }
        return value;
    }

    public record UpdateResult(int affectedRows, Object generatedKey) {}
}
