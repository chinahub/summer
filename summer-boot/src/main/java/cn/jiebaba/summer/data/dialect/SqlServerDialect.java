package cn.jiebaba.summer.data.dialect;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class SqlServerDialect implements Dialect {
    @Override public String name() { return "sqlserver"; }
    @Override public void appendPagination(StringBuilder sql, long offset, long size, List<Object> params) {
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(size);
    }
    @Override public String escapeQuote(String identifier) { return "[" + identifier + "]"; }
    @Override public String jsonColumnType() { return "nvarchar(max)"; }
    @Override public void setJsonParameter(PreparedStatement ps, int index, String json) throws SQLException {
        ps.setString(index, json);
    }

    /**
     * SQL Server upsert：{@code MERGE ... WITH (HOLDLOCK) USING (VALUES ...)}，
     * 语句以分号收尾（MERGE 要求）；无更新列时省略 WHEN MATCHED。
     */
    @Override
    public String upsert(String table, List<String> insertColumns, List<String> conflictColumns, List<String> updateColumns) {
        String qt = quote(table);
        StringBuilder sb = new StringBuilder("MERGE ").append(qt)
                .append(" WITH (HOLDLOCK) AS tgt USING (VALUES (")
                .append(DialectSql.placeholders(insertColumns.size()))
                .append(")) AS src (").append(DialectSql.quotedJoin(insertColumns, this)).append(") ON ");
        for (int i = 0; i < conflictColumns.size(); i++) {
            if (i > 0) sb.append(" AND ");
            String c = conflictColumns.get(i);
            sb.append("tgt.").append(quote(c)).append(" = src.").append(quote(c));
        }
        if (!updateColumns.isEmpty()) {
            sb.append(" WHEN MATCHED THEN UPDATE SET ");
            for (int i = 0; i < updateColumns.size(); i++) {
                if (i > 0) sb.append(", ");
                String c = updateColumns.get(i);
                sb.append("tgt.").append(quote(c)).append(" = src.").append(quote(c));
            }
        }
        sb.append(" WHEN NOT MATCHED THEN INSERT (").append(DialectSql.quotedJoin(insertColumns, this)).append(") VALUES (");
        for (int i = 0; i < insertColumns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("src.").append(quote(insertColumns.get(i)));
        }
        sb.append(");");
        return sb.toString();
    }
}
