package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.data.annotation.IdType;
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

/**
 * Executes {@link SqlBuilder.Sql} over a {@link DataSource} and maps rows back to
 * entities via {@link TableInfo}. When a transaction is active
 * ({@link TransactionManager#currentConnection()}), the bound connection is reused
 * and NOT closed (the transaction manager owns it); otherwise a pooled connection
 * is borrowed and returned per statement.
 */
public final class SqlExecutor {

    private final DataSource dataSource;

    public SqlExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** A connection handle that closes only non-transactional connections. */
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
        // 1. single-source @Transactional
        Connection tx = TransactionManager.currentConnection();
        if (tx != null) return new Handle(tx, true);
        // 2. multi-source @DSTransactional
        if (DsTransactionManager.isActive()) {
            Connection dsConn = DsTransactionManager.getConnection(dataSource);
            if (dsConn != null) return new Handle(dsConn, true);
            // not yet borrowed for this datasource — borrow now (DsTransactionManager owns it)
            if (dataSource instanceof DynamicDataSource dds) {
                dsConn = DsTransactionManager.borrow(dds);
                return new Handle(dsConn, true);
            }
        }
        // 3. no transaction — fresh pooled connection
        return new Handle(dataSource.getConnection(), false);
    }

    public UpdateResult updateWithGeneratedKey(SqlBuilder.Sql sql, TableInfo table) {
        try (Handle h = open();
             PreparedStatement ps = h.connection.prepareStatement(sql.sql(), Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, sql.params());
            int affected = ps.executeUpdate();
            Object generatedKey = null;
            if (table.idType() == IdType.AUTO) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) generatedKey = keys.getObject(1);
                } catch (SQLException ignore) {}
            }
            return new UpdateResult(affected, generatedKey);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute update: " + sql.sql(), e);
        }
    }

    public int update(SqlBuilder.Sql sql) {
        try (Handle h = open();
             PreparedStatement ps = h.connection.prepareStatement(sql.sql())) {
            bind(ps, sql.params());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute update: " + sql.sql(), e);
        }
    }

    public <T> List<T> query(SqlBuilder.Sql sql, TableInfo table) {
        try (Handle h = open();
             PreparedStatement ps = h.connection.prepareStatement(sql.sql())) {
            bind(ps, sql.params());
            try (ResultSet rs = ps.executeQuery()) {
                return mapRows(rs, table);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute query: " + sql.sql(), e);
        }
    }

    public long count(SqlBuilder.Sql sql) {
        try (Handle h = open();
             PreparedStatement ps = h.connection.prepareStatement(sql.sql())) {
            bind(ps, sql.params());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute count: " + sql.sql(), e);
        }
    }

    private void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    @SuppressWarnings("unchecked")
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
                    Object value = rs.getObject(idx);
                    if (value != null) {
                        value = coerce(value, f.javaType());
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

    private static Object coerce(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (value instanceof BigDecimal bd) {
            // JDBC drivers often return numeric columns as BigDecimal; convert via
            // the Number API instead of toString()+parse, which breaks on scaled values.
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
        // temporal / date cross-type mapping (e.g. TIMESTAMP -> LocalDateTime)
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
            // value cannot be coerced to the target numeric type; keep the raw
            // value rather than failing the whole row (see #2).
            return value;
        }
        return value;
    }

    public record UpdateResult(int affectedRows, Object generatedKey) {}
}
