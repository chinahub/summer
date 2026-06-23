package cn.jiebaba.summer.web.server;

import cn.jiebaba.summer.core.env.Environment;

public record WebServerProperties(int port, String host, String contextPath,
                                  boolean keepAlive, int keepAliveTimeout, int maxRequestsPerConnection,
                                  int maxHeaderSize, int maxRequestSize) {
    public static WebServerProperties from(Environment env) {
        int port = env.getProperty("server.port", Integer.class, 8080);
        String host = env.getProperty("server.host", String.class, "0.0.0.0");
        String contextPath = env.getProperty("server.context-path", String.class, "");
        if (contextPath == null) contextPath = "";
        if (!contextPath.isEmpty() && !contextPath.startsWith("/")) contextPath = "/" + contextPath;
        if (contextPath.endsWith("/") && contextPath.length() > 1) {
            contextPath = contextPath.substring(0, contextPath.length() - 1);
        }
        boolean keepAlive = env.getProperty("server.keep-alive", Boolean.class, true);
        int keepAliveTimeout = env.getProperty("server.keep-alive-timeout", Integer.class, 30000);
        int maxRequests = env.getProperty("server.max-requests-per-connection", Integer.class, 100);
        int maxHeaderSize = env.getProperty("server.max-header-size", Integer.class, 16 * 1024);
        int maxRequestSize = env.getProperty("server.max-request-size", Integer.class, 10 * 1024 * 1024);
        return new WebServerProperties(port, host, contextPath, keepAlive, keepAliveTimeout, maxRequests,
                maxHeaderSize, maxRequestSize);
    }
}