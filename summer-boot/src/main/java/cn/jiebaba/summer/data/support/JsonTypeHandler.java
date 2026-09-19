package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.core.util.JsonUtil;
import cn.jiebaba.summer.data.dialect.Dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * 将 Java 对象序列化为 JSON 文本（经 {@link JsonUtil}），并将原生列绑定委托给
 * 当前 {@link Dialect}，使同一字段在 PostgreSQL 上映射为 {@code jsonb}、
 * MySQL 为 {@code json}、Oracle 为 {@code CLOB} 等。
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
