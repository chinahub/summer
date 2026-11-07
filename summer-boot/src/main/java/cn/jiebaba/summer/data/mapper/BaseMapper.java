package cn.jiebaba.summer.data.mapper;

import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.page.IPage;

import java.util.List;

/**
 * MyBatis-Plus 风格的泛型 CRUD mapper。用户声明自己的接口：
 * <pre>{@code
 * public interface UserMapper extends BaseMapper<User> {}
 * }</pre>
 * 框架通过 JDK 动态代理提供实现。
 */
public interface BaseMapper<T> {

    int insert(T entity);

    /**
     * 批量插入：AUTO 自增主键降级逐条插入，其余单语句多行 VALUES。
     * 空列表返回 0；返回总受影响行数。
     */
    int insertBatch(List<T> entities);

    /**
     * upsert：存在则更新、不存在则插入（按主键冲突判定）。
     * 需要显式主键；空值字段既不插入也不更新。方言不支持时抛出 UnsupportedOperationException。
     */
    int upsert(T entity);

    int deleteById(Object id);

    /**
     * 按 id 更新：实体带 {@code @Version} 字段时走乐观锁
     * （{@code WHERE version = ?}，成功后版本号自增），冲突时抛出
     * {@link cn.jiebaba.summer.data.support.OptimisticLockException}。
     */
    int updateById(T entity);

    /**
     * 条件更新：SET 由 entity 非空字段（entity 非 null 时）与 Wrapper.set 片段组成，
     * 后者允许显式将列更新为 NULL；SET 或 WHERE 为空抛出 IllegalArgumentException。
     * 不参与 @Version 乐观锁（乐观锁仅 {@link #updateById} 支持）。
     */
    int update(T entity, AbstractWrapper<T, ?> wrapper);

    /** 仅按 Wrapper.set 片段更新的便捷形态（entity 为 null）。 */
    default int update(AbstractWrapper<T, ?> wrapper) { return update(null, wrapper); }

    T selectById(Object id);

    List<T> selectList();

    List<T> selectList(AbstractWrapper<T, ?> wrapper);

    T selectOne(AbstractWrapper<T, ?> wrapper);

    long selectCount(AbstractWrapper<T, ?> wrapper);

    IPage<T> selectPage(IPage<T> page, AbstractWrapper<T, ?> wrapper);
}
