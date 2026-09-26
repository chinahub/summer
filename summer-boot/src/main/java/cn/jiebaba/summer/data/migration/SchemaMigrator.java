package cn.jiebaba.summer.data.migration;

import cn.jiebaba.summer.data.support.DataAccessException;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.data.support.SqlExecutor;
import cn.jiebaba.summer.data.transaction.TransactionManager;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 版本化 schema 迁移执行器：维护迁移历史表（默认 {@code summer_schema_history}），
 * 按版本号升序执行未应用的 {@link DatabaseMigration}，每个迁移独立事务
 * （注入了 {@link TransactionManager} 时），成功后记录版本与说明。
 * <p>可移植性：历史表以 INTEGER/VARCHAR/TIMESTAMP 等跨库基础类型建表；
 * 建表采用"先探测后建表"而非 {@code CREATE TABLE IF NOT EXISTS}（兼容 Oracle 旧版本）。
 * 注意多数数据库 DDL 隐式提交，单迁移的原子性以数据库实际语义为准。
 */
public final class SchemaMigrator {

    /** 默认迁移历史表名。 */
    public static final String DEFAULT_HISTORY_TABLE = "summer_schema_history";

    private static final Logger LOG = Logger.getLogger(SchemaMigrator.class.getName());

    private final SqlExecutor executor;
    private final TransactionManager transactionManager;
    private final String historyTable;

    public SchemaMigrator(SqlExecutor executor) {
        this(executor, null, DEFAULT_HISTORY_TABLE);
    }

    /**
     * @param executor           数据库执行器
     * @param transactionManager 事务管理器（可为 null：迁移不包事务）
     * @param historyTable       迁移历史表名
     */
    public SchemaMigrator(SqlExecutor executor, TransactionManager transactionManager, String historyTable) {
        if (executor == null) throw new IllegalArgumentException("executor 不能为空");
        if (historyTable == null || historyTable.isBlank()) {
            throw new IllegalArgumentException("historyTable 不能为空");
        }
        this.executor = executor;
        this.transactionManager = transactionManager;
        this.historyTable = historyTable;
    }

    /**
     * 执行全部未应用的迁移（按 version 升序）；返回本次应用的迁移数。
     * 版本号非正整数或重复时拒绝执行；已有版本跳过（幂等，可安全重复启动）。
     *
     * @param migrations 迁移集合（通常来自容器的 DatabaseMigration Bean）
     * @throws DataAccessException 迁移失败（含失败的版本号与说明）
     */
    public int migrate(Collection<DatabaseMigration> migrations) {
        if (migrations == null || migrations.isEmpty()) return 0;
        List<DatabaseMigration> sorted = new ArrayList<>(migrations);
        sorted.sort(Comparator.comparingInt(DatabaseMigration::version));
        Set<Integer> seen = new HashSet<>();
        for (DatabaseMigration m : sorted) {
            if (m.version() <= 0) {
                throw new IllegalArgumentException("迁移版本号必须为正整数: " + m.version()
                        + " (" + m.description() + ")");
            }
            if (!seen.add(m.version())) {
                throw new IllegalArgumentException("迁移版本号重复: " + m.version()
                        + " (" + m.description() + ")");
            }
        }
        ensureHistoryTable();
        Set<Integer> applied = appliedVersions();
        int count = 0;
        for (DatabaseMigration m : sorted) {
            if (applied.contains(m.version())) continue;
            apply(m);
            count++;
        }
        return count;
    }

    /** 已应用的版本号集合。 */
    public Set<Integer> appliedVersions() {
        SqlBuilder.Sql sql = new SqlBuilder.Sql(
                "SELECT " + quote("version") + " FROM " + quote(historyTable), List.of());
        Set<Integer> applied = new HashSet<>();
        for (Integer v : executor.query(sql, (rs, i) -> rs.getInt(1))) {
            applied.add(v);
        }
        return applied;
    }

    /** 执行单个迁移并在历史表记账（同一事务内，若启用事务）。 */
    private void apply(DatabaseMigration m) {
        boolean began = transactionManager != null && transactionManager.begin();
        try {
            m.migrate(executor);
            executor.update(new SqlBuilder.Sql(
                    "INSERT INTO " + quote(historyTable) + " ("
                            + quote("version") + ", " + quote("description") + ", " + quote("applied_at")
                            + ") VALUES (?, ?, ?)",
                    List.of(m.version(), m.description(), new Timestamp(System.currentTimeMillis()))));
            if (began) transactionManager.commit();
            LOG.info("已应用数据库迁移 V" + m.version() + ": " + m.description());
        } catch (Exception e) {
            if (began) transactionManager.rollback();
            throw new DataAccessException(
                    "数据库迁移 V" + m.version() + " (" + m.description() + ") 失败", e);
        } finally {
            if (began) transactionManager.end(true);
        }
    }

    /** 历史表不存在则创建（先探测后建表，兼容不支持 IF NOT EXISTS 的数据库）。 */
    private void ensureHistoryTable() {
        try {
            executor.query(new SqlBuilder.Sql(
                    "SELECT COUNT(*) FROM " + quote(historyTable), List.of()), (rs, i) -> rs.getLong(1));
        } catch (RuntimeException missing) {
            executor.update(new SqlBuilder.Sql(
                    "CREATE TABLE " + quote(historyTable) + " ("
                            + quote("version") + " INTEGER PRIMARY KEY, "
                            + quote("description") + " VARCHAR(500), "
                            + quote("applied_at") + " TIMESTAMP)",
                    List.of()));
        }
    }

    private String quote(String identifier) {
        return executor.dialect().quote(identifier);
    }
}
