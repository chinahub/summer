package cn.jiebaba.summer.data.dialect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class PostgreSqlDialect implements Dialect {
    @Override public String name() { return "postgresql"; }
    @Override public void appendPagination(StringBuilder sql, long offset, long size, List<Object> params) {
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
    }
    @Override public String quote(String identifier) { return "\"" + identifier + "\""; }
    @Override public String jsonColumnType() { return "jsonb"; }
    @Override public void setJsonParameter(PreparedStatement ps, int index, String json) throws SQLException {
        ps.setObject(index, pgObject("jsonb", json));
    }
    private static Object pgObject(String type, String value) throws SQLException {
        Constructor<?> ctor = PgObjectHolder.ctor;
        Method setType = PgObjectHolder.setType;
        Method setValue = PgObjectHolder.setValue;
        if (ctor == null) {
            throw new SQLException("PGobject unavailable; ensure the PostgreSQL JDBC driver is on the runtime classpath");
        }
        try {
            Object obj = ctor.newInstance();
            setType.invoke(obj, type);
            setValue.invoke(obj, value);
            return obj;
        } catch (Exception e) {
            throw new SQLException("Failed to build PGobject(" + type + ")", e);
        }
    }
    private static final class PgObjectHolder {
        static final Constructor<?> ctor;
        static final Method setType;
        static final Method setValue;
        static {
            Constructor<?> c = null;
            Method t = null;
            Method v = null;
            try {
                Class<?> clazz = Class.forName("org.postgresql.util.PGobject");
                c = clazz.getDeclaredConstructor();
                t = clazz.getMethod("setType", String.class);
                v = clazz.getMethod("setValue", String.class);
            } catch (Exception ignored) {
            }
            ctor = c;
            setType = t;
            setValue = v;
        }
    }
}