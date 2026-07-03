package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.core.env.Environment;

public record DataProperties(String url, String username, String password,
                             String driver, int poolSize,
                             long connectionTimeoutMillis, long leakDetectionThresholdMillis,
                             long idleTimeoutMillis, long maxLifetimeMillis, String keepaliveQuery,
                             int minimumIdle, long keepaliveTimeMillis) {

    public static DataProperties from(Environment env) {
        return from(env, "summer.datasource");
    }

    public static DataProperties from(Environment env, String prefix) {
        int poolSize = env.getProperty(prefix + ".pool-size", Integer.class, 8);
        return new DataProperties(
                env.getProperty(prefix + ".url", ""),
                env.getProperty(prefix + ".username", ""),
                env.getProperty(prefix + ".password", ""),
                env.getProperty(prefix + ".driver-class-name", ""),
                poolSize,
                env.getProperty(prefix + ".connection-timeout", Long.class, 30000L),
                env.getProperty(prefix + ".leak-detection-threshold", Long.class, 0L),
                env.getProperty(prefix + ".idle-timeout", Long.class, 600000L),
                env.getProperty(prefix + ".max-lifetime", Long.class, 1800000L),
                env.getProperty(prefix + ".keepalive-query", "SELECT 1"),
                env.getProperty(prefix + ".minimum-idle", Integer.class, poolSize),
                env.getProperty(prefix + ".keepalive-time", Long.class, 0L));
    }

    public boolean isConfigured() {
        return url != null && !url.isBlank();
    }
}