package cn.jiebaba.summer.data.transaction;

import cn.jiebaba.summer.data.support.DataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Binds a JDBC {@link Connection} (autoCommit=false) to the current thread for
 * the duration of a {@link Transactional @Transactional} method. Supports
 * nested @Transactional via propagation REQUIRES_NEW-less nesting: nested calls
 * join the existing transaction (a reference-counted stack).
 */
public final class TransactionManager {

    private static final Logger LOG = Logger.getLogger(TransactionManager.class.getName());
    private static final ThreadLocal<Deque<Connection>> HOLDER = ThreadLocal.withInitial(ArrayDeque::new);

    private final DataSource dataSource;

    public TransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Begin a transaction (or join the current one); returns true if a new connection was bound. */
    public boolean begin() {
        Deque<Connection> stack = HOLDER.get();
        if (!stack.isEmpty()) {
            return false; // join existing transaction
        }
        try {
            Connection conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            stack.push(conn);
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to begin transaction", e);
        }
    }

    public void commit() {
        Deque<Connection> stack = HOLDER.get();
        Connection conn = stack.peek();
        if (conn == null) return;
        try {
            conn.commit();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to commit transaction", e);
        }
    }

    public void rollback() {
        Deque<Connection> stack = HOLDER.get();
        Connection conn = stack.peek();
        if (conn == null) return;
        try {
            conn.rollback();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to rollback transaction", e);
        }
    }

    /** End the transaction: close+unbind the connection if this caller began it. */
    public void end(boolean began) {
        if (!began) return;
        Deque<Connection> stack = HOLDER.get();
        Connection conn = stack.poll();
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                LOG.log(Level.FINE, "Failed to close transactional connection", e);
            }
        }
        if (stack.isEmpty()) {
            HOLDER.remove();
        }
    }

    /** The connection bound to the current thread, or null if no transaction is active. */
    public static Connection currentConnection() {
        Deque<Connection> stack = HOLDER.get();
        return stack.peek();
    }

    public boolean isActive() {
        return !HOLDER.get().isEmpty();
    }
}
