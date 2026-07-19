package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.logging.AiCallLog;
import cn.jiebaba.summer.boot.ai.logging.JdbcAiCallLogger;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Assumptions;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.data.support.SqlExecutor;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * JdbcAiCallLogger 端到端测试：将调用日志写入 PostgreSQL 的 ai_call_log 风格表并读回验证。
 * 复用 summer-sample 的 datasource 配置（经 Environment 读取，不硬编码凭据）。
 * 当 DB 不可达时通过 {@link Assumptions#assumeTrue} 跳过而非失败；本测试不依赖 pgvector 扩展。
 */
public class JdbcAiCallLoggerTest {

    private static final String TABLE = "summer_ai_log_test";

    /** 成功调用记录写入：验证模型、token 用量、success 与提问摘要落库正确。 */
    @Test
    public void jdbcLoggerWritesSuccessLog() throws Exception {
        DataSource ds = resolveDataSource();
        SqlExecutor sqlExecutor = new SqlExecutor(ds);
        dropTable(ds);
        JdbcAiCallLogger logger = new JdbcAiCallLogger(sqlExecutor, TABLE);

        logger.log(new AiCallLog("deepseek-chat", 10L, 20L, 30L, 123L, true, null, "hello"));

        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT model, prompt_tokens, completion_tokens, total_tokens, success, query_summary FROM " + TABLE)) {
            Assert.assertTrue(rs.next(), "应写入一条记录");
            Assert.assertEquals("deepseek-chat", rs.getString("model"));
            Assert.assertEquals(10, rs.getInt("prompt_tokens"));
            Assert.assertEquals(20, rs.getInt("completion_tokens"));
            Assert.assertEquals(30, rs.getInt("total_tokens"));
            Assert.assertTrue(rs.getBoolean("success"), "success 应为 true");
            Assert.assertEquals("hello", rs.getString("query_summary"));
            Assert.assertFalse(rs.next(), "应仅一条记录");
        }
        dropTable(ds);
    }

    /** 失败调用记录写入：验证 error_message 落库、success=false，且 token 字段可为 null。 */
    @Test
    public void jdbcLoggerWritesFailureLog() throws Exception {
        DataSource ds = resolveDataSource();
        SqlExecutor sqlExecutor = new SqlExecutor(ds);
        dropTable(ds);
        JdbcAiCallLogger logger = new JdbcAiCallLogger(sqlExecutor, TABLE);

        logger.log(new AiCallLog(null, null, null, null, 50L, false, "连接超时", "hi"));

        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT model, total_tokens, success, error_message FROM " + TABLE)) {
            Assert.assertTrue(rs.next(), "应写入一条失败记录");
            Assert.assertNull(rs.getString("model"), "无模型时 model 应为 null");
            Assert.assertEquals(0, rs.getInt("total_tokens"));
            Assert.assertTrue(rs.wasNull(), "total_tokens 应为 SQL NULL");
            Assert.assertFalse(rs.getBoolean("success"), "success 应为 false");
            Assert.assertEquals("连接超时", rs.getString("error_message"));
        }
        dropTable(ds);
    }

    /** 读取 summer.datasource.* 配置并探测 PG 可达，否则跳过测试。 */
    private static DataSource resolveDataSource() {
        Environment env = new Environment();
        String url = env.getProperty("summer.datasource.url");
        String user = env.getProperty("summer.datasource.username");
        String pass = env.getProperty("summer.datasource.password");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "无 summer.datasource.url 配置，跳过 JdbcAiCallLogger 测试");
        DriverManager.setLoginTimeout(10);
        DataSource ds = new SimpleDataSource(url, user, pass);
        try (Connection c = ds.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT 1")) {
            rs.next();
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "PG 不可达，跳过: " + e.getMessage());
        }
        return ds;
    }

    /** 删除测试表，保证用例幂等。 */
    private static void dropTable(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS " + TABLE);
        } catch (SQLException ignored) {
            // 清理失败不阻断测试
        }
    }

    /** 最小 DataSource 实现：每次 getConnection 经 DriverManager 新建连接，仅供测试使用。 */
    private static final class SimpleDataSource implements DataSource {
        private final String url;
        private final String user;
        private final String password;

        SimpleDataSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException("unwrap");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }
    }
}
