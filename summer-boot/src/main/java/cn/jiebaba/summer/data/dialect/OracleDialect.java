package cn.jiebaba.summer.data.dialect;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class OracleDialect implements Dialect {
    @Override public String name() { return "oracle"; }
    @Override public void appendPagination(StringBuilder sql, long offset, long size, List<Object> params) {
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(size);
    }
    /** Oracle 双引号转义且统一转大写：未加引号的标识符由数据库隐式转大写存储，加引号后需显式保持一致。 */
    @Override public String escapeQuote(String identifier) { return "\"" + identifier.toUpperCase() + "\""; }
    @Override public String jsonColumnType() { return "CLOB"; }
    @Override public void setJsonParameter(PreparedStatement ps, int index, String json) throws SQLException {
        ps.setString(index, json);
    }

    /** Oracle upsert：{@code MERGE INTO ... USING (SELECT ? ... FROM dual)}（无更新列时省略 WHEN MATCHED）。 */
    @Override
    public String upsert(String table, List<String> insertColumns, List<String> conflictColumns, List<String> updateColumns) {
        StringBuilder src = new StringBuilder("SELECT ");
        for (int i = 0; i < insertColumns.size(); i++) {
            if (i > 0) src.append(", ");
            src.append('?').append(" AS ").append(quote(insertColumns.get(i)));
        }
        src.append(" FROM dual");
        String qt = quote(table);
        StringBuilder sb = new StringBuilder("MERGE INTO ").append(qt)
                .append(" USING (").append(src).append(") src ON (");
        for (int i = 0; i < conflictColumns.size(); i++) {
            if (i > 0) sb.append(" AND ");
            String c = conflictColumns.get(i);
            sb.append(qt).append('.').append(quote(c)).append(" = src.").append(quote(c));
        }
        sb.append(')');
        if (!updateColumns.isEmpty()) {
            sb.append(" WHEN MATCHED THEN UPDATE SET ");
            for (int i = 0; i < updateColumns.size(); i++) {
                if (i > 0) sb.append(", ");
                String c = updateColumns.get(i);
                sb.append(qt).append('.').append(quote(c)).append(" = src.").append(quote(c));
            }
        }
        sb.append(" WHEN NOT MATCHED THEN INSERT (").append(DialectSql.quotedJoin(insertColumns, this)).append(") VALUES (");
        for (int i = 0; i < insertColumns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("src.").append(quote(insertColumns.get(i)));
        }
        sb.append(')');
        return sb.toString();
    }
}
