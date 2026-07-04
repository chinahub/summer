package cn.jiebaba.summer.data.datasource;

import cn.jiebaba.summer.data.support.SqlExecutor;
import cn.jiebaba.summer.data.transaction.TransactionManager;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 一种 {@link DataSource}，依据 {@link DsContext#current()} 将 {@link #getConnection()}
 * 路由到若干底层连接池之一。未设置路由键时使用默认（主）数据源。
 *
 * <p>这是注册到 context 中的单一 {@code DataSource} bean。
 * {@link SqlExecutor SqlExecutor} 与 {@link TransactionManager TransactionManager}
 * 透明地使用它——它们看到的是单个 DataSource，但实际连接来自由
 * {@code @DS}/{@code @Master}/{@code @Slave} 选定的连接池。
 */
public final class DynamicDataSource implements DataSource {

    private final Map<String, DataSource> dataSources;
    private final String defaultKey;

    public DynamicDataSource(Map<String, DataSource> dataSources, String defaultKey) {
        if (dataSources == null || dataSources.isEmpty()) {
            throw new IllegalArgumentException("At least one datasource required");
        }
        this.dataSources = new LinkedHashMap<>(dataSources);
        this.defaultKey = defaultKey != null ? defaultKey : this.dataSources.keySet().iterator().next();
        if (!this.dataSources.containsKey(this.defaultKey)) {
            throw new IllegalArgumentException("Default datasource '" + this.defaultKey + "' not found");
        }
    }

    /** 返回当前路由上下文选中的数据源（或默认）。 */
    public DataSource routed() {
        String key = DsContext.current();
        if (key == null) key = defaultKey;
        DataSource ds = dataSources.get(key);
        if (ds == null) {
            throw new IllegalStateException("No datasource named '" + key + "'. Available: " + dataSources.keySet());
        }
        return ds;
    }

    /** 按名称返回对应数据源（不做路由）。 */
    public DataSource forName(String name) {
        DataSource ds = dataSources.get(name);
        if (ds == null) {
            throw new IllegalStateException("No datasource named '" + name + "'. Available: " + dataSources.keySet());
        }
        return ds;
    }

    public Map<String, DataSource> all() {
        return dataSources;
    }

    public String defaultKey() {
        return defaultKey;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return routed().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return routed().getConnection(username, password);
    }

    @Override public PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(PrintWriter out) {}
    @Override public void setLoginTimeout(int seconds) {}
    @Override public int getLoginTimeout() { return 0; }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) return iface.cast(this);
        throw new SQLException("Not a wrapper");
    }
    @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
    @Override public Logger getParentLogger() { return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME); }
}
