package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.data.dialect.Dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Bridges a Java value and a JDBC column, mirroring MyBatis's
 * {@code org.apache.ibatis.type.TypeHandler}. The handler owns the Java side
 * (serialize/deserialize) while the {@link Dialect} owns the native column
 * type binding, so the same handler works across databases.
 */
public interface TypeHandler {
    void setParameter(PreparedStatement ps, int index, Object value, Dialect dialect) throws SQLException;

    Object getResult(ResultSet rs, int index, Class<?> javaType, Dialect dialect) throws SQLException;
}