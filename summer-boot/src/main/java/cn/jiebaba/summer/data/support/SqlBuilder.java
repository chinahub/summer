package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.conditions.QueryWrapper;
import cn.jiebaba.summer.data.dialect.PostgreSqlDialect;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.metadata.TableFieldInfo;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.metadata.NamingUtils;
import cn.jiebaba.summer.data.page.IPage;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯 SQL 生成器。将实体元数据与可选的 Wrapper 转换为带 {@code ?} 占位符的 SQL
 * 字符串及对应参数列表。此处不涉及 JDBC，因此无需数据库即可完整单元测试。
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
            columns.add(col(f.column()));
            params.add(wrap(f, value));
        }
        String sql = "INSERT INTO " + tbl()
                + " (" + String.join(", ", columns) + ")"
                + " VALUES (" + placeholders(columns.size()) + ")";
        return new Sql(sql, params);
    }

    /**
     * 批量插入：单语句多行 VALUES。列集合取"任一实体非空"的字段并集——全部为空的字段
     * 整列省略（由数据库默认值兜底），个别行为空的字段显式写 NULL。AUTO 自增主键列
     * 不参与（单语句多行无法跨方言回填自增 id，由 MapperSupport 降级逐条 insert）。
     */
    public Sql insertBatch(List<?> entities) {
        List<TableFieldInfo> candidates = new ArrayList<>();
        for (TableFieldInfo f : table.insertFields()) {
            if (table.idField() == f && table.idType() == IdType.AUTO) continue;
            candidates.add(f);
        }
        List<TableFieldInfo> columns = new ArrayList<>();
        for (TableFieldInfo f : candidates) {
            for (Object entity : entities) {
                if (f.getValue(entity) != null) {
                    columns.add(f);
                    break;
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("批量插入的实体没有任何非空字段: " + table.entityType().getName());
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tbl())
                .append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(col(columns.get(i).column()));
        }
        sql.append(") VALUES ");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append('(').append(placeholders(columns.size())).append(')');
            for (TableFieldInfo f : columns) {
                Object value = f.getValue(entities.get(i));
                params.add(value == null ? null : wrap(f, value));
            }
        }
        return new Sql(sql.toString(), params);
    }

    /**
     * upsert（存在则更新、不存在则插入）：以主键判定冲突，语句由方言生成；
     * 空值字段既不插入也不更新（与 {@link #insert} 的空值跳过语义一致）。
     * {@code @Version} 版本列不参与自动管理（由调用方自行给值）。
     */
    public Sql upsert(Object entity) {
        if (table.idField() == null) throw new IllegalStateException("Entity has no id field: " + table.entityType());
        List<TableFieldInfo> fields = table.insertFields();
        List<String> insertColumns = new ArrayList<>();
        List<String> updateColumns = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (TableFieldInfo f : fields) {
            Object value = f.getValue(entity);
            if (value == null) continue;
            insertColumns.add(f.column());
            params.add(wrap(f, value));
            if (f != table.idField()) updateColumns.add(f.column());
        }
        if (insertColumns.isEmpty()) {
            throw new IllegalArgumentException("upsert 的实体没有任何非空字段: " + table.entityType().getName());
        }
        String sql = dialect.upsert(table.qualifiedTableName(), insertColumns,
                List.of(table.idField().column()), updateColumns);
        return new Sql(sql, params);
    }

    public Sql updateById(Object entity) {
        if (table.idField() == null) throw new IllegalStateException("Entity has no id field: " + table.entityType());
        TableFieldInfo versionField = table.versionField();
        List<TableFieldInfo> fields = table.updateFields();
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (TableFieldInfo f : fields) {
            Object value = f.getValue(entity);
            if (value == null) continue;
            sets.add(col(f.column()) + " = ?");
            params.add(wrap(f, value));
        }
        if (versionField != null) {
            // 乐观锁：SET ..., version = version + 1 WHERE id = ? AND version = ?
            Object current = versionField.getValue(entity);
            if (current == null) {
                throw new IllegalStateException(
                        "乐观锁更新要求版本号非空（请先查询再更新）: " + table.entityType().getName());
            }
            sets.add(col(versionField.column()) + " = " + col(versionField.column()) + " + 1");
            params.add(table.idField().getValue(entity));
            params.add(wrap(versionField, current));
            String sql = "UPDATE " + tbl()
                    + (sets.isEmpty() ? "" : " SET " + String.join(", ", sets))
                    + " WHERE " + col(table.idField().column()) + " = ?"
                    + " AND " + col(versionField.column()) + " = ?";
            return new Sql(sql, params);
        }
        params.add(table.idField().getValue(entity));
        String sql = "UPDATE " + tbl()
                + (sets.isEmpty() ? "" : " SET " + String.join(", ", sets))
                + " WHERE " + col(table.idField().column()) + " = ?";
        return new Sql(sql, params);
    }

    public Sql deleteById(Object id) {
        if (table.idField() == null) throw new IllegalStateException("Entity has no id field");
        if (table.hasLogicDelete()) {
            String sql = "UPDATE " + tbl()
                    + " SET " + col(table.logicDeleteField().column()) + " = " + table.logicDeleteField().logicDeleteValue()
                    + " WHERE " + col(table.idField().column()) + " = ?";
            return new Sql(sql, List.of(id));
        }
        String sql = "DELETE FROM " + tbl()
                + " WHERE " + col(table.idField().column()) + " = ?";
        return new Sql(sql, List.of(id));
    }

    public Sql selectById(Object id) {
        StringBuilder sql = new StringBuilder("SELECT ").append(selectColumns(null))
                .append(" FROM ").append(tbl())
                .append(" WHERE ").append(col(table.idField().column())).append(" = ?");
        appendLogicDelete(sql, null);
        return new Sql(sql.toString(), List.of(id));
    }

    public Sql selectList(AbstractWrapper<?, ?> wrapper) {
        return selectList(wrapper, null);
    }

    public Sql selectList(AbstractWrapper<?, ?> wrapper, IPage<?> page) {
        StringBuilder sql = new StringBuilder("SELECT ").append(selectColumns(wrapper))
                .append(" FROM ").append(tbl());
        List<Object> params = new ArrayList<>();
        applyWhere(sql, wrapper, params);
        if (wrapper != null) {
            if (!wrapper.groupByClause().isEmpty()) {
                sql.append(resolveClause(wrapper.groupByClause(), " GROUP BY "));
            }
            if (!wrapper.orderByClause().isEmpty()) {
                sql.append(resolveClause(wrapper.orderByClause(), " ORDER BY "));
            }
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
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(tbl());
        List<Object> params = new ArrayList<>();
        applyWhere(sql, wrapper, params);
        return new Sql(sql.toString(), params);
    }

    private void applyWhere(StringBuilder sql, AbstractWrapper<?, ?> wrapper, List<Object> params) {
        String where = whereClause(wrapper, params);
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(where);
        } else if (table.hasLogicDelete()) {
            sql.append(" WHERE ").append(col(table.logicDeleteField().column()))
               .append(" = ").append(table.logicDeleteField().logicNotDeleteValue());
        }
    }

    private void appendLogicDelete(StringBuilder sql, AbstractWrapper<?, ?> wrapper) {
        if (table.hasLogicDelete()) {
            sql.append(" AND ").append(col(table.logicDeleteField().column()))
               .append(" = ").append(table.logicDeleteField().logicNotDeleteValue());
        }
    }

    /** 将 Wrapper 的片段（可能使用属性名）解析为基于列的 SQL。 */
    public String whereClause(AbstractWrapper<?, ?> wrapper, List<Object> params) {
        if (wrapper == null || wrapper.isEmpty()) return "";
        List<String> resolved = new ArrayList<>();
        for (int i = 0; i < wrapper.segments().size(); i++) {
            String seg = wrapper.segments().get(i);
            resolved.add(resolveSegment(seg));
        }
        if (wrapper instanceof LambdaQueryWrapper<?> lw && params != null) {
            // 参数已在 Wrapper 中添加；无需额外处理
        }
        if (params != null) params.addAll(wrapper.params());
        return AbstractWrapper.joinSegments(resolved);
    }

    private String resolveSegment(String segment) {
        String result = segment;
        for (TableFieldInfo f : table.fields()) {
            result = replacePropertyWithColumn(result, f.property(), col(f.column()));
        }
        return result;
    }

    /**
     * 解析 ORDER BY / GROUP BY 子句：保留前缀（如 " ORDER BY "），
     * 将其中的属性名逐个替换为实际列名，与 WHERE 段的解析规则保持一致。
     */
    private String resolveClause(String clause, String prefix) {
        String body = clause.substring(prefix.length());
        String resolved = resolveSegment(body);
        return prefix + resolved;
    }

    private String replacePropertyWithColumn(String segment, String property, String column) {
        if (column == null || property == null || property.isEmpty()) return segment;
        // 将整词出现的属性名替换为 SQL 列名。
        // quoteReplacement 会转义替换字符串；此前代码将结果与 null 比较，
        // 而该结果不可能为 null（输入为 null 时会抛异常），故该三元判断属于死代码（见 #1）.
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
                cols.add(col(f != null ? f.column() : NamingUtils.toSnakeCase(p)));
            }
            return String.join(", ", cols);
        }
        List<String> cols = new ArrayList<>();
        for (TableFieldInfo f : table.fields()) cols.add(col(f.column()));
        return String.join(", ", cols);
    }

    /** 列名按方言按需转义（保留字/特殊字符加引号，普通标识符保持裸名）。 */
    private String col(String column) { return dialect.quote(column); }

    /** 表名按方言按需转义（qualifiedTableName 的 schema 与表名分段处理）。 */
    private String tbl() { return dialect.quote(table.qualifiedTableName()); }

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
