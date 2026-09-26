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

    T selectById(Object id);

    List<T> selectList();

    List<T> selectList(AbstractWrapper<T, ?> wrapper);

    T selectOne(AbstractWrapper<T, ?> wrapper);

    long selectCount(AbstractWrapper<T, ?> wrapper);

    IPage<T> selectPage(IPage<T> page, AbstractWrapper<T, ?> wrapper);
}
