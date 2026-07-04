package cn.jiebaba.summer.test;

import cn.jiebaba.summer.data.datasource.DsContext;
import cn.jiebaba.summer.data.datasource.DynamicDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在无真实数据库的情况下测试多数据源路由逻辑。
 * 验证 DynamicDataSource 能依据 DsContext 路由到正确的底层连接池。
 */
public class MultiDsSmokeTest {

    private static int passed = 0;

    /**
     * 多数据源冒烟测试入口：构造 master/slave/log-db 三个 FakeDataSource，
     * 依次验证默认路由、@Master/@Slave/@DS 路由、嵌套路由的压栈/出栈与恢复。
     */
    public static void main(String[] args) throws Exception {
        Map<String, DataSource> sources = new LinkedHashMap<>();
        sources.put("master", new FakeDataSource("master"));
        sources.put("slave", new FakeDataSource("slave"));
        sources.put("log-db", new FakeDataSource("log-db"));
        DynamicDataSource dds = new DynamicDataSource(sources, "master");

        header("default routing");
        DsContext.clear();
        FakeConnection conn = (FakeConnection) unwrap(dds.getConnection());
        expect("default uses master", "master", conn.name);

        header("@Master routing");
        DsContext.push(DsContext.MASTER);
        conn = (FakeConnection) unwrap(dds.getConnection());
        expect("master routed", "master", conn.name);
        DsContext.pop();

        header("@Slave routing");
        DsContext.push(DsContext.SLAVE);
        conn = (FakeConnection) unwrap(dds.getConnection());
        expect("slave routed", "slave", conn.name);
        DsContext.pop();

        header("@DS(\"log-db\") routing");
        DsContext.push("log-db");
        conn = (FakeConnection) unwrap(dds.getConnection());
        expect("log-db routed", "log-db", conn.name);
        DsContext.pop();

        header("nested routing restore");
        DsContext.push("master");
        DsContext.push("slave");
        conn = (FakeConnection) unwrap(dds.getConnection());
        expect("nested top is slave", "slave", conn.name);
        DsContext.pop();
        conn = (FakeConnection) unwrap(dds.getConnection());
        expect("restored to master", "master", conn.name);
        DsContext.pop();

        header("default key");
        expect("default key is master", "master", dds.defaultKey());

        header("no routing after clear");
        DsContext.clear();
        conn = (FakeConnection) unwrap(dds.getConnection());
        expect("cleared uses default master", "master", conn.name);

        System.out.println();
        System.out.println("Multi-DS smoke test: " + passed + " assertions passed");
    }

    static Connection unwrap(Connection conn) {
        if (conn == null) return null;
        try {
            return conn.unwrap(Connection.class);
        } catch (SQLException e) {
            // FakeDataSource 返回的是原始连接，未经代理包装
            return conn;
        }
    }

    static void header(String name) { System.out.println("== " + name + " =="); }

    static void expect(String label, Object expected, Object actual) {
        boolean ok = java.util.Objects.equals(expected, actual);
        if (ok) { passed++; }
        else { System.out.println("  FAIL " + label + ": expected=" + expected + " actual=" + actual); }
    }

    static final class FakeDataSource implements DataSource {
        final String name;
        final AtomicInteger borrowed = new AtomicInteger();

        FakeDataSource(String name) { this.name = name; }

        @Override public Connection getConnection() {
            borrowed.incrementAndGet();
            return new FakeConnection(name);
        }
        @Override public Connection getConnection(String username, String password) { return getConnection(); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }

    static final class FakeConnection extends ConnectionStub {
        final String name;
        FakeConnection(String name) { this.name = name; }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("Not a wrapper");
        }
        @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
    }

    /** 最小化的 Connection 桩，对所有方法抛出 UnsupportedOperationException。 */
    static class ConnectionStub implements Connection {
        @Override public void close() {}
        @Override public boolean isClosed() { return false; }
        @Override public boolean getAutoCommit() { return true; }
        @Override public void setAutoCommit(boolean autoCommit) {}
        @Override public void commit() {}
        @Override public void rollback() {}
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public java.sql.Statement createStatement() { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql) { return null; }
        @Override public java.sql.CallableStatement prepareCall(String sql) { return null; }
        @Override public String nativeSQL(String sql) { return null; }
        @Override public java.sql.DatabaseMetaData getMetaData() { return null; }
        @Override public void setReadOnly(boolean readOnly) {}
        @Override public boolean isReadOnly() { return false; }
        @Override public void setCatalog(String catalog) {}
        @Override public String getCatalog() { return null; }
        @Override public void setTransactionIsolation(int level) {}
        @Override public int getTransactionIsolation() { return 0; }
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency) { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) { return null; }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) { return null; }
        @Override public Map<String, Class<?>> getTypeMap() { return null; }
        @Override public void setTypeMap(Map<String, Class<?>> map) {}
        @Override public void setHoldability(int holdability) {}
        @Override public int getHoldability() { return 0; }
        @Override public java.sql.Savepoint setSavepoint() { return null; }
        @Override public java.sql.Savepoint setSavepoint(String name) { return null; }
        @Override public void rollback(java.sql.Savepoint savepoint) {}
        @Override public void releaseSavepoint(java.sql.Savepoint savepoint) {}
        @Override public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return null; }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes) { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames) { return null; }
        @Override public java.sql.Clob createClob() { return null; }
        @Override public java.sql.Blob createBlob() { return null; }
        @Override public java.sql.NClob createNClob() { return null; }
        @Override public java.sql.SQLXML createSQLXML() { return null; }
        @Override public boolean isValid(int timeout) { return true; }
        @Override public void setClientInfo(String name, String value) {}
        @Override public void setClientInfo(java.util.Properties properties) {}
        @Override public String getClientInfo(String name) { return null; }
        @Override public java.util.Properties getClientInfo() { return null; }
        @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) { return null; }
        @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) { return null; }
        @Override public void setSchema(String schema) {}
        @Override public String getSchema() { return null; }
        @Override public void abort(java.util.concurrent.Executor executor) {}
        @Override public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) {}
        @Override public int getNetworkTimeout() { return 0; }
    }
}
