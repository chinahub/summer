package cn.jiebaba.summer.data.mapper;

import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.page.IPage;

import java.util.List;

/**
 * MyBatis-Plus-style generic CRUD mapper. Users declare their own interface:
 * <pre>{@code
 * public interface UserMapper extends BaseMapper<User> {}
 * }</pre>
 * The framework supplies an implementation via a JDK dynamic proxy.
 */
public interface BaseMapper<T> {

    int insert(T entity);

    int deleteById(Object id);

    int updateById(T entity);

    T selectById(Object id);

    List<T> selectList();

    List<T> selectList(AbstractWrapper<T, ?> wrapper);

    T selectOne(AbstractWrapper<T, ?> wrapper);

    long selectCount(AbstractWrapper<T, ?> wrapper);

    IPage<T> selectPage(IPage<T> page, AbstractWrapper<T, ?> wrapper);
}
