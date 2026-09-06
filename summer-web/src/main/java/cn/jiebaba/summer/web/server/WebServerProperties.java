package cn.jiebaba.summer.web.server;

import cn.jiebaba.summer.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

public record WebServerProperties(int port, String host, String contextPath,
                                  boolean keepAlive, int keepAliveTimeout, int maxRequestsPerConnection,
                                  int maxHeaderSize, int maxRequestSize,
                                  Compression compression,
                                  Ssl ssl) {

    /** TLS 配置：密钥库、信任库与客户端认证策略。 */
    public record Ssl(boolean enabled, String keystore, String keystoreType,
                      String keystorePassword, String truststore, String truststoreType,
                      String truststorePassword, boolean needClientAuth) {}

    /**
     * 响应压缩配置：仅对 mime 类型匹配且达到最小长度的响应体做 gzip。
     * minResponseSize 为启用压缩的最小响应体字节数。
     */
    public record Compression(boolean enabled, List<String> mimeTypes, int minResponseSize) {

        /** Content-Type（可带参数）是否命中配置的 mime 列表；支持 {@code type/*} 通配。 */
        public boolean matches(String contentType) {
            if (contentType == null) return false;
            String mime = contentType.split(";")[0].trim().toLowerCase();
            for (String pattern : mimeTypes) {
                String p = pattern.trim().toLowerCase();
                if (p.isEmpty()) continue;
                if (mime.equals(p) || (p.endsWith("/*") && mime.startsWith(p.substring(0, p.length() - 1)))) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 压缩判定与执行：enabled、mime 匹配、达到 minResponseSize、请求声明 gzip
         * 全部满足时返回 gzip 压缩字节，否则返回 {@code null}（调用方保持明文响应）。
         * gzip 执行失败同样返回 {@code null}，回退明文。
         */
        public byte[] apply(String contentType, byte[] body, boolean acceptsGzip) {
            if (!enabled || body == null || body.length == 0
                    || body.length < minResponseSize || !matches(contentType) || !acceptsGzip) {
                return null;
            }
            try {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(Math.max(32, body.length / 4));
                try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bos)) {
                    gz.write(body);
                }
                return bos.toByteArray();
            } catch (java.io.IOException e) {
                return null;
            }
        }
    }

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

        boolean compressionEnabled = env.getProperty("server.compression.enabled", Boolean.class, false);
        List<String> compressionMimeTypes = splitCsv(env.getProperty("server.compression.mime-types",
                String.class, "application/json,text/html,text/plain,text/xml,text/css,text/javascript"));
        int minResponseSize = env.getProperty("server.compression.min-response-size", Integer.class, 2048);
        Compression compression = new Compression(compressionEnabled, compressionMimeTypes, minResponseSize);
        return new WebServerProperties(port, host, contextPath, keepAlive, keepAliveTimeout, maxRequests,
                maxHeaderSize, maxRequestSize, compression, ssl);
    }

    /** 逗号分隔字符串拆为列表（去空段）。 */
    private static List<String> splitCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String item : csv.split(",")) {
            if (!item.isBlank()) out.add(item.trim());
        }
        return out;
    }
}
