package cn.jiebaba.summer.web.server;

import cn.jiebaba.summer.core.env.Environment;

public record WebServerProperties(int port, String host, String contextPath,
                                  boolean keepAlive, int keepAliveTimeout, int maxRequestsPerConnection,
                                  int maxHeaderSize, int maxRequestSize,
                                  Ssl ssl) {

    /** TLS 配置：密钥库、信任库与客户端认证策略。 */
    public record Ssl(boolean enabled, String keystore, String keystoreType,
                      String keystorePassword, String truststore, String truststoreType,
                      String truststorePassword, boolean needClientAuth) {}

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

        boolean sslEnabled = env.getProperty("server.ssl.enabled", Boolean.class, false);
        String sslKeystore = env.getProperty("server.ssl.keystore", String.class, null);
        String sslKeystoreType = env.getProperty("server.ssl.keystoretype", String.class, "PKCS12");
        String sslKeystorePassword = env.getProperty("server.ssl.keystorepassword", String.class, null);
        String sslTruststore = env.getProperty("server.ssl.truststore", String.class, null);
        String sslTruststoreType = env.getProperty("server.ssl.truststoretype", String.class, "PKCS12");
        String sslTruststorePassword = env.getProperty("server.ssl.truststorepassword", String.class, null);
        boolean sslNeedClientAuth = env.getProperty("server.ssl.needclientauth", Boolean.class, false);
        Ssl ssl = new Ssl(sslEnabled, sslKeystore, sslKeystoreType, sslKeystorePassword,
                sslTruststore, sslTruststoreType, sslTruststorePassword, sslNeedClientAuth);
        return new WebServerProperties(port, host, contextPath, keepAlive, keepAliveTimeout, maxRequests,
                maxHeaderSize, maxRequestSize, ssl);
    }
}
