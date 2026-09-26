package cn.jiebaba.summer.data.dialect;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * H2 方言：与 MySQL 系共用 {@code LIMIT ? OFFSET ?} 分页，但不可复用 MySqlDialect——
 * 反引号转义与 {@code ON DUPLICATE KEY UPDATE} 仅在 MySQL 兼容模式（{@code ;MODE=MySQL}）下可用，
 * 默认 Regular 模式会直接报语法错误。故标识符转义用标准双引号，
 * upsert 用 H2 原生 {@code MERGE INTO ... KEY(...)}（所有兼容模式通用，1.4 与 2.x 均支持）。
 */
public final class H2Dialect implements Dialect {
    @Override public String name() { return "h2"; }
    @Override public void appendPagination(StringBuilder sql, long offset, long size, List<Object> params) {
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
    }
    @Override public String escapeQuote(String identifier) { return "\"" + identifier + "\""; }
    @Override public String jsonColumnType() { return "json"; }
    @Override public void setJsonParameter(PreparedStatement ps, int index, String json) throws SQLException {
        ps.setString(index, json);
    }

    /**
     * H2 upsert：{@code MERGE INTO t (cols) KEY(pk) VALUES (...)}。
     * 命中 KEY 冲突时按 VALUES 更新全部列出列（仅列主键时等价 DO NOTHING），
     * 占位符顺序与 {@code insertColumns} 一致；KEY 列须有主键/唯一约束（调用方已保证显式主键）。
     */
    @Override
    public String upsert(String table, List<String> insertColumns, List<String> conflictColumns, List<String> updateColumns) {
        return new StringBuilder("MERGE INTO ").append(quote(table))
                .append(" (").append(DialectSql.quotedJoin(insertColumns, this)).append(") KEY (")
                .append(DialectSql.quotedJoin(conflictColumns, this)).append(") VALUES (")
                .append(DialectSql.placeholders(insertColumns.size())).append(")")
                .toString();
    }
}
