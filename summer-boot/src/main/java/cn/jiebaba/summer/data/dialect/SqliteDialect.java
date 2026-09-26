package cn.jiebaba.summer.data.dialect;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * SQLite 方言：分页/引号与 MySQL 系一致（{@code LIMIT ? OFFSET ?}、反引号），
 * 但 upsert 语法不同——SQLite 不支持 {@code ON DUPLICATE KEY UPDATE}，
 * 需用 {@code ON CONFLICT (pk) DO UPDATE SET ...}（SQLite 3.24+）。
 */
public final class SqliteDialect implements Dialect {
    @Override public String name() { return "sqlite"; }
    @Override public void appendPagination(StringBuilder sql, long offset, long size, List<Object> params) {
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
    }
    @Override public String escapeQuote(String identifier) { return "`" + identifier + "`"; }
    @Override public String jsonColumnType() { return "text"; }
    @Override public void setJsonParameter(PreparedStatement ps, int index, String json) throws SQLException {
        ps.setString(index, json);
    }

    /** SQLite upsert：{@code ON CONFLICT (pk) DO UPDATE SET ...}（无更新列时 DO NOTHING）。 */
    @Override
    public String upsert(String table, List<String> insertColumns, List<String> conflictColumns, List<String> updateColumns) {
        StringBuilder sb = new StringBuilder("INSERT INTO ").append(quote(table))
                .append(" (").append(DialectSql.quotedJoin(insertColumns, this)).append(") VALUES (")
                .append(DialectSql.placeholders(insertColumns.size())).append(")")
                .append(" ON CONFLICT (").append(DialectSql.quotedJoin(conflictColumns, this)).append(")");
        if (updateColumns.isEmpty()) {
            sb.append(" DO NOTHING");
        } else {
            sb.append(" DO UPDATE SET ");
            for (int i = 0; i < updateColumns.size(); i++) {
                if (i > 0) sb.append(", ");
                String c = updateColumns.get(i);
                sb.append(quote(c)).append(" = excluded.").append(quote(c));
            }
        }
        return sb.toString();
    }
}
