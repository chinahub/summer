package cn.jiebaba.summer.data.dialect;

import java.util.List;

public final class PostgreSqlDialect implements Dialect {
    @Override public String name() { return "postgresql"; }
    @Override public void appendPagination(StringBuilder sql, long offset, long size, List<Object> params) {
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
    }
    @Override public String quote(String identifier) { return "\"" + identifier + "\""; }
}
