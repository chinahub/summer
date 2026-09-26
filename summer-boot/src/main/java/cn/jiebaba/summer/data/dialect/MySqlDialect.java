package cn.jiebaba.summer.data.dialect;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class MySqlDialect implements Dialect {
    @Override public String name() { return "mysql"; }
    @Override public void appendPagination(StringBuilder sql, long offset, long size, List<Object> params) {
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
    }
    @Override public String escapeQuote(String identifier) { return "`" + identifier + "`"; }
    @Override public String jsonColumnType() { return "json"; }
    @Override public void setJsonParameter(PreparedStatement ps, int index, String json) throws SQLException {
        ps.setString(index, json);
    }

    /** MySQL 系 upsert：{@code ON DUPLICATE KEY UPDATE}（冲突判定走唯一键，VALUES() 写法兼容 MariaDB）。 */
    @Override
    public String upsert(String table, List<String> insertColumns, List<String> conflictColumns, List<String> updateColumns) {
        StringBuilder sb = new StringBuilder("INSERT INTO ").append(quote(table))
                .append(" (").append(DialectSql.quotedJoin(insertColumns, this)).append(") VALUES (")
                .append(DialectSql.placeholders(insertColumns.size())).append(")");
        sb.append(" ON DUPLICATE KEY UPDATE ");
        if (updateColumns.isEmpty()) {
            String pk = conflictColumns.get(0);
            sb.append(quote(pk)).append(" = ").append(quote(pk));
        } else {
            for (int i = 0; i < updateColumns.size(); i++) {
                if (i > 0) sb.append(", ");
                String c = updateColumns.get(i);
                sb.append(quote(c)).append(" = VALUES(").append(quote(c)).append(')');
            }
        }
        return sb.toString();
    }
}
