package cn.jiebaba.summer.data.datasource;

import cn.jiebaba.summer.data.support.DataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages multi-datasource transactions for {@link DSTransactional}. When active,
 * {@link #getConnection(DataSource)} returns a transactional connection (autoCommit
 *=false) for the routed datasource, caching it per datasource so multiple
 * operations on the same source share one connection. On commit/rollback, all
 * participating connections are handled together.
 */
public final class DsTransactionManager {

    private static final Logger LOG = Logger.getLogger(DsTransactionManager.class.getName());

    private static final ThreadLocal<Map<String, Connection>> HOLDER = ThreadLocal.withInitial(LinkedHashMap::new);

    /**
     * Returns a transactional connection for the given datasource if a
     * {@code @DSTransactional} scope is active; otherwise returns null (caller
     * should get a fresh non-transactional connection).
     */
    public static Connection getConnection(DataSource dataSource) {
        Map<String, Connection> conns = HOLDER.get();
        if (conns.isEmpty()) return null;
        String key = dsKey(dataSource);
        return conns.get(key);
    }

    /** Begin a multi-datasource transaction scope. */
    public void begin() {
        HOLDER.get(); // initialize if not present
    }

    /** Borrow a transactional connection for the datasource (lazily on first use). */
    public static Connection borrow(DataSource dataSource) throws SQLException {
        Map<String, Connection> conns = HOLDER.get();
        String key = dsKey(dataSource);
        Connection existing = conns.get(key);
        if (existing != null) return existing;
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        conns.put(key, conn);
        return conn;
    }

    /** Commit all participating connections. */
    public void commit() {
        Map<String, Connection> conns = HOLDER.get();
        SQLException firstError = null;
        for (var entry : conns.entrySet()) {
            try {
                entry.getValue().commit();
            } catch (SQLException e) {
                LOG.log(Level.SEVERE, "Failed to commit datasource " + entry.getKey(), e);
                firstError = e;
            }
        }
        if (firstError != null) {
            throw new DataAccessException("Failed to commit one or more datasources", firstError);
        }
    }

    /** Rollback all participating connections. */
    public void rollback() {
        Map<String, Connection> conns = HOLDER.get();
        for (var entry : conns.entrySet()) {
            try {
                entry.getValue().rollback();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to rollback datasource " + entry.getKey(), e);
            }
        }
    }

    /** Close all participating connections and clear the scope. */
    public void end() {
        Map<String, Connection> conns = HOLDER.get();
        for (var entry : conns.entrySet()) {
            try {
                Connection conn = entry.getValue();
                if (!conn.getAutoCommit()) conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                LOG.log(Level.FINE, "Failed to close transactional connection for " + entry.getKey(), e);
            }
        }
        conns.clear();
        HOLDER.remove();
    }

    /** Whether a multi-datasource transaction scope is active. */
    public static boolean isActive() {
        return !HOLDER.get().isEmpty();
    }

    private static String dsKey(DataSource dataSource) {
        if (dataSource instanceof DynamicDataSource dds) {
            return dds.defaultKey() + "::" + DsContext.current();
        }
        return dataSource.toString();
    }
}