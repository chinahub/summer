package cn.jiebaba.summer.admin.testsupport;

import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.mapper.BaseMapper;
import cn.jiebaba.summer.data.page.IPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 测试用内存 Mapper 基类：以 List 存储实体，提供 insert/selectById/selectList
 * 等少量测试需要的语义。Wrapper 查询在内存中无法求值，统一不支持；
 * 逻辑删除表的子类应自行重写 selectList/selectById 过滤已删行，
 * 并在 deleteById 中改写删除标志，以镜像真实 Mapper 的行为。
 *
 * @param <T> 实体类型
 */
public abstract class InMemoryBaseMapper<T> implements BaseMapper<T> {

    /** 实体存储，按引用共享，调用方就地修改即等价于 updateById。 */
    protected final List<T> rows = new ArrayList<>();

    /** 提取实体主键，用于 selectById/deleteById；无主键实体返回 null。 */
    protected abstract Long idOf(T entity);

    @Override
    public int insert(T entity) {
        rows.add(entity);
        return 1;
    }

    @Override
    public int insertBatch(List<T> entities) {
        rows.addAll(entities);
        return entities.size();
    }

    @Override
    public int upsert(T entity) {
        rows.add(entity);
        return 1;
    }

    @Override
    public int deleteById(Object id) {
        boolean removed = rows.removeIf(e -> Objects.equals(idOf(e), id));
        return removed ? 1 : 0;
    }

    @Override
    public int updateById(T entity) {
        return 1;
    }

    @Override
    public int update(T entity, AbstractWrapper<T, ?> wrapper) {
        throw new UnsupportedOperationException("内存 Mapper 不支持 Wrapper 更新");
    }

    @Override
    public T selectById(Object id) {
        return rows.stream().filter(e -> Objects.equals(idOf(e), id)).findFirst().orElse(null);
    }

    @Override
    public List<T> selectList() {
        return List.copyOf(rows);
    }

    @Override
    public List<T> selectList(AbstractWrapper<T, ?> wrapper) {
        throw new UnsupportedOperationException("内存 Mapper 不支持 Wrapper 查询");
    }

    @Override
    public T selectOne(AbstractWrapper<T, ?> wrapper) {
        throw new UnsupportedOperationException("内存 Mapper 不支持 Wrapper 查询");
    }

    @Override
    public long selectCount(AbstractWrapper<T, ?> wrapper) {
        throw new UnsupportedOperationException("内存 Mapper 不支持 Wrapper 查询");
    }

    @Override
    public IPage<T> selectPage(IPage<T> page, AbstractWrapper<T, ?> wrapper) {
        throw new UnsupportedOperationException("内存 Mapper 不支持 Wrapper 查询");
    }
}
