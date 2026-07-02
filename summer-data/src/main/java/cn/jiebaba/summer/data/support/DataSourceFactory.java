package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.core.env.Environment;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A tiny blocking-queue connection pool wrapping {@link DriverManager}. No
 * third-party pooling library. Virtual-thread friendly.
 *
 * <p>Supports:
 * <ul>
 *   <li>borrow timeout ({@code connection-timeout})</li>
 *   <li>leak detection ({@code leak-detection-threshold})</li>
 *   <li>idle connection eviction ({@code idle-timeout})</li>
 *   <li>max-lifetime recycling ({@code max-lifetime})</li>
 *   <li>keepalive probe ({@code keepalive-query})</li>
 * </ul>
 * Background maintenance runs on daemon virtual threads.
 */
public final class DataSourceFactory {

    private DataSourceFactory() {}

    public static DataSource create(String url, String username, String password, String driver,
                                    int poolSize,
                                    long connectionTimeout, long leakThreshold,
                                    long idleTimeout, long maxLifetime, String keepaliveQuery) {
        DataProperties props = new DataProperties(url, username, password, driver, poolSize,
                connectionTimeout, leakThreshold, idleTimeout, maxLifetime, keepaliveQuery);
        if (!props.isConfigured()) {
            throw new IllegalStateException("summer.datasource.url is not configured");
        }
        if (!props.driver().isBlank()) {
            try {
                Class.forName(props.driver(), true, DataSourceFactory.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("JDBC driver not found on module/class path: " + props.driver()
                        + ". Add the driver as a runtime dependency.", e);
            }
        }
        return new PooledDataSource(props);
    }

    public static DataSource create(Environment environment) {
        DataProperties props = DataProperties.from(environment);
        if (!props.isConfigured()) {
            throw new IllegalStateException("summer.datasource.url is not configured");
        }
        if (!props.driver().isBlank()) {
            try {
                Class.forName(props.driver(), true, DataSourceFactory.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("JDBC driver not found on module/class path: " + props.driver()
                        + ". Add the driver as a runtime dependency.", e);
            }
        }
        return new PooledDataSource(props);
    }

    /** Returns a no-op DataSource for when no datasource URL is configured. */
    public static DataSource lazyDummy() {
        return new LazyDataSource();
    }

    static final class PooledDataSource implements DataSource {
        private static final Logger LOG = Logger.getLogger(PooledDataSource.class.getName());

        private final DataProperties props;
        private final BlockingQueue<PooledConnection> pool;
        private final Map<PooledConnection, LeaseInfo> leases = new ConcurrentHashMap<>();
        private final List<Thread> maintenanceThreads = new ArrayList<>();

        record LeaseInfo(long borrowedAt, StackTraceElement[] stack) {}

        PooledDataSource(DataProperties props) {
            this.props = props;
            int size = Math.max(props.poolSize(), 1);
            this.pool = new ArrayBlockingQueue<>(size);
            List<PooledConnection> created = new ArrayList<>(size);
            try {
                for (int i = 0; i < size; i++) {
                    created.add(newConnection());
                }
            } catch (SQLException e) {
                for (PooledConnection pc : created) {
                    try { pc.close(); } catch (SQLException ignore) {}
                }
                throw new IllegalStateException("Failed to initialize connection pool for " + props.url(), e);
            }
            pool.addAll(created);

            long leakThreshold = props.leakDetectionThresholdMillis();
            if (leakThreshold > 0) {
                long interval = Math.max(leakThreshold / 2, 5000L);
                startMaintenance("summer-pool-leak", () -> leakDetectionLoop(leakThreshold, interval));
            }
            long idleTimeout = props.idleTimeoutMillis();
            long maxLifetime = props.maxLifetimeMillis();
            if (idleTimeout > 0 || maxLifetime > 0) {
                long raw = Math.min(
                        idleTimeout > 0 ? idleTimeout / 2 : Long.MAX_VALUE,
                        maxLifetime > 0 ? maxLifetime / 2 : Long.MAX_VALUE);
                final long interval = Math.max(Math.min(raw, 60000L), 5000L);
                startMaintenance("summer-pool-housekeeping", () -> housekeepingLoop(interval));
            }
        }

        private void startMaintenance(String name, Runnable task) {
            Thread t = Thread.startVirtualThread(task);
            t.setName(name);
            maintenanceThreads.add(t);
        }

        private PooledConnection newConnection() throws SQLException {
            Connection raw = DriverManager.getConnection(props.url(), props.username(), props.password());
            return new PooledConnection(raw, System.currentTimeMillis());
        }

        private void leakDetectionLoop(long threshold, long interval) {
            while (true) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long now = System.currentTimeMillis();
                for (var entry : leases.entrySet()) {
                    long held = now - entry.getValue().borrowedAt();
                    if (held > threshold) {
                        LOG.warning("Possible connection leak: connection held for " + held
                                + "ms (threshold=" + threshold + "ms). Borrow site:\n"
                                + formatStack(entry.getValue().stack()));
                    }
                }
            }
        }

        private void housekeepingLoop(long interval) {
            long idleTimeout = props.idleTimeoutMillis();
            long maxLifetime = props.maxLifetimeMillis();
            while (true) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long now = System.currentTimeMillis();
                List<PooledConnection> drain = new ArrayList<>();
                pool.drainTo(drain);
                for (int idx = 0; idx < drain.size(); idx++) {
                    PooledConnection pc = drain.get(idx);
                    boolean keep = true;
                    if (maxLifetime > 0 && (now - pc.createdAt) > maxLifetime) {
                        LOG.fine("Closing connection exceeded max-lifetime (" + maxLifetime + "ms)");
                        keep = false;
                    }
                    if (keep && idleTimeout > 0 && (now - pc.returnedAt) > idleTimeout) {
                        // pool.size(): already returned to the pool this round;
                        // remainingInDrain: connections still to be put back, including this one.
                        // Only close an idle connection when at least one other connection will
                        // remain available, otherwise the pool gets drained to zero (see #14).
                        int remainingInDrain = drain.size() - idx;
                        int current = pool.size() + remainingInDrain - 1;
                        if (current >= 1) {
                            LOG.fine("Closing idle connection (idle " + (now - pc.returnedAt) + "ms)");
                            keep = false;
                        }
                    }
                    if (keep) {
                        if (!pool.offer(pc)) {
                            safeClose(pc);
                        }
                    } else {
                        safeClose(pc);
                    }
                }
            }
        }

        private static void safeClose(PooledConnection pc) {
            try { pc.close(); } catch (SQLException ignore) {}
        }

        private static String formatStack(StackTraceElement[] stack) {
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(stack.length, 12);
            for (int i = 0; i < limit; i++) {
                sb.append("    at ").append(stack[i]).append('\n');
            }
            return sb.toString();
        }

        private PooledConnection borrow() throws SQLException {
            PooledConnection pc;
            try {
                pc = pool.poll(props.connectionTimeoutMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted waiting for a pooled connection", e);
            }
            if (pc == null) {
                throw new SQLException("Connection wait timeout after " + props.connectionTimeoutMillis()
                        + "ms (pool size=" + props.poolSize() + "). Consider increasing summer.datasource.pool-size.");
            }
            if (pc.raw.isClosed() || !pc.raw.isValid(2)) {
                safeClose(pc);
                try {
                    pc = newConnection();
                } catch (SQLException e) {
                    throw new SQLException("Failed to create replacement connection", e);
                }
            }
            leases.put(pc, new LeaseInfo(System.currentTimeMillis(), Thread.currentThread().getStackTrace()));
            return pc;
        }

        private void returnConnection(PooledConnection pc) {
            leases.remove(pc);
            try {
                if (!pc.raw.getAutoCommit()) {
                    pc.raw.setAutoCommit(true);
                }
            } catch (SQLException ignore) {
                safeClose(pc);
                return;
            }
            pc.returnedAt = System.currentTimeMillis();
            if (!pool.offer(pc)) {
                safeClose(pc);
            }
        }

        @Override
        public Connection getConnection() throws SQLException {
            PooledConnection pc = borrow();
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    new PooledConnectionHandler(pc, this));
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(props.url(), username, password);
        }

        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public Logger getParentLogger() { return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME); }
    }

    /** A DataSource that throws on use; returned when summer.datasource.url is not configured. */
    static final class LazyDataSource implements DataSource {
        @Override public Connection getConnection() throws SQLException {
            throw new SQLException("DataSource not configured (summer.datasource.url is empty)");
        }
        @Override public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public Logger getParentLogger() { return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME); }
    }

    /** Tracks a physical connection's lifecycle timestamps for housekeeping. */
    static final class PooledConnection {
        final Connection raw;
        final long createdAt;
        volatile long returnedAt;

        PooledConnection(Connection raw, long createdAt) {
            this.raw = raw;
            this.createdAt = createdAt;
            this.returnedAt = createdAt;
        }

        void close() throws SQLException {
            raw.close();
        }
    }

    /** Forwards every Connection method to the physical connection, except close() returns it to the pool. */
    static final class PooledConnectionHandler implements InvocationHandler {
        private final PooledConnection pc;
        private final PooledDataSource owner;

        PooledConnectionHandler(PooledConnection pc, PooledDataSource owner) {
            this.pc = pc;
            this.owner = owner;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("close")) {
                owner.returnConnection(pc);
                return null;
            }
            if (name.equals("isClosed") && args == null) {
                return pc.raw.isClosed();
            }
            if (name.equals("unwrap") && args != null && args.length == 1) {
                Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(pc.raw)) return pc.raw;
            }
            try {
                return method.invoke(pc.raw, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
