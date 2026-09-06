package cn.jiebaba.summer.test;

import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.data.annotation.TableField;
import cn.jiebaba.summer.data.annotation.TableId;
import cn.jiebaba.summer.data.annotation.TableName;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.dialect.OracleDialect;
import cn.jiebaba.summer.data.metadata.MetadataParser;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.support.DataProperties;
import cn.jiebaba.summer.data.support.DataSourceFactory;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.data.support.SqlExecutor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Oracle / SQL Server 真库冒烟测试（main() 手工运行，参数 oracle|sqlserver）：
 * 覆盖按需转义（保留字列 level）、字符串回读（CLOB/NVARCHAR(max)）、CRUD、
 * lambda wrapper、分页方言语法与 keepalive 探活的方言兜底。
 * 运行：mvn -pl build-test test-compile 后执行 main（需真库容器已启动）。
 */
public class DialectDbSmokeTest {

    @TableName("summer_dialect_demo")
    public static class Demo {
        @TableId private Long id;
        private String name;
        @TableField("level") private Integer level;   // 保留字列：Oracle LEVEL / SqlServer [level]
        private String memo;                          // 长文本：Oracle CLOB / SqlServer NVARCHAR(max)
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public String getMemo() { return memo; }
        public void setMemo(String memo) { this.memo = memo; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    /** 屏蔽 classpath 配置文件的可测试 Environment（避免 sample yml 的显式 keepalive-query 干扰兜底验证）。 */
    private static final class EmptyEnv extends Environment {
        EmptyEnv() {
            super(new String[0], java.util.Map.of());
        }

        @Override
        protected String readClasspathText(String location) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        String db = args.length > 0 ? args[0] : "oracle";
        boolean oracle = "oracle".equals(db);
        System.setProperty("summer.datasource.url", oracle
                ? "jdbc:oracle:thin:@localhost:1521/FREEPDB1"
                : "jdbc:sqlserver://localhost:1433;encrypt=false;databaseName=tempdb");
        System.setProperty("summer.datasource.username", oracle ? "appuser" : "sa");
        System.setProperty("summer.datasource.password", oracle ? "App123456" : "Sql2022_PASS");
        System.setProperty("summer.datasource.driver-class-name", oracle
                ? "oracle.jdbc.OracleDriver" : "com.microsoft.sqlserver.jdbc.SQLServerDriver");

        Environment env = new EmptyEnv();
        DataProperties props = DataProperties.from(env);
        DataSource ds = DataSourceFactory.create(props);
        Dialect dialect = Dialect.detect(props.driver(), props.url());
        SqlExecutor executor = new SqlExecutor(ds, dialect);
        TableInfo table = MetadataParser.parse(Demo.class);
        SqlBuilder builder = new SqlBuilder(table, dialect);

        int passed = 0;
        header("config");
        System.out.println("  url=" + props.url());
        System.out.println("  dialect=" + dialect.name()
                + " keepalive=" + props.keepaliveQuery()
                + (oracle ? " (expect SELECT 1 FROM DUAL)" : ""));
        passed += check("keepalive dialect default",
                oracle ? "SELECT 1 FROM DUAL" : "SELECT 1", props.keepaliveQuery());
        // 探活 SQL 真库执行一次（验证方言兜底语法合法）
        try (var conn = ds.getConnection(); var st = conn.createStatement()) {
            st.execute(props.keepaliveQuery());
        }
        passed++;

        header("DDL");
        String drop = oracle ? "DROP TABLE summer_dialect_demo" : "DROP TABLE IF EXISTS summer_dialect_demo";
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            try { s.execute(drop); } catch (Exception ignore) { /* 首次不存在 */ }
            if (oracle) {
                // LEVEL 是 Oracle 保留字（ORA-03050），建表须带引号且大写，与按需转义生成的大小写一致
                s.execute("CREATE TABLE summer_dialect_demo (id NUMBER(19) PRIMARY KEY, "
                        + "name VARCHAR2(255), \"LEVEL\" INT, memo CLOB, created_at TIMESTAMP)");
            } else {
                s.execute("CREATE TABLE summer_dialect_demo (id BIGINT PRIMARY KEY, "
                        + "name NVARCHAR(255), level INT, memo NVARCHAR(MAX), created_at DATETIME2)");
            }
        }
        System.out.println("  table created");

        header("insert（按需转义 SQL）");
        Demo d = new Demo();
        d.setId(1L);
        d.setName("phone");
        d.setLevel(42);
        d.setMemo("long-text-".repeat(200));
        d.setCreatedAt(LocalDateTime.of(2026, 9, 7, 10, 30));
        SqlBuilder.Sql ins = builder.insert(d);
        System.out.println("  SQL: " + ins.sql());
        executor.update(ins);
        passed += check("insert executed", true, true);

        header("selectById（保留字列 + 字符串回读）");
        Demo found = executor.<Demo>query(builder.selectById(1L), table).get(0);
        passed += check("name", "phone", found.getName());
        passed += check("level (reserved word)", 42, found.getLevel());
        passed += check("memo length (CLOB/NVARCHAR read-back)", 2000, found.getMemo().length());
        passed += check("createdAt", d.createdAt, found.getCreatedAt());

        header("update / delete");
        d.setLevel(43);
        d.setMemo("updated");
        executor.update(builder.updateById(d));
        Demo updated = executor.<Demo>query(builder.selectById(1L), table).get(0);
        passed += check("updated level", 43, updated.getLevel());
        passed += check("updated memo", "updated", updated.getMemo());

        header("lambda wrapper + 分页");
        for (long i = 2; i <= 4; i++) {
            Demo extra = new Demo();
            extra.setId(i);
            extra.setName("item" + i);
            extra.setLevel((int) (10 * i));
            extra.setMemo("m" + i);
            extra.setCreatedAt(LocalDateTime.now());
            executor.update(builder.insert(extra));
        }
        LambdaQueryWrapper<Demo> w = new LambdaQueryWrapper<Demo>()
                .ge(Demo::getLevel, 10)
                .orderByDesc(Demo::getLevel);
        SqlBuilder.Sql list = builder.selectList(w);
        System.out.println("  SQL: " + list.sql());
        List<Demo> rows = executor.<Demo>query(list, table);
        passed += check("wrapper rows 4", 4, rows.size());
        passed += check("ordered desc", 43, rows.get(0).getLevel());
        SqlBuilder.Sql paged = builder.selectList(w, new cn.jiebaba.summer.data.page.Page<>(2, 2));
        System.out.println("  SQL: " + paged.sql());
        List<Demo> page2 = executor.<Demo>query(paged, table);
        passed += check("page size", 2, page2.size());
        System.out.println("  page2 levels=" + page2.stream().map(x -> String.valueOf(x.getLevel())).toList());

        header("delete + cleanup");
        executor.update(builder.deleteById(2L));
        passed += check("deleted", 3, executor.<Demo>query(builder.selectList(null), table).size());
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(drop);
        }
        System.out.println("  table dropped");
        System.out.println();
        System.out.println(db.toUpperCase() + " dialect smoke: " + passed + " assertions passed");
    }

    private static int check(String label, Object expected, Object actual) {
        boolean ok = java.util.Objects.equals(expected, actual);
        System.out.println((ok ? "  OK   " : "  FAIL ") + label + (ok ? "" : " (expected=" + expected + ", actual=" + actual + ")"));
        return ok ? 1 : 0;
    }

    private static void header(String name) { System.out.println("== " + name + " =="); }
}
