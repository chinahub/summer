package cn.jiebaba.summer.test.data;

import cn.jiebaba.summer.data.annotation.TableId;
import cn.jiebaba.summer.data.annotation.TableName;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.dialect.H2Dialect;
import cn.jiebaba.summer.data.mapper.MapperSupport;
import cn.jiebaba.summer.data.metadata.MetadataParser;
import cn.jiebaba.summer.data.support.SqlExecutor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

/**
 * H2Dialect 真库集成测试。此前 h2 驱动映射到 MySqlDialect，其 upsert
 * （{@code ON DUPLICATE KEY UPDATE ... VALUES(...)}）与反引号转义仅在 MySQL 兼容模式
 * （{@code ;MODE=MySQL}）下可用，默认 Regular 模式直接语法报错；
 * 本测试在无任何兼容模式的 Regular 内存库上真实执行 upsert 往返验证修复效果。
 */
public class H2DialectIntegrationTest {

    /** key 列命中保留字超集：顺带验证 Regular 模式下双引号转义（反引号不可用）。 */
    @TableName("h2_task")
    public static class H2Task {
        @TableId
        private Long id;
        private String module;
        private Integer progress;
        private String key;

        public H2Task() {}
        public H2Task(Long id, String module, Integer progress, String key) {
            this.id = id;
            this.module = module;
            this.progress = progress;
            this.key = key;
        }
    }

    private static final String DDL = "DROP TABLE IF EXISTS h2_task;"
            + "CREATE TABLE h2_task ("
            + "id BIGINT PRIMARY KEY, module VARCHAR(100), progress INT, \"key\" VARCHAR(100))";

    /** 每个用例独立命名内存库；不追加 MODE 参数即为 Regular 模式。 */
    private static MapperSupport<H2Task> support(String dbName, String extra) {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1" + extra;
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(url);
        Dialect dialect = Dialect.detect("org.h2.Driver", url);
        Assertions.assertTrue(dialect instanceof H2Dialect,
                "h2 驱动应映射到 H2Dialect，实际 " + dialect.name());
        SqlExecutor executor = new SqlExecutor(ds, dialect);
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(DDL);
        } catch (Exception e) {
            throw new IllegalStateException("建表失败", e);
        }
        return new MapperSupport<>(MetadataParser.parse(H2Task.class), executor);
    }

    private static void assertRow(H2Task t, Long id, String module, Integer progress, String key) {
        Assertions.assertEquals(id, t.id);
        Assertions.assertEquals(module, t.module);
        Assertions.assertEquals(progress, t.progress);
        Assertions.assertEquals(key, t.key);
    }

    @Test
    @DisplayName("Regular 模式：upsert 插入与更新往返")
    void upsertInsertAndUpdateRoundTrip() {
        MapperSupport<H2Task> ms = support("h2dialect_regular", "");
        Assertions.assertEquals(1, ms.upsert(new H2Task(1L, "api", 10, "k1")));
        assertRow(ms.selectById(1L), 1L, "api", 10, "k1");

        Assertions.assertEquals(1, ms.upsert(new H2Task(1L, "web", 20, "k2")));
        assertRow(ms.selectById(1L), 1L, "web", 20, "k2");
    }

    @Test
    @DisplayName("Regular 模式：空值字段既不插入也不更新，保留字列转义正确")
    void upsertSkipsNullsAndQuotesReservedColumn() {
        MapperSupport<H2Task> ms = support("h2dialect_nulls", "");
        ms.upsert(new H2Task(1L, "api", 10, "k1"));
        // module 之外全为 null：upsert 只更新 module，progress/key 保留旧值
        ms.upsert(new H2Task(1L, "ops", null, null));
        assertRow(ms.selectById(1L), 1L, "ops", 10, "k1");
    }

    @Test
    @DisplayName("仅主键非空：等价插入或无操作（DO NOTHING 语义）")
    void upsertOnlyPk() {
        MapperSupport<H2Task> ms = support("h2dialect_pkonly", "");
        ms.upsert(new H2Task(2L, null, null, null));
        H2Task row = ms.selectById(2L);
        Assertions.assertNotNull(row, "不存在时应插入");
        assertRow(row, 2L, null, null, null);
        ms.upsert(new H2Task(2L, null, null, null));
        assertRow(ms.selectById(2L), 2L, null, null, null);
    }

    @Test
    @DisplayName("MySQL 兼容模式：H2Dialect 的 MERGE KEY 同样可用（对既有用户无回归）")
    void mergeKeyAlsoWorksInMySqlMode() {
        MapperSupport<H2Task> ms = support("h2dialect_mysqlmode", ";MODE=MySQL");
        ms.upsert(new H2Task(1L, "api", 10, "k1"));
        assertRow(ms.selectById(1L), 1L, "api", 10, "k1");
        ms.upsert(new H2Task(1L, "web", 20, "k2"));
        assertRow(ms.selectById(1L), 1L, "web", 20, "k2");
    }
}
