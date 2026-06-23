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
 * A {@link DataSource} that routes {@link #getConnection()} to one of several
 * underlying pools based on {@link DsContext#current()}. When no routing key is
 * set, the default (primary) datasource is used.
 *
 * <p>This is the single {@code DataSource} bean registered in the context.
 * {@link SqlExecutor SqlExecutor} and
 * {@link TransactionManager TransactionManager} use it
 * transparently — they see one DataSource, but the actual connection comes from
 * the pool selected by {@code @DS}/{@code @Master}/{@code @Slave}.
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

    /** Returns the datasource selected by the current routing context (or default). */
    public DataSource routed() {
        String key = DsContext.current();
        if (key == null) key = defaultKey;
        DataSource ds = dataSources.get(key);
        if (ds == null) {
            throw new IllegalStateException("No datasource named '" + key + "'. Available: " + dataSources.keySet());
        }
        return ds;
    }

    /** Returns the datasource for a specific name (no routing). */
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