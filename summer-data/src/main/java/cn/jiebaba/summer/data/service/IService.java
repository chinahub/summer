package cn.jiebaba.summer.data.service;

import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.mapper.BaseMapper;
import cn.jiebaba.summer.data.page.IPage;

import java.util.Collection;
import java.util.List;

public interface IService<T> {

    BaseMapper<T> baseMapper();

    default boolean save(T entity) { return baseMapper().insert(entity) > 0; }

    default boolean saveBatch(Collection<T> entities) {
        boolean ok = true;
        for (T e : entities) ok = ok && baseMapper().insert(e) > 0;
        return ok;
    }

    default boolean updateById(T entity) { return baseMapper().updateById(entity) > 0; }

    default boolean removeById(Object id) { return baseMapper().deleteById(id) > 0; }

    default T getById(Object id) { return baseMapper().selectById(id); }

    default List<T> list() { return baseMapper().selectList(); }

    default List<T> list(AbstractWrapper<T, ?> wrapper) { return baseMapper().selectList(wrapper); }

    default T getOne(AbstractWrapper<T, ?> wrapper) { return baseMapper().selectOne(wrapper); }

    default long count(AbstractWrapper<T, ?> wrapper) { return baseMapper().selectCount(wrapper); }

    default IPage<T> page(IPage<T> page, AbstractWrapper<T, ?> wrapper) {
        return baseMapper().selectPage(page, wrapper);
    }
}
