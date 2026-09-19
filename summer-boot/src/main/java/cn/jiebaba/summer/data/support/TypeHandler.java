package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.data.dialect.Dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 桥接 Java 值与 JDBC 列，借鉴 MyBatis 的
 * {@code org.apache.ibatis.type.TypeHandler}。处理器负责 Java 侧
 * （序列化/反序列化），{@link Dialect} 负责原生列类型绑定，
 * 使同一处理器可跨数据库工作。
 */
public interface TypeHandler {
    void setParameter(PreparedStatement ps, int index, Object value, Dialect dialect) throws SQLException;

    Object getResult(ResultSet rs, int index, Class<?> javaType, Dialect dialect) throws SQLException;
}
