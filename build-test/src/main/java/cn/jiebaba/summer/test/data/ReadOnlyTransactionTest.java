package cn.jiebaba.summer.test.data;

import cn.jiebaba.summer.data.transaction.TransactionManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * 只读事务（{@code @Transactional(readOnly=true)} 落地）的单测：
 * begin(readOnly=true) 应将连接标记为只读；事务结束应恢复为可写，避免池内状态残留。
 * 使用 JDK 代理记录连接调用序列，不依赖真实数据库。
 */
public class ReadOnlyTransactionTest {

    /** 记录全部方法调用的 Connection 代理。 */
    private static Connection recordingConnection(List<String> calls) {
        return (Connection) Proxy.newProxyInstance(ReadOnlyTransactionTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    calls.add(method.getName()
                            + (args != null && args.length > 0 ? "(" + args[0] + ")" : "()"));
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r == int.class) return 0;
                    return null;
                });
    }

    private static DataSource singleConnectionDataSource(Connection conn) {
        return new DataSource() {
            @Override public Connection getConnection() { return conn; }
            @Override public Connection getConnection(String username, String password) { return conn; }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public <T> T unwrap(Class<T> iface) throws java.sql.SQLException { throw new java.sql.SQLException("Not a wrapper"); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
            @Override public java.util.logging.Logger getParentLogger() { return null; }
        };
    }

    @Test
    public void readOnlyBeginMarksConnectionAndEndRestores() {
        List<String> calls = new ArrayList<>();
        TransactionManager tm = new TransactionManager(singleConnectionDataSource(recordingConnection(calls)));

        Assertions.assertTrue(tm.begin(true));
        tm.commit();
        tm.end(true);

        int beginIdx = calls.indexOf("setAutoCommit(false)");
        int readOnlyIdx = calls.indexOf("setReadOnly(true)");
        int restoreIdx = calls.indexOf("setReadOnly(false)");
        int endAutoCommitIdx = calls.indexOf("setAutoCommit(true)");
        Assertions.assertTrue(beginIdx >= 0 && readOnlyIdx > beginIdx,
                "begin 应先关闭自动提交再标记只读，实际: " + calls);
        Assertions.assertTrue(restoreIdx >= 0 && endAutoCommitIdx > restoreIdx,
                "end 应先恢复可写再关闭连接，实际: " + calls);
    }

    @Test
    public void writableBeginDoesNotTouchReadOnlyFlag() {
        List<String> calls = new ArrayList<>();
        TransactionManager tm = new TransactionManager(singleConnectionDataSource(recordingConnection(calls)));

        Assertions.assertTrue(tm.begin());
        tm.commit();
        tm.end(true);

        Assertions.assertFalse(calls.contains("setReadOnly(true)"), "默认 begin 不应设置只读");
        Assertions.assertTrue(calls.contains("setReadOnly(false)"), "end 应总是恢复可写标记");
    }

    @Test
    public void nestedTransactionDoesNotAlterConnection() {
        List<String> calls = new ArrayList<>();
        TransactionManager tm = new TransactionManager(singleConnectionDataSource(recordingConnection(calls)));

        Assertions.assertTrue(tm.begin(true));
        // 嵌套加入已有事务：不绑定新连接，也不改变连接属性（由最外层决定）
        Assertions.assertFalse(tm.begin(false));
        tm.commit();
        tm.end(true);

        Assertions.assertEquals(1, countOf(calls, "setAutoCommit(false)"), "嵌套 begin 不应重复借连接");
        Assertions.assertEquals(1, countOf(calls, "setReadOnly(true)"));
    }

    private static int countOf(List<String> calls, String entry) {
        int n = 0;
        for (String c : calls) {
            if (c.equals(entry)) n++;
        }
        return n;
    }
}
