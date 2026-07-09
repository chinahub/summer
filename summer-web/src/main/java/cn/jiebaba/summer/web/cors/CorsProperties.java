package cn.jiebaba.summer.web.cors;

import cn.jiebaba.summer.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * summer.web.cors.* 配置项绑定：来源、方法、请求头、凭证与预检缓存时长等。
 * 对应 Spring Boot 的 CorsConfiguration / CorsProperties。
 *
 * <p>配置示例（application.yml）：
 * <pre>
 * summer:
 *   web:
 *     cors:
 *       enabled: true
 *       allowed-origins: [https://example.com, https://*.example.com]
 *       allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
 *       allowed-headers: [Content-Type, Authorization]
 *       exposed-headers: [X-Custom-Header]
 *       allow-credentials: true
 *       max-age: 1800
 * </pre>
 *
 * <p>列表项既支持逗号分隔的单值（application.properties 风格），也支持
 * YAML 列表展平后的索引形式（allowed-origins[0]）。
 */
public record CorsProperties(boolean enabled,
                             List<String> allowedOrigins,
                             List<String> allowedOriginPatterns,
                             List<String> allowedMethods,
                             List<String> allowedHeaders,
                             List<String> exposedHeaders,
                             boolean allowCredentials,
                             long maxAge) {

    /** 默认预检缓存时长（秒）。 */
    private static final long DEFAULT_MAX_AGE = 1800L;

    /**
     * 从环境配置解析 CorsProperties。读取 summer.web.cors.* 前缀下的各配置项，
     * 列表项支持逗号分隔或索引形式；未配置时返回 enabled=false 的空实例。
     *
     * @param env 环境配置
     * @return 解析得到的 CORS 配置
     */
    public static CorsProperties from(Environment env) {
        boolean enabled = env.getProperty("summer.web.cors.enabled", Boolean.class, false);
        List<String> allowedOrigins = readList(env, "summer.web.cors.allowed-origins");
        List<String> allowedOriginPatterns = readList(env, "summer.web.cors.allowed-origin-patterns");
        List<String> allowedMethods = readList(env, "summer.web.cors.allowed-methods");
        List<String> allowedHeaders = readList(env, "summer.web.cors.allowed-headers");
        List<String> exposedHeaders = readList(env, "summer.web.cors.exposed-headers");
        boolean allowCredentials = env.getProperty("summer.web.cors.allow-credentials", Boolean.class, false);
        long maxAge = env.getProperty("summer.web.cors.max-age", Long.class, DEFAULT_MAX_AGE);
        return new CorsProperties(enabled, allowedOrigins, allowedOriginPatterns, allowedMethods,
                allowedHeaders, exposedHeaders, allowCredentials, maxAge);
    }

    /**
     * 读取列表型配置：优先取逗号分隔的单值（支持 [a, b] 流式写法），
     * 再回退到 YAML 列表展平后的索引形式（key[0]、key[1] ...）。
     *
     * @param env 环境配置
     * @param key 不含索引后缀的配置键
     * @return 解析得到的不可变列表，可能为空
     */
    private static List<String> readList(Environment env, String key) {
        List<String> result = new ArrayList<>();
        String direct = env.getProperty(key);
        if (direct != null && !direct.isBlank()) {
            String value = direct;
            if (value.startsWith("[") && value.endsWith("]")) {
                value = value.substring(1, value.length() - 1);
            }
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
            if (!result.isEmpty()) return List.copyOf(result);
        }
        for (int i = 0; ; i++) {
            String indexed = env.getProperty(key + "[" + i + "]");
            if (indexed == null) break;
            if (!indexed.isBlank()) result.add(indexed.trim());
        }
        return List.copyOf(result);
    }
}
