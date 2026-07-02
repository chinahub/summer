package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.core.env.Environment;

public record DataProperties(String url, String username, String password,
                             String driver, int poolSize,
                             long connectionTimeoutMillis, long leakDetectionThresholdMillis,
                             long idleTimeoutMillis, long maxLifetimeMillis, String keepaliveQuery) {

    public static DataProperties from(Environment env) {
        return new DataProperties(
                env.getProperty("summer.datasource.url", ""),
                env.getProperty("summer.datasource.username", ""),
                env.getProperty("summer.datasource.password", ""),
                env.getProperty("summer.datasource.driver-class-name", ""),
                env.getProperty("summer.datasource.pool-size", Integer.class, 8),
                env.getProperty("summer.datasource.connection-timeout", Long.class, 30000L),
                env.getProperty("summer.datasource.leak-detection-threshold", Long.class, 0L),
                env.getProperty("summer.datasource.idle-timeout", Long.class, 600000L),
                env.getProperty("summer.datasource.max-lifetime", Long.class, 1800000L),
                env.getProperty("summer.datasource.keepalive-query", "SELECT 1"));
    }

    public boolean isConfigured() {
        return url != null && !url.isBlank();
    }
}