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
    @Override public String jsonColumnType() { return "nvarchar(max)"; }
    @Override public void setJsonParameter(PreparedStatement ps, int index, String json) throws SQLException {
        ps.setString(index, json);
    }
}