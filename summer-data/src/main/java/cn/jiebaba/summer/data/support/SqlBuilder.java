package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.conditions.QueryWrapper;
import cn.jiebaba.summer.data.dialect.PostgreSqlDialect;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.metadata.TableFieldInfo;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.page.IPage;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure SQL generator. Turns entity metadata plus optional wrappers into SQL
 * strings with {@code ?} placeholders and the matching parameter list. No JDBC
 * here, so it is fully unit-testable without a database.
 */
public final class SqlBuilder {

    public record Sql(String sql, List<Object> params) {}

    private final TableInfo table;
    private final Dialect dialect;

    public SqlBuilder(TableInfo table) {
        this(table, new PostgreSqlDialect());
    }

    public SqlBuilder(TableInfo table, Dialect dialect) {
        this.table = table;
        this.dialect = dialect;
    }

    public Sql insert(Object entity) {
        List<TableFieldInfo> fields = table.insertFields();
        List<String> columns = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (TableFieldInfo f : fields) {
            Object value = f.getValue(entity);
            if (table.idField() == f && table.idType() == IdType.AUTO) {
                continue;
            }
            if (value == null) continue;
            columns.add(f.column());
            params.add(wrap(f, value));
        }
        String sql = "INSERT INTO " + table.qualifiedTableName()
                + " (" + String.join(", ", columns) + ")"
                + " VALUES (" + placeholders(columns.size()) + ")";
        return new Sql(sql, params);
    }

    public Sql updateById(Object entity) {
        if (table.idField() == null) throw new IllegalStateException("Entity has no id field: " + table.entityType());
        List<TableFieldInfo> fields = table.updateFields();
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (TableFieldInfo f : fields) {
            Object value = f.getValue(entity);
            if (value == null) continue;
            sets.add(f.column() + " = ?");
            params.add(wrap(f, value));
        }
        params.add(table.idField().getValue(entity));
        String sql = "UPDATE " + table.qualifiedTableName()
                + (sets.isEmpty() ? "" : " SET " + String.join(", ", sets))
                + " WHERE " + table.idField().column() + " = ?";
        return new Sql(sql, params);
    }

    public Sql deleteById(Object id) {
        if (table.idField() == null) throw new IllegalStateException("Entity has no id field");
        if (table.hasLogicDelete()) {
            String sql = "UPDATE " + table.qualifiedTableName()
                    + " SET " + table.logicDeleteField().column() + " = " + table.logicDeleteField().logicDeleteValue()
                    + " WHERE " + table.idField().column() + " = ?";
            return new Sql(sql, List.of(id));
        }
        String sql = "DELETE FROM " + table.qualifiedTableName()
                + " WHERE " + table.idField().column() + " = ?";
        return new Sql(sql, List.of(id));
    }

    public Sql selectById(Object id) {
        StringBuilder sql = new StringBuilder("SELECT ").append(selectColumns(null))
                .append(" FROM ").append(table.qualifiedTableName())
                .append(" WHERE ").append(table.idField().column()).append(" = ?");
        appendLogicDelete(sql, null);
        return new Sql(sql.toString(), List.of(id));
    }

    public Sql selectList(AbstractWrapper<?, ?> wrapper) {
        return selectList(wrapper, null);
    }

    public Sql selectList(AbstractWrapper<?, ?> wrapper, IPage<?> page) {
        StringBuilder sql = new StringBuilder("SELECT ").append(selectColumns(wrapper))
                .append(" FROM ").append(table.qualifiedTableName());
        List<Object> params = new ArrayList<>();
        applyWhere(sql, wrapper, params);
        if (wrapper != null) {
            sql.append(wrapper.groupByClause());
            sql.append(wrapper.orderByClause());
            if (page != null) {
                dialect.appendPagination(sql, page.offset(), page.size(), params);
            }
            if (!wrapper.lastSql().isEmpty()) {
                sql.append(' ').append(wrapper.lastSql());
            }
        }
        return new Sql(sql.toString(), params);
    }

    public Sql selectCount(AbstractWrapper<?, ?> wrapper) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table.qualifiedTableName());
        List<Object> params = new ArrayList<>();
        applyWhere(sql, wrapper, params);
        return new Sql(sql.toString(), params);
    }

    private void applyWhere(StringBuilder sql, AbstractWrapper<?, ?> wrapper, List<Object> params) {
        String where = whereClause(wrapper, params);
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(where);
        } else if (table.hasLogicDelete()) {
            sql.append(" WHERE ").append(table.logicDeleteField().column())
               .append(" = ").append(table.logicDeleteField().logicNotDeleteValue());
        }
    }

    private void appendLogicDelete(StringBuilder sql, AbstractWrapper<?, ?> wrapper) {
        if (table.hasLogicDelete()) {
            sql.append(" AND ").append(table.logicDeleteField().column())
               .append(" = ").append(table.logicDeleteField().logicNotDeleteValue());
        }
    }

    /** Resolve a wrapper's segments (which may use property names) into column-backed SQL. */
    public String whereClause(AbstractWrapper<?, ?> wrapper, List<Object> params) {
        if (wrapper == null || wrapper.isEmpty()) return "";
        List<String> resolved = new ArrayList<>();
        for (int i = 0; i < wrapper.segments().size(); i++) {
            String seg = wrapper.segments().get(i);
            resolved.add(resolveSegment(seg));
        }
        if (wrapper instanceof LambdaQueryWrapper<?> lw && params != null) {
            // params already added in wrapper; nothing extra
        }
        if (params != null) params.addAll(wrapper.params());
        return String.join(" AND ", resolved);
    }

    private String resolveSegment(String segment) {
        String result = segment;
        for (TableFieldInfo f : table.fields()) {
            result = replacePropertyWithColumn(result, f.property(), f.column());
        }
        return result;
    }

    private String replacePropertyWithColumn(String segment, String property, String column) {
        if (column == null || property == null || property.isEmpty()) return segment;
        // Replace whole-word property occurrences (propertyName) with the SQL column.
        // quoteReplacement escapes the replacement string; the previous code compared its
        // result to null, which is impossible (it throws on null input), so the ternary
        // was dead code (see #1).
        String regex = "\\b" + java.util.regex.Pattern.quote(property) + "\\b";
        return segment.replaceAll(regex, java.util.regex.Matcher.quoteReplacement(column));
    }

    private String selectColumns(AbstractWrapper<?, ?> wrapper) {
        if (wrapper instanceof QueryWrapper<?> qw && qw.hasSelect()) {
            return String.join(", ", qw.selectColumns());
        }
        if (wrapper instanceof LambdaQueryWrapper<?> lw && lw.hasSelect()) {
            List<String> cols = new ArrayList<>();
            for (String p : lw.selectProperties()) {
                TableFieldInfo f = table.field(p);
                cols.add(f != null ? f.column() : p);
            }
            return String.join(", ", cols);
        }
        List<String> cols = new ArrayList<>();
        for (TableFieldInfo f : table.fields()) cols.add(f.column());
        return String.join(", ", cols);
    }

    private static Object wrap(TableFieldInfo f, Object value) {
        return f.typeHandler() != null ? new JdbcValue(value, f.typeHandler()) : value;
    }

    private static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append('?');
        }
        return sb.toString();
    }
}
