package cn.jiebaba.summer.data.migration;

import cn.jiebaba.summer.data.support.SqlExecutor;

/**
 * 版本化数据库迁移：一次结构/数据变更（建表、加列、建索引、数据订正）。
 * 以 Bean 形式声明后由 {@link SchemaMigrator} 在启动期（容器 refresh 阶段，
 * 先于 Web 服务与 ApplicationRunner）按 {@link #version()} 升序执行未应用者，
 * 成功后记入迁移历史表（默认 {@code summer_schema_history}），失败则中止启动。
 * <pre>{@code
 * @Component
 * public class V2EmployeeInstance implements DatabaseMigration {
 *     public int version() { return 2; }
 *     public String description() { return "员工实例表"; }
 *     public void migrate(SqlExecutor executor) {
 *         executor.update(new SqlBuilder.Sql("CREATE TABLE ...", List.of()));
 *     }
 * }
 * }</pre>
 */
public interface DatabaseMigration {

    /**
     * 版本号（正整数，全局唯一）。执行顺序按版本号升序，与声明顺序无关；
     * 重复版本号会被拒绝执行。
     */
    int version();

    /** 变更说明（记录到迁移历史表，便于追溯）。 */
    String description();

    /**
     * 执行迁移。抛出异常则整体回滚（若在事务内）并中止启动。
     * 注意：多数数据库 DDL 隐式提交，事务保证以数据库实际语义为准。
     *
     * @param executor 数据库执行器（与应用共用同一数据源）
     * @throws Exception 迁移失败
     */
    void migrate(SqlExecutor executor) throws Exception;
}
