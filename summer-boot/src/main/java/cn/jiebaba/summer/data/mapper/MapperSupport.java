package cn.jiebaba.summer.data.mapper;

import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.metadata.TableFieldInfo;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.page.IPage;
import cn.jiebaba.summer.data.support.IdGenerator;
import cn.jiebaba.summer.data.support.OptimisticLockException;
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
        fillVersionIfNeeded(entity);
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

    /**
     * 批量插入：AUTO 自增主键降级逐条 {@link #insert}（单语句多行无法跨方言回填自增 id），
     * 其余走单语句多行 VALUES。返回总受影响行数。
     */
    public int insertBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        if (table.idField() != null && table.idType() == IdType.AUTO) {
            int total = 0;
            for (T entity : entities) total += insert(entity);
            return total;
        }
        for (T entity : entities) {
            fillIdIfNeeded(entity);
            fillVersionIfNeeded(entity);
        }
        return executor.update(sqlBuilder.insertBatch(entities));
    }

    /**
     * upsert：存在则更新、不存在则插入（按主键冲突判定，语句由方言生成）。
     * 需要显式主键（AUTO 自增不支持——回填 id 与冲突判定无法兼得）。
     */
    public int upsert(T entity) {
        fillIdIfNeeded(entity);
        if (table.idField() != null && table.idField().getValue(entity) == null) {
            throw new IllegalStateException(
                    "upsert 需要显式 id（不支持 AUTO 自增回填）: " + table.entityType().getName());
        }
        return executor.update(sqlBuilder.upsert(entity));
    }

    /**
     * 按 id 更新：实体带 {@code @Version} 字段时走乐观锁（WHERE version = ? 且成功后版本号自增），
     * 受影响行数为 0 时抛出 {@link cn.jiebaba.summer.data.support.OptimisticLockException}。
     */
    public int updateById(T entity) {
        TableFieldInfo versionField = table.versionField();
        if (versionField == null) {
            return executor.update(sqlBuilder.updateById(entity));
        }
        Object current = versionField.getValue(entity);
        int rows = executor.update(sqlBuilder.updateById(entity));
        if (rows == 0) {
            throw new OptimisticLockException("乐观锁更新失败（版本冲突或记录不存在）: "
                    + table.entityType().getSimpleName()
                    + " id=" + table.idField().getValue(entity) + " version=" + current);
        }
        if (current instanceof Integer i) {
            versionField.setValue(entity, i + 1);
        } else {
            versionField.setValue(entity, ((Number) current).longValue() + 1L);
        }
        return rows;
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

    /** {@code @Version} 字段为空时初始化版本号为 1（int/Integer 型）或 1L（long/Long 型）。 */
    @SuppressWarnings("unchecked")
    private void fillVersionIfNeeded(T entity) {
        TableFieldInfo versionField = table.versionField();
        if (versionField == null || versionField.getValue(entity) != null) return;
        boolean wide = versionField.javaType() == long.class || versionField.javaType() == Long.class;
        versionField.setValue(entity, wide ? 1L : 1);
    }

    public TableInfo table() { return table; }
    public SqlBuilder sqlBuilder() { return sqlBuilder; }
    public SqlExecutor executor() { return executor; }
}
