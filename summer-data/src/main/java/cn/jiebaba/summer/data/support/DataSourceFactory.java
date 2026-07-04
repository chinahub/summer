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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 基于阻塞队列与动态 {@link Connection} 代理构建的轻量级、受 HikariCP 启发的连接池。
 * 无第三方池化库。对虚拟线程友好。
 *
 * <p>设计（在可行处沿用 HikariCP 语义）：
 * <ul>
 *   <li>{@code pool-size} 为 <b>最大</b> 池大小。池按需增长至该上限，并在空闲队列为空
 *       但尚未达到上限时惰性创建连接，因此池在驱逐后能自愈，而非停滞到重启。</li>
 *   <li>{@code minimum-idle} 是后台 housekeeper 维持的下限；超出此值的空闲连接会被
 *       {@code idle-timeout} 裁剪。默认等于 {@code pool-size}（始终填满）。</li>
 *   <li>{@code max-lifetime} <b>按连接施加并带少量随机抖动</b>，使同一时刻创建的连接
 *       不会同时过期、导致连接池整体坍塌。</li>
 *   <li>超过生命周期的空闲连接会被 housekeeper 关闭并替换；借出的连接在归还时被
 *       软性退役。</li>
 *   <li>{@code keepalive-time} 周期性地用 {@code keepalive-query} 探测空闲连接，
 *       使其在池化代理之后仍保持存活。</li>
 *   <li>借用超时（{@code connection-timeout}）与泄漏检测
 *       （{@code leak-detection-threshold}）如前所述。</li>
 * </ul>
 * 后台维护运行在 daemon 虚拟线程上。
 */
public final class DataSourceFactory {

    /** 对空闲时间小于此值的连接跳过 isValid 校验（刚归还的连接）。 */
    private static final long VALIDATION_BYPASS_MS = 1000L;
    /** 仅当寿命超过此值时才施加最大寿命抖动（避免极小的随机区间）。 */
    private static final long MIN_LIFETIME_FOR_VARIANCE = 60_000L;
    /** isValid 探测超时（秒）。 */
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private DataSourceFactory() {}

    public static DataSource create(Environment environment) {
        return create(DataProperties.from(environment));
    }

    public static DataSource create(DataProperties props) {
        if (!props.isConfigured()) {
            throw new IllegalStateException("summer.datasource.url is not configured");
        }
        loadDriver(props);
        return new PooledDataSource(props);
    }

    public static DataSource create(String url, String username, String password, String driver,
                                    int poolSize,
                                    long connectionTimeout, long leakThreshold,
                                    long idleTimeout, long maxLifetime, String keepaliveQuery) {
        return create(url, username, password, driver, poolSize, connectionTimeout, leakThreshold,
                idleTimeout, maxLifetime, keepaliveQuery, poolSize, 0L);
    }

    public static DataSource create(String url, String username, String password, String driver,
                                    int poolSize,
                                    long connectionTimeout, long leakThreshold,
                                    long idleTimeout, long maxLifetime, String keepaliveQuery,
                                    int minimumIdle, long keepaliveTime) {
        int normalizedPool = Math.max(poolSize, 1);
        int normalizedMin = Math.max(Math.min(minimumIdle, normalizedPool), 0);
        DataProperties props = new DataProperties(url, username, password, driver, normalizedPool,
                connectionTimeout, leakThreshold, idleTimeout, maxLifetime, keepaliveQuery,
                normalizedMin, keepaliveTime);
        return create(props);
    }

    /** 当未配置数据源 URL 时返回的空操作 DataSource。 */
    public static DataSource lazyDummy() {
        return new LazyDataSource();
    }

    private static void loadDriver(DataProperties props) {
        if (props.driver().isBlank()) {
            return;
        }
        try {
            Class.forName(props.driver(), true, DataSourceFactory.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC driver not found on module/class path: " + props.driver()
                    + ". Add the driver as a runtime dependency.", e);
        }
    }

    static final class PooledDataSource implements DataSource {
        private static final Logger LOG = Logger.getLogger(PooledDataSource.class.getName());

        private final DataProperties props;
        private final int maximumPoolSize;
        private final int minimumIdle;
        private final BlockingQueue<PooledConnection> idle;
        private final AtomicInteger totalConnections = new AtomicInteger();
        private final AtomicBoolean filling = new AtomicBoolean();
        private final Map<PooledConnection, LeaseInfo> leases = new ConcurrentHashMap<>();
        private final List<Thread> maintenanceThreads = new ArrayList<>();

        record LeaseInfo(long borrowedAt, StackTraceElement[] stack) {}

        PooledDataSource(DataProperties props) {
            this.props = props;
            this.maximumPoolSize = Math.max(props.poolSize(), 1);
            this.minimumIdle = Math.max(Math.min(props.minimumIdle(), maximumPoolSize), 0);
            this.idle = new ArrayBlockingQueue<>(maximumPoolSize);

            // 将连接池预填充到 minimumIdle（数据库不可达时快速失败）。
            List<PooledConnection> created = new ArrayList<>();
            try {
                for (int i = 0; i < minimumIdle; i++) {
                    created.add(newConnection());
                }
            } catch (SQLException e) {
                for (PooledConnection pc : created) {
                    safeClose(pc);
                }
                throw new IllegalStateException("Failed to initialize connection pool for " + props.url(), e);
            }
            idle.addAll(created);
            totalConnections.set(created.size());

            long leakThreshold = props.leakDetectionThresholdMillis();
            if (leakThreshold > 0) {
                long interval = Math.max(leakThreshold / 2, 5000L);
                startMaintenance("summer-pool-leak", () -> leakDetectionLoop(leakThreshold, interval));
            }
            if (leakThreshold > 0 || props.idleTimeoutMillis() > 0 || props.maxLifetimeMillis() > 0
                    || props.keepaliveTimeMillis() > 0) {
                startMaintenance("summer-pool-housekeeping", () -> housekeepingLoop(computeHousekeepingInterval()));
            }
        }

        private long computeHousekeepingInterval() {
            long idleTimeout = props.idleTimeoutMillis();
            long maxLifetime = props.maxLifetimeMillis();
            long keepalive = props.keepaliveTimeMillis();
            long raw = Math.min(
                    Math.min(idleTimeout > 0 ? idleTimeout / 2 : Long.MAX_VALUE,
                            maxLifetime > 0 ? maxLifetime / 2 : Long.MAX_VALUE),
                    keepalive > 0 ? keepalive / 2 : Long.MAX_VALUE);
            return Math.max(Math.min(raw, 60_000L), 5_000L);
        }

        private void startMaintenance(String name, Runnable task) {
            Thread t = Thread.startVirtualThread(task);
            t.setName(name);
            maintenanceThreads.add(t);
        }

        private PooledConnection newConnection() throws SQLException {
            Connection raw = DriverManager.getConnection(props.url(), props.username(), props.password());
            return new PooledConnection(raw, System.currentTimeMillis(),
                    computeMaxLifetime(props.maxLifetimeMillis()));
        }

        private static long computeMaxLifetime(long maxLifetime) {
            if (maxLifetime <= 0) {
                return 0L;
            }
            if (maxLifetime <= MIN_LIFETIME_FOR_VARIANCE) {
                return maxLifetime;
            }
            // 最多抖动 2.5%（1/40），使同时创建的连接在不同时刻过期，
            // 避免整个连接池同时失效。
            long variance = ThreadLocalRandom.current().nextLong(maxLifetime / 40);
            return maxLifetime - variance;
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
            while (true) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                evictAndProbe(System.currentTimeMillis());
                triggerFill();
            }
        }

        /**
         * 逐个检查排空出的空闲连接：按最大寿命、已关闭、空闲超时、keepalive 探测与
         * 有效性校验决定保留或回收，保留的重新放入空闲队列。
         */
        private void evictAndProbe(long now) {
            long idleTimeout = props.idleTimeoutMillis();
            long maxLifetime = props.maxLifetimeMillis();
            long keepalive = props.keepaliveTimeMillis();
            String keepaliveQuery = props.keepaliveQuery();

            List<PooledConnection> drain = new ArrayList<>();
            idle.drainTo(drain);
            int remainingToProcess = drain.size();
            for (PooledConnection pc : drain) {
                remainingToProcess--;
                long idleSince = now - pc.returnedAt;
                boolean keep = true;

                if (maxLifetime > 0 && pc.maxLifetime > 0 && (now - pc.createdAt) > pc.maxLifetime) {
                    LOG.fine("Closing connection exceeded max-lifetime (max " + maxLifetime + "ms)");
                    keep = false;
                } else if (isClosedQuietly(pc)) {
                    keep = false;
                } else if (idleTimeout > 0 && idleSince > idleTimeout
                        && (idle.size() + remainingToProcess) > minimumIdle) {
                    LOG.fine("Closing idle connection (idle " + idleSince + "ms)");
                    keep = false;
                } else if (keepalive > 0 && idleSince > keepalive) {
                    if (!runKeepalive(pc, keepaliveQuery)) {
                        LOG.fine("Closing connection: keepalive probe failed");
                        keep = false;
                    } else {
                        pc.returnedAt = now;
                    }
                } else if (idleSince > VALIDATION_BYPASS_MS && !isValidQuietly(pc)) {
                    keep = false;
                }

                if (keep) {
                    if (!idle.offer(pc)) {
                        retire(pc);
                    }
                } else {
                    retire(pc);
                }
            }
        }

        private static boolean isClosedQuietly(PooledConnection pc) {
            try {
                return pc.raw.isClosed();
            } catch (SQLException e) {
                return true;
            }
        }

        private static boolean isValidQuietly(PooledConnection pc) {
            try {
                return pc.raw.isValid(VALIDATION_TIMEOUT_SECONDS);
            } catch (SQLException e) {
                return false;
            }
        }

        private static boolean runKeepalive(PooledConnection pc, String query) {
            try (Statement st = pc.raw.createStatement()) {
                st.execute(query);
                return true;
            } catch (SQLException e) {
                return false;
            }
        }

        private void triggerFill() {
            if (totalConnections.get() >= minimumIdle) {
                return;
            }
            if (!filling.compareAndSet(false, true)) {
                return;
            }
            Thread.startVirtualThread(() -> {
                try {
                    fillPool();
                } finally {
                    filling.set(false);
                }
            });
        }

        private void fillPool() {
            while (totalConnections.get() < minimumIdle && totalConnections.get() < maximumPoolSize) {
                if (!tryReserveSlot()) {
                    break;
                }
                try {
                    PooledConnection pc = newConnection();
                    if (!idle.offer(pc)) {
                        retire(pc);
                        break;
                    }
                } catch (SQLException e) {
                    totalConnections.decrementAndGet();
                    LOG.warning("Failed to refill connection pool: " + e.getMessage());
                    break;
                }
            }
        }

        /** 原子地为新连接预留一个槽位，上限为 maximumPoolSize。 */
        private boolean tryReserveSlot() {
            while (true) {
                int cur = totalConnections.get();
                if (cur >= maximumPoolSize) {
                    return false;
                }
                if (totalConnections.compareAndSet(cur, cur + 1)) {
                    return true;
                }
            }
        }

        private static void safeClose(PooledConnection pc) {
            try { pc.close(); } catch (SQLException ignore) {}
        }

        private void retire(PooledConnection pc) {
            safeClose(pc);
            totalConnections.decrementAndGet();
        }

        private static String formatStack(StackTraceElement[] stack) {
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(stack.length, 12);
            for (int i = 0; i < limit; i++) {
                sb.append("    at ").append(stack[i]).append('\n');
            }
            return sb.toString();
        }

        /**
         * 借出一个连接：优先取空闲连接，否则在配额内新建，已达上限则等待归还，
         * 超时则抛出 {@link SQLException}。
         */
        private PooledConnection borrow() throws SQLException {
            long deadline = System.currentTimeMillis() + props.connectionTimeoutMillis();
            while (true) {
                PooledConnection pc = idle.poll();
                if (pc != null) {
                    if (isUsable(pc)) {
                        leases.put(pc, new LeaseInfo(System.currentTimeMillis(),
                                Thread.currentThread().getStackTrace()));
                        return pc;
                    }
                    retire(pc);
                    continue;
                }
                // 当前无空闲连接：在配额允许时新建（自愈）。
                if (tryReserveSlot()) {
                    try {
                        PooledConnection created = newConnection();
                        leases.put(created, new LeaseInfo(System.currentTimeMillis(),
                                Thread.currentThread().getStackTrace()));
                        return created;
                    } catch (SQLException e) {
                        totalConnections.decrementAndGet();
                        throw new SQLException("Failed to create a pooled connection", e);
                    }
                }
                // 已达上限：等待归还。
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    throw borrowTimeout();
                }
                PooledConnection waited;
                try {
                    waited = idle.poll(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted waiting for a pooled connection", e);
                }
                if (waited == null) {
                    throw borrowTimeout();
                }
                if (isUsable(waited)) {
                    leases.put(waited, new LeaseInfo(System.currentTimeMillis(),
                            Thread.currentThread().getStackTrace()));
                    return waited;
                }
                retire(waited);
            }
        }

        private SQLException borrowTimeout() {
            return new SQLException("Connection wait timeout after " + props.connectionTimeoutMillis()
                    + "ms (max pool size=" + maximumPoolSize + "). "
                    + "Consider increasing summer.datasource.pool-size or connection-timeout.");
        }

        private boolean isUsable(PooledConnection pc) {
            long now = System.currentTimeMillis();
            if (pc.maxLifetime > 0 && (now - pc.createdAt) > pc.maxLifetime) {
                return false;
            }
            try {
                if (pc.raw.isClosed()) {
                    return false;
                }
                if ((now - pc.returnedAt) <= VALIDATION_BYPASS_MS) {
                    return true;
                }
                return pc.raw.isValid(VALIDATION_TIMEOUT_SECONDS);
            } catch (SQLException e) {
                return false;
            }
        }

        private boolean isExpiredOrClosed(PooledConnection pc) {
            long now = System.currentTimeMillis();
            if (pc.maxLifetime > 0 && (now - pc.createdAt) > pc.maxLifetime) {
                return true;
            }
            return isClosedQuietly(pc);
        }

        /**
         * 归还连接：重置自动提交、校验是否过期或已关闭，随后放回空闲队列；
         * 不可用则回收并触发补充。
         */
        private void returnConnection(PooledConnection pc) {
            leases.remove(pc);
            try {
                if (!pc.raw.getAutoCommit()) {
                    pc.raw.setAutoCommit(true);
                }
            } catch (SQLException e) {
                retire(pc);
                triggerFill();
                return;
            }
            if (isExpiredOrClosed(pc)) {
                retire(pc);
                triggerFill();
                return;
            }
            pc.returnedAt = System.currentTimeMillis();
            if (!idle.offer(pc)) {
                retire(pc);
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

    /** 使用即抛异常的 DataSource；当未配置 summer.datasource.url 时返回。 */
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

    /** 记录物理连接生命周期时间戳，用于维护。 */
    static final class PooledConnection {
        final Connection raw;
        final long createdAt;
        final long maxLifetime;
        volatile long returnedAt;

        PooledConnection(Connection raw, long createdAt, long maxLifetime) {
            this.raw = raw;
            this.createdAt = createdAt;
            this.maxLifetime = maxLifetime;
            this.returnedAt = createdAt;
        }

        void close() throws SQLException {
            raw.close();
        }
    }

    /** 将每个 Connection 方法转发给物理连接，但 close() 会将连接归还连接池。 */
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
