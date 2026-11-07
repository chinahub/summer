package cn.jiebaba.summer.admin;

import cn.jiebaba.summer.admin.testsupport.MySqlTestDatabase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 初始化测试：验证 {@code sql/summer_admin.sql} 可在空测试库上执行、
 * 建齐 15 张表、种入 admin 种子数据、可重复执行且不覆盖已有修改。
 * 全部操作只发生在 {@code summer_admin_test}，绝不影响 {@code summer_admin}。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SqlInitializationTest {

    /** 设计 §6.2 规定的表清单。 */
    private static final Set<String> EXPECTED_TABLES = Set.of(
            "sys_user", "sys_dept", "sys_post", "sys_user_post", "sys_role",
            "sys_user_role", "sys_menu", "sys_role_menu", "sys_role_dept",
            "sys_dict_type", "sys_dict_data", "sys_config", "sys_notice",
            "sys_oper_log", "sys_login_log");

    @Test
    @Order(1)
    void emptyTestDatabaseInitializesWithFifteenTables() throws Exception {
        MySqlTestDatabase.reset();
        assertEquals(EXPECTED_TABLES, actualTables(), "测试库应恰好建齐 15 张设计表");
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT user_id, nick_name, password FROM sys_user WHERE user_id = 1")) {
            assertTrue(rs.next(), "种子数据应包含 user_id=1 的 admin");
            assertEquals(1L, rs.getLong(1));
            String password = rs.getString(3);
            assertTrue(password != null && password.startsWith("$2"),
                    "admin 密码必须保存为 BCrypt 哈希，实际为: " + password);
        }
    }

    @Test
    @Order(2)
    void scriptIsRerunnableAndPreservesBusinessModifications() throws Exception {
        // 第二次完整重放脚本（不重建库）应成功
        MySqlTestDatabase.executeScript();
        // 模拟业务修改后再重放，脚本不得覆盖昵称
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            st.execute("UPDATE sys_user SET nick_name = 'KeepMe' WHERE user_id = 1");
        }
        MySqlTestDatabase.executeScript();
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT nick_name FROM sys_user WHERE user_id = 1")) {
            assertTrue(rs.next());
            assertEquals("KeepMe", rs.getString(1), "重复执行脚本不得覆盖已有业务修改");
        }
    }

    @Test
    @Order(3)
    void testDatabaseIsIsolatedFromProduction() throws Exception {
        assertNotEquals("summer_admin", MySqlTestDatabase.testDatabaseName());
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
            assertTrue(rs.next());
            assertEquals(MySqlTestDatabase.TEST_DATABASE, rs.getString(1),
                    "当前连接必须落在隔离的测试库");
            assertNotEquals(MySqlTestDatabase.PRODUCTION_DATABASE, rs.getString(1));
        }
    }

    @Test
    @Order(4)
    void rerunDoesNotResurrectDeletedSeedRowsOrRelations() throws Exception {
        // 业务删除一条种子关系与一条种子业务行
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM sys_user_role WHERE user_id = 2 AND role_id = 2");
            st.execute("DELETE FROM sys_dict_data WHERE dict_code = 11");
        }
        MySqlTestDatabase.executeScript();
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM sys_user_role WHERE user_id = 2 AND role_id = 2")) {
                rs.next();
                assertEquals(0, rs.getLong(1), "重跑脚本不得复活已删除的种子关系");
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sys_dict_data WHERE dict_code = 11")) {
                rs.next();
                assertEquals(0, rs.getLong(1), "重跑脚本不得复活已删除的种子业务行");
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM sys_config WHERE config_key = 'sys.init.marker'")) {
                rs.next();
                assertEquals(1, rs.getLong(1), "初始化标记必须保持存在");
            }
        }
    }

    @Test
    @Order(5)
    void interruptedInitializationCanBeCompletedOnRerun() throws Exception {
        // 模拟初始化中断后的状态：无标记且缺失部分种子
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM sys_config WHERE config_key = 'sys.init.marker'");
            st.execute("DELETE FROM sys_dept WHERE dept_id = 4");
        }
        MySqlTestDatabase.executeScript();
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sys_dept WHERE dept_id = 4")) {
                rs.next();
                assertEquals(1, rs.getLong(1), "无标记时重跑应补齐缺失的种子行");
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM sys_config WHERE config_key = 'sys.init.marker'")) {
                rs.next();
                assertEquals(1, rs.getLong(1), "补齐后必须重新落初始化标记");
            }
        }
    }

    private static Set<String> actualTables() throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Connection conn = testConnection()) {
            try (ResultSet rs = conn.getMetaData().getTables(
                    conn.getCatalog(), null, "sys_%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
        }
        return tables;
    }

    private static Connection testConnection() throws SQLException {
        return DriverManager.getConnection(
                MySqlTestDatabase.testJdbcUrl(),
                requiredEnv("SUMMER_DATASOURCE_USERNAME"),
                requiredEnv("SUMMER_DATASOURCE_PASSWORD"));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }
}
