package cn.jiebaba.summer.data.support;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 行映射器：将 {@link ResultSet} 的每一行映射为目标对象，借鉴 Spring 的 {@code RowMapper}。
 * 供 {@link SqlExecutor#query(SqlBuilder.Sql, RowMapper)} 使用，适用于结果集无法直接对应实体
 * （如含计算列、聚合列、跨表投影或数据库特有算子）的自定义查询场景。
 */
@FunctionalInterface
public interface RowMapper<T> {

    /** 将当前行映射为目标对象；rowNum 从 0 开始计数。 */
    T mapRow(ResultSet rs, int rowNum) throws SQLException;
}
