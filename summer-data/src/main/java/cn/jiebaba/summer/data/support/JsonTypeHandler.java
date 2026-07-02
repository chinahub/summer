package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.core.util.JsonUtil;
import cn.jiebaba.summer.data.dialect.Dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Serializes a Java object to JSON text (via {@link JsonUtil}) and delegates
 * the native column binding to the active {@link Dialect}, so the same field
 * maps to {@code jsonb} on PostgreSQL, {@code json} on MySQL, {@code CLOB} on
 * Oracle, etc.
 */
public final class JsonTypeHandler implements TypeHandler {

    @Override
    public void setParameter(PreparedStatement ps, int index, Object value, Dialect dialect) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
            return;
        }
        String json = value instanceof CharSequence s ? s.toString() : JsonUtil.toJsonStr(value);
        dialect.setJsonParameter(ps, index, json);
    }

    @Override
    public Object getResult(ResultSet rs, int index, Class<?> javaType, Dialect dialect) throws SQLException {
        String json = dialect.getJsonResult(rs, index);
        if (json == null) return null;
        if (javaType == String.class || javaType == CharSequence.class) return json;
        return toBean(json, javaType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object toBean(String json, Class<?> javaType) {
        return JsonUtil.toBean(json, (Class) javaType);
    }
}