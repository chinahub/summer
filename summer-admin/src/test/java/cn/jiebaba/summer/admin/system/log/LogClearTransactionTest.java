package cn.jiebaba.summer.admin.system.log;

import cn.jiebaba.summer.admin.SummerAdminApplication;
import cn.jiebaba.summer.admin.testsupport.MySqlTestDatabase;
import cn.jiebaba.summer.boot.SummerApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 清空事务边界真实集成测试：以完整应用（真实连接池 + 事务拦截器 + 代理 Service）
 * 对隔离测试库执行，用触发器在精确位置制造失败：
 * <ul>
 *   <li>删除中途失败（计数器表触发器，确保必然发生在第二行）：REQUIRED 事务
 *       必须回滚已删行，且不得留下"清空"审计；</li>
 *   <li>批量删除中途失败：同一 REQUIRED 事务语义，已删行必须随事务回滚，
 *       绝不留半批删除；</li>
 *   <li>审计插入失败（BEFORE INSERT 触发器）：REQUIRES_NEW 审计失败被隔离，
 *       清空结果照常提交，调用方仍得到成功返回。</li>
 * </ul>
 * 凭据只从环境变量读取；触发器与计数器表均在 finally 中清理。
 */
class LogClearTransactionTest {

    private static SummerApplication app;
    private static LogQueryService logQueryService;

    /** 启动应用：真实代理的 LogQueryService（声明式事务经 TransactionInterceptor 生效）。 */
    @BeforeAll
    static void startApplication() {
        MySqlTestDatabase.reset();
        app = SummerApplication.run(SummerAdminApplication.class,
                new String[]{"--server.port=0", "--summer.profiles.active=test"});
        logQueryService = app.context().getBean(LogQueryService.class);
    }

    @AfterAll
    static void stopApplication() {
        if (app != null) {
            app.webServer().stop();
            app.context().close();
            app = null;
        }
    }

    @Test
    void operBatchDeleteFailureRollsBackAllRows() throws Exception {
        seedOperLogs(3);
        createCounterTrigger("sys_oper_log");
        try {
            List<Long> ids = operLogIds();
            assertThrows(RuntimeException.class, () -> logQueryService.deleteOperLogs(ids),
                    "第二条删除失败必须向外抛出，由全局异常处理");
            assertEquals(3, countRows("sys_oper_log"), "REQUIRED 事务必须回滚批量删除已删的行，不允许部分删除");
        } finally {
            dropTrigger("trg_clear_probe", "sys_oper_log");
        }
    }

    @Test
    void loginBatchDeleteFailureRollsBackAllRows() throws Exception {
        seedLoginLogs(3);
        createCounterTrigger("sys_login_log");
        try {
            List<Long> ids = loginLogIds();
            assertThrows(RuntimeException.class, () -> logQueryService.deleteLoginLogs(ids),
                    "第二条删除失败必须向外抛出，由全局异常处理");
            assertEquals(3, countRows("sys_login_log"), "登录日志批量删除失败同样不得部分删除");
        } finally {
            dropTrigger("trg_clear_probe", "sys_login_log");
        }
    }

    @Test
    void operClearDeleteFailureRollsBackAllRowsAndSkipsAudit() throws Exception {
        seedOperLogs(3);
        createCounterTrigger("sys_oper_log");
        try {
            assertThrows(RuntimeException.class, () -> logQueryService.clearOperLogs(),
                    "第二行删除失败必须向外抛出，由全局异常处理");
            assertEquals(3, countRows("sys_oper_log"), "REQUIRED 事务必须回滚已删除的行，不允许部分清空");
            assertEquals(0, countOperClearAudit(), "删除失败路径不得留下“清空”审计事件");
        } finally {
            dropTrigger("trg_clear_probe", "sys_oper_log");
        }
    }

    @Test
    void operClearAuditFailureStillReturnsSuccessfulClear() throws Exception {
        seedOperLogs(3);
        createFailInsertTrigger("sys_oper_log");
        try {
            int cleared = logQueryService.clearOperLogs();
            assertEquals(3, cleared, "审计写库失败绝不允许替换清空的成功返回");
            assertEquals(0, countRows("sys_oper_log"), "清空结果必须照常提交");
            assertEquals(0, countOperClearAudit(), "审计插入失败则不应留下审计行");
        } finally {
            dropTrigger("trg_clear_probe", "sys_oper_log");
        }
    }

    @Test
    void loginClearDeleteFailureRollsBackAllRowsAndSkipsAudit() throws Exception {
        seedLoginLogs(3);
        createCounterTrigger("sys_login_log");
        try {
            assertThrows(RuntimeException.class, () -> logQueryService.clearLoginLogs());
            assertEquals(3, countRows("sys_login_log"), "登录日志清空失败同样不得部分清空");
            assertEquals(0, countOperClearAudit(), "删除失败路径不得把审计写进操作日志表");
        } finally {
            dropTrigger("trg_clear_probe", "sys_login_log");
        }
    }

    @Test
    void loginClearAuditFailureStillReturnsSuccessfulClear() throws Exception {
        seedLoginLogs(3);
        createFailInsertTrigger("sys_oper_log");
        try {
            int cleared = logQueryService.clearLoginLogs();
            assertEquals(3, cleared);
            assertEquals(0, countRows("sys_login_log"));
            assertEquals(0, countOperClearAudit());
        } finally {
            dropTrigger("trg_clear_probe", "sys_oper_log");
        }
    }

    // ---- 触发器与种子辅助 -------------------------------------------------

    /**
     * 创建"第二次 DELETE 必然失败"的触发器：用计数器表记录本语句内已触发的行数，
     * 与迭代顺序无关，保证失败一定发生在至少一行已被删除之后。
     *
     * @param table 目标表名（仅限本测试的 sys_oper_log / sys_login_log）
     */
    private static void createCounterTrigger(String table) throws Exception {
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS clear_probe ("
                    + "id INT PRIMARY KEY, cnt INT NOT NULL DEFAULT 0)");
            st.execute("DELETE FROM clear_probe");
            st.execute("INSERT INTO clear_probe (id, cnt) VALUES (1, 0)");
            st.execute("CREATE TRIGGER trg_clear_probe BEFORE DELETE ON " + table
                    + " FOR EACH ROW BEGIN"
                    + " UPDATE clear_probe SET cnt = cnt + 1 WHERE id = 1;"
                    + " IF (SELECT cnt FROM clear_probe WHERE id = 1) >= 2 THEN"
                    + " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'probe: fail on second delete';"
                    + " END IF; END");
        }
    }

    /** 创建"任何 INSERT 必然失败"的触发器：仅用于审计写入失败隔离场景。 */
    private static void createFailInsertTrigger(String table) throws Exception {
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TRIGGER trg_clear_probe BEFORE INSERT ON " + table
                    + " FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'probe: insert blocked'");
        }
    }

    private static void dropTrigger(String name, String table) throws Exception {
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TRIGGER IF EXISTS " + name);
        }
    }

    private static void seedOperLogs(int count) throws Exception {
        try (Connection conn = testConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO sys_oper_log (title, status) VALUES (?, 0)")) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM sys_oper_log");
            }
            for (int i = 1; i <= count; i++) {
                ps.setString(1, "种子操作" + i);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private static void seedLoginLogs(int count) throws Exception {
        try (Connection conn = testConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO sys_login_log (user_name, status) VALUES (?, '0')")) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM sys_login_log");
            }
            for (int i = 1; i <= count; i++) {
                ps.setString(1, "user" + i);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private static long countRows(String table) throws Exception {
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static List<Long> operLogIds() throws Exception {
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT oper_id FROM sys_oper_log ORDER BY oper_id")) {
            List<Long> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
            return ids;
        }
    }

    private static List<Long> loginLogIds() throws Exception {
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT info_id FROM sys_login_log ORDER BY info_id")) {
            List<Long> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
            return ids;
        }
    }

    private static long countOperClearAudit() throws Exception {
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM sys_oper_log WHERE business_type = 7")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Connection testConnection() throws Exception {
        return DriverManager.getConnection(MySqlTestDatabase.testJdbcUrl(),
                requiredEnv("SUMMER_DATASOURCE_USERNAME"), requiredEnv("SUMMER_DATASOURCE_PASSWORD"));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }
}
