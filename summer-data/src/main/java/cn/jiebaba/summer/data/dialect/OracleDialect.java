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
    @Override public String jsonColumnType() { return "CLOB"; }
    @Override public void setJsonParameter(PreparedStatement ps, int index, String json) throws SQLException {
        ps.setString(index, json);
    }
}