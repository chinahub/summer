package cn.jiebaba.summer.data.mapper;

import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.page.IPage;
import cn.jiebaba.summer.data.support.IdGenerator;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.support.SqlExecutor;

import java.util.List;

/**
 * 支撑每个 {@link BaseMapper} 代理的具体实现。持有实体 {@link TableInfo}、
 * 一个 {@link SqlBuilder} 与共享的 {@link SqlExecutor}。
 */
public class MapperSupport<T> {

    private final TableInfo table;
    private final SqlBuilder sqlBuilder;
    private final SqlExecutor executor;

    public MapperSupport(TableInfo table, SqlExecutor executor) {
        this(table, executor, executor.dialect());
    }

    public MapperSupport(TableInfo table, SqlExecutor executor, Dialect dialect) {
        this.table = table;
        this.executor = executor;
        this.sqlBuilder = new SqlBuilder(table, dialect);
    }

    @SuppressWarnings("unchecked")
    public int insert(T entity) {
        fillIdIfNeeded(entity);
        SqlBuilder.Sql sql = sqlBuilder.insert(entity);
        SqlExecutor.UpdateResult result = executor.updateWithGeneratedKey(sql, table);
        if (result.generatedKey() != null && table.idField() != null) {
            table.idField().setValue(entity, result.generatedKey());
        }
        return result.affectedRows();
    }

    public int deleteById(Object id) {
        return executor.update(sqlBuilder.deleteById(id));
    }

    public int updateById(T entity) {
        return executor.update(sqlBuilder.updateById(entity));
    }

    public T selectById(Object id) {
        List<T> rows = executor.query(sqlBuilder.selectById(id), table);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<T> selectList() {
        return executor.query(sqlBuilder.selectList(null), table);
    }

    public List<T> selectList(AbstractWrapper<T, ?> wrapper) {
        return executor.query(sqlBuilder.selectList(wrapper), table);
    }

    public T selectOne(AbstractWrapper<T, ?> wrapper) {
        List<T> rows = executor.query(sqlBuilder.selectList(wrapper), table);
        if (rows.isEmpty()) return null;
        if (rows.size() > 1) throw new IllegalStateException("selectOne returned " + rows.size() + " rows");
        return rows.get(0);
    }

    public long selectCount(AbstractWrapper<T, ?> wrapper) {
        return executor.count(sqlBuilder.selectCount(wrapper));
    }

    public IPage<T> selectPage(IPage<T> page, AbstractWrapper<T, ?> wrapper) {
        long total = executor.count(sqlBuilder.selectCount(wrapper));
        page.setTotal(total);
        List<T> records = executor.query(sqlBuilder.selectList(wrapper, page), table);
        page.setRecords(records);
        return page;
    }

    @SuppressWarnings("unchecked")
    private void fillIdIfNeeded(T entity) {
        if (table.idField() == null) return;
        Object current = table.idField().getValue(entity);
        if (current != null) return;
        Object id = IdGenerator.generate(table.idType());
        if (id != null) {
            table.idField().setValue(entity, id);
        }
    }

    public TableInfo table() { return table; }
    public SqlBuilder sqlBuilder() { return sqlBuilder; }
    public SqlExecutor executor() { return executor; }
}
