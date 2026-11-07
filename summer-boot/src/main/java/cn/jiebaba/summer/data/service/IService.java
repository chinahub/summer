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

    /**
     * 条件更新：SET 由 entity 非空字段（entity 非 null 时）与 wrapper.set 片段组成，
     * 后者允许显式将列更新为 NULL；SET 或 WHERE 为空抛出 IllegalArgumentException。
     */
    default boolean update(T entity, AbstractWrapper<T, ?> wrapper) { return baseMapper().update(entity, wrapper) > 0; }

    /** 仅按 wrapper.set 片段更新的便捷形态（entity 为 null）。 */
    default boolean update(AbstractWrapper<T, ?> wrapper) { return update(null, wrapper); }

    default boolean removeById(Object id) { return baseMapper().deleteById(id) > 0; }

    default T getById(Object id) { return baseMapper().selectById(id); }

    default List<T> list() { return baseMapper().selectList(); }

    default List<T> list(AbstractWrapper<T, ?> wrapper) { return baseMapper().selectList(wrapper); }

    default T getOne(AbstractWrapper<T, ?> wrapper) { return baseMapper().selectOne(wrapper); }

    /** 统计全部记录数（无查询条件）。 */
    default long count() { return count(null); }

    default long count(AbstractWrapper<T, ?> wrapper) { return baseMapper().selectCount(wrapper); }

    default IPage<T> page(IPage<T> page, AbstractWrapper<T, ?> wrapper) {
        return baseMapper().selectPage(page, wrapper);
    }
}
