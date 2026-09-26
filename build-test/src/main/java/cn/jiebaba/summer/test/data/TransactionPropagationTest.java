package cn.jiebaba.summer.test.data;

import cn.jiebaba.summer.data.transaction.TransactionManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * 事务传播行为单测：{@code REQUIRES_NEW} 挂起外层事务、开启独立新连接并独立提交，
 * 结束后外层事务恢复；{@code REQUIRED} 嵌套加入语义不变。使用 JDK 代理记录连接，不依赖真实数据库。
 */
public class TransactionPropagationTest {

    /** 每次借出新记录连接的数据源。 */
    private static DataSource dataSource(List<List<String>> connLogs, List<Connection> conns) {
        return new DataSource() {
            @Override public Connection getConnection() {
                List<String> calls = new ArrayList<>();
                connLogs.add(calls);
                Connection conn = recordingConnection(calls);
                conns.add(conn);
                return conn;
            }
            @Override public Connection getConnection(String username, String password) { return getConnection(); }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public <T> T unwrap(Class<T> iface) throws java.sql.SQLException { throw new java.sql.SQLException("Not a wrapper"); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
            @Override public java.util.logging.Logger getParentLogger() { return null; }
        };
    }

    private static Connection recordingConnection(List<String> calls) {
        return (Connection) Proxy.newProxyInstance(TransactionPropagationTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r == int.class) return 0;
                    return null;
                });
    }

    @Test
    @DisplayName("REQUIRES_NEW 挂起外层、独立提交、结束后外层恢复")
    void requiresNewSuspendsAndRestores() {
        List<List<String>> connLogs = new ArrayList<>();
        List<Connection> conns = new ArrayList<>();
        TransactionManager tm = new TransactionManager(dataSource(connLogs, conns));

        Assertions.assertTrue(tm.begin());
        Connection outer = TransactionManager.currentConnection();

        Assertions.assertTrue(tm.begin(false, true), "REQUIRES_NEW 应开启新连接");
        Assertions.assertNotSame(outer, TransactionManager.currentConnection(), "内层应使用新连接");
        tm.commit();
        tm.end(true);

        Assertions.assertSame(outer, TransactionManager.currentConnection(), "内层结束后外层应恢复");
        tm.commit();
        tm.end(true);
        Assertions.assertNull(TransactionManager.currentConnection());

        Assertions.assertEquals(2, conns.size(), "两次事务应各自持连接");
        Assertions.assertEquals(List.of("setAutoCommit", "commit", "setReadOnly", "setAutoCommit", "close"),
                connLogs.get(1), "内层连接应独立提交并关闭");
    }

    @Test
    @DisplayName("REQUIRED 嵌套加入已有事务：不借新连接、不重复提交")
    void requiredNestedJoins() {
        List<List<String>> connLogs = new ArrayList<>();
        List<Connection> conns = new ArrayList<>();
        TransactionManager tm = new TransactionManager(dataSource(connLogs, conns));

        Assertions.assertTrue(tm.begin());
        Assertions.assertFalse(tm.begin(false, false), "REQUIRED 嵌套应加入而非新开");
        tm.end(false);
        tm.commit();
        tm.end(true);

        Assertions.assertEquals(1, conns.size(), "嵌套不应借新连接");
        Assertions.assertEquals(1, connLogs.get(0).stream().filter("commit"::equals).count(),
                "仅外层提交一次");
    }

    @Test
    @DisplayName("REQUIRES_NEW 内层回滚不影响外层")
    void requiresNewRollbackIsIndependent() {
        List<List<String>> connLogs = new ArrayList<>();
        List<Connection> conns = new ArrayList<>();
        TransactionManager tm = new TransactionManager(dataSource(connLogs, conns));

        Assertions.assertTrue(tm.begin());
        Assertions.assertTrue(tm.begin(false, true));
        tm.rollback();
        tm.end(true);
        tm.commit();
        tm.end(true);

        Assertions.assertEquals(List.of("setAutoCommit", "rollback", "setReadOnly", "setAutoCommit", "close"),
                connLogs.get(1), "内层应回滚自身");
        Assertions.assertTrue(connLogs.get(0).contains("commit"), "外层不受内层回滚影响");
    }
}
