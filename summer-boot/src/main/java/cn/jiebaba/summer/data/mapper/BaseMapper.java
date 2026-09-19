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

    int deleteById(Object id);

    int updateById(T entity);

    T selectById(Object id);

    List<T> selectList();

    List<T> selectList(AbstractWrapper<T, ?> wrapper);

    T selectOne(AbstractWrapper<T, ?> wrapper);

    long selectCount(AbstractWrapper<T, ?> wrapper);

    IPage<T> selectPage(IPage<T> page, AbstractWrapper<T, ?> wrapper);
}
