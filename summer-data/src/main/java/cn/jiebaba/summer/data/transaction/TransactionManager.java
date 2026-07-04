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
 * 在 {@link Transactional @Transactional} 方法执行期间，将 JDBC {@link Connection}
 * （autoCommit=false）绑定到当前线程。通过非 REQUIRES_NEW 的嵌套传播支持
 * 嵌套 @Transactional：嵌套调用加入当前事务（基于引用计数的栈）。
 */
public final class TransactionManager {

    private static final Logger LOG = Logger.getLogger(TransactionManager.class.getName());
    private static final ThreadLocal<Deque<Connection>> HOLDER = ThreadLocal.withInitial(ArrayDeque::new);

    private final DataSource dataSource;

    public TransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 开启事务（或加入当前事务）；绑定了新连接时返回 true。 */
    public boolean begin() {
        Deque<Connection> stack = HOLDER.get();
        if (!stack.isEmpty()) {
            return false; // 加入已有事务
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

    /** 结束事务：若由本调用方开启，则关闭并解绑连接。 */
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

    /** 绑定到当前线程的连接；无活动事务时为 null。 */
    public static Connection currentConnection() {
        Deque<Connection> stack = HOLDER.get();
        return stack.peek();
    }

    public boolean isActive() {
        return !HOLDER.get().isEmpty();
    }
}
