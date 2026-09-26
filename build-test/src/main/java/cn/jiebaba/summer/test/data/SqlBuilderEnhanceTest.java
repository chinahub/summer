package cn.jiebaba.summer.test.data;

import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.annotation.TableId;
import cn.jiebaba.summer.data.annotation.TableName;
import cn.jiebaba.summer.data.annotation.Version;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.dialect.H2Dialect;
import cn.jiebaba.summer.data.dialect.MySqlDialect;
import cn.jiebaba.summer.data.dialect.OracleDialect;
import cn.jiebaba.summer.data.dialect.PostgreSqlDialect;
import cn.jiebaba.summer.data.dialect.SqlServerDialect;
import cn.jiebaba.summer.data.dialect.SqliteDialect;
import cn.jiebaba.summer.data.metadata.MetadataParser;
import cn.jiebaba.summer.data.support.SqlBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * {@link SqlBuilder} 增强能力单测：insertBatch 多行 VALUES、upsert 各方言子句、
 * @Version 乐观锁 update SQL 与元数据校验。
 */
public class SqlBuilderEnhanceTest {

    @TableName("dev_task")
    public static class DevTask {
        @TableId
        private Long id;
        private String module;
        private Integer progress;
        @Version
        private Integer version;

        public DevTask(Long id, String module, Integer progress, Integer version) {
            this.id = id;
            this.module = module;
            this.progress = progress;
            this.version = version;
        }
    }

    @TableName("plain_task")
    public static class PlainTask {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String module;

        public PlainTask(Long id, String module) {
            this.id = id;
            this.module = module;
        }
    }

    public static class BadVersionType {
        @TableId
        private Long id;
        @Version
        private String version;
    }

    private static SqlBuilder builder(Class<?> entity, Dialect dialect) {
        return new SqlBuilder(MetadataParser.parse(entity), dialect);
    }

    @Test
    @DisplayName("updateById 带 @Version：SET version = version + 1 且 WHERE version = ?")
    void updateByIdOptimisticLockSql() {
        SqlBuilder sb = builder(DevTask.class, new PostgreSqlDialect());
        SqlBuilder.Sql sql = sb.updateById(new DevTask(7L, "api", 50, 3));
        Assertions.assertEquals(
                "UPDATE dev_task SET module = ?, progress = ?, version = version + 1"
                        + " WHERE id = ? AND version = ?",
                sql.sql());
        Assertions.assertEquals(Arrays.asList("api", 50, 7L, 3), sql.params());
    }

    @Test
    @DisplayName("版本号为空的更新被拒绝（须先查询再更新）")
    void updateByIdRequiresVersionValue() {
        SqlBuilder sb = builder(DevTask.class, new PostgreSqlDialect());
        Assertions.assertThrows(IllegalStateException.class,
                () -> sb.updateById(new DevTask(7L, "api", 50, null)));
    }

    @Test
    @DisplayName("无 @Version 实体维持普通 update SQL")
    void updateByIdWithoutVersion() {
        SqlBuilder sb = builder(PlainTask.class, new PostgreSqlDialect());
        SqlBuilder.Sql sql = sb.updateById(new PlainTask(7L, "api"));
        Assertions.assertEquals("UPDATE plain_task SET module = ? WHERE id = ?", sql.sql());
    }

    @Test
    @DisplayName("insertBatch：单语句多行 VALUES，列取非空并集")
    void insertBatchMultiRow() {
        SqlBuilder sb = builder(DevTask.class, new PostgreSqlDialect());
        SqlBuilder.Sql sql = sb.insertBatch(List.of(
                new DevTask(1L, "api", 10, 1),
                new DevTask(2L, "web", null, 1)));
        Assertions.assertEquals(
                "INSERT INTO dev_task (id, module, progress, version) VALUES (?, ?, ?, ?), (?, ?, ?, ?)",
                sql.sql());
        Assertions.assertEquals(Arrays.asList(1L, "api", 10, 1, 2L, "web", null, 1), sql.params());
    }

    @Test
    @DisplayName("upsert：MySQL 系 ON DUPLICATE KEY UPDATE")
    void upsertMySql() {
        SqlBuilder.Sql sql = builder(DevTask.class, new MySqlDialect())
                .upsert(new DevTask(1L, "api", 10, 1));
        Assertions.assertTrue(sql.sql().contains("ON DUPLICATE KEY UPDATE"), sql.sql());
        Assertions.assertTrue(sql.sql().contains("module = VALUES(module)"), sql.sql());
    }

    @Test
    @DisplayName("upsert：PostgreSQL/SQLite 用 ON CONFLICT DO UPDATE")
    void upsertOnConflict() {
        for (Dialect dialect : List.of(new PostgreSqlDialect(), new SqliteDialect())) {
            SqlBuilder.Sql sql = builder(DevTask.class, dialect).upsert(new DevTask(1L, "api", 10, 1));
            Assertions.assertTrue(sql.sql().contains("ON CONFLICT (id) DO UPDATE SET"), sql.sql());
            Assertions.assertTrue(sql.sql().contains("excluded.module"), sql.sql());
        }
    }

    @Test
    @DisplayName("upsert：Oracle/SQL Server 用 MERGE")
    void upsertMerge() {
        SqlBuilder.Sql oracle = builder(DevTask.class, new OracleDialect())
                .upsert(new DevTask(1L, "api", 10, 1));
        Assertions.assertTrue(oracle.sql().startsWith("MERGE INTO"), oracle.sql());
        Assertions.assertTrue(oracle.sql().contains("FROM dual"), oracle.sql());

        SqlBuilder.Sql mssql = builder(DevTask.class, new SqlServerDialect())
                .upsert(new DevTask(1L, "api", 10, 1));
        Assertions.assertTrue(mssql.sql().startsWith("MERGE"), mssql.sql());
        Assertions.assertTrue(mssql.sql().endsWith(";"), mssql.sql());
    }

    @Test
    @DisplayName("upsert：H2 用 MERGE INTO ... KEY（Regular/MySQL 模式均可用）")
    void upsertH2() {
        SqlBuilder.Sql sql = builder(DevTask.class, new H2Dialect())
                .upsert(new DevTask(1L, "api", 10, 1));
        Assertions.assertEquals(
                "MERGE INTO dev_task (id, module, progress, version) KEY (id) VALUES (?, ?, ?, ?)",
                sql.sql());
        Assertions.assertEquals(Arrays.asList(1L, "api", 10, 1), sql.params());
    }

    @Test
    @DisplayName("upsert 仅主键：冲突时不做更新（DO NOTHING / 空更新子句）")
    void upsertConflictDoNothing() {
        SqlBuilder.Sql pg = builder(DevTask.class, new PostgreSqlDialect())
                .upsert(new DevTask(1L, null, null, null));
        Assertions.assertTrue(pg.sql().contains("DO NOTHING"), pg.sql());

        SqlBuilder.Sql mysql = builder(DevTask.class, new MySqlDialect())
                .upsert(new DevTask(1L, null, null, null));
        Assertions.assertTrue(mysql.sql().contains("ON DUPLICATE KEY UPDATE id = id"), mysql.sql());
    }

    @Test
    @DisplayName("@Version 字段类型非法时解析报错")
    void badVersionTypeRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> MetadataParser.parse(BadVersionType.class));
    }
}
