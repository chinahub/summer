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
 * 为 {@link DSTransactional} 管理多数据源事务。激活时，{@link #getConnection(DataSource)}
 * 返回路由数据源的事务连接（autoCommit=false），并按数据源缓存，使同一数据源的多次操作
 * 共用一个连接。提交/回滚时统一处理所有参与连接。
 */
public final class DsTransactionManager {

    private static final Logger LOG = Logger.getLogger(DsTransactionManager.class.getName());

    private static final ThreadLocal<Map<String, Connection>> HOLDER = ThreadLocal.withInitial(LinkedHashMap::new);

    /**
     * 当 {@code @DSTransactional} 作用域激活时，返回给定数据源的事务连接；
     * 否则返回 {@code null}（调用方应自行获取新的非事务连接）。
     */
    public static Connection getConnection(DataSource dataSource) {
        Map<String, Connection> conns = HOLDER.get();
        if (conns.isEmpty()) return null;
        String key = dsKey(dataSource);
        return conns.get(key);
    }

    /** 开启多数据源事务作用域。 */
    public void begin() {
        HOLDER.get(); // 若不存在则初始化
    }

    /** 为数据源借出事务连接（首次使用时惰性创建）。 */
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

    /** 提交所有参与连接。 */
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

    /** 回滚所有参与连接。 */
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

    /** 关闭所有参与连接并清除作用域。 */
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

    /** 多数据源事务作用域是否激活。 */
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
