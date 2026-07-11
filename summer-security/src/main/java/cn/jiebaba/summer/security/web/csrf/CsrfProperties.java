package cn.jiebaba.summer.security.web.csrf;

import cn.jiebaba.summer.core.env.Environment;

/**
 * summer.security.csrf.* 配置项绑定：开关、Cookie 名称与属性，以及令牌回传的请求头名、参数名。
 * 默认未启用，需显式设置 {@code summer.security.csrf.enabled=true}。对应 Spring Security 的 CSRF 配置。
 *
 * <p>配置示例（application.yml）：
 * <pre>
 * summer:
 *   security:
 *     csrf:
 *       enabled: true
 *       cookie.name: XSRF-TOKEN
 *       cookie.http-only: false
 *       cookie.same-site: Lax
 *       header-name: X-XSRF-TOKEN
 * </pre>
 */
public record CsrfProperties(boolean enabled,
                             String cookieName,
                             boolean cookieHttpOnly,
                             boolean cookieSecure,
                             String cookieSameSite,
                             long cookieMaxAge,
                             String headerName,
                             String parameterName) {

    /**
     * 从环境配置解析 CsrfProperties。读取 summer.security.csrf.* 前缀下的各项；
     * 未配置时返回带默认值（开关关闭）的实例。
     *
     * @param env 环境配置
     * @return 解析得到的 CSRF 配置
     */
    public static CsrfProperties from(Environment env) {
        boolean enabled = env.getProperty("summer.security.csrf.enabled", Boolean.class, false);
        String cookieName = env.getProperty("summer.security.csrf.cookie.name", String.class, "XSRF-TOKEN");
        boolean httpOnly = env.getProperty("summer.security.csrf.cookie.http-only", Boolean.class, false);
        boolean secure = env.getProperty("summer.security.csrf.cookie.secure", Boolean.class, false);
        String sameSite = env.getProperty("summer.security.csrf.cookie.same-site", String.class, "Lax");
        long maxAge = env.getProperty("summer.security.csrf.cookie.max-age", Long.class, -1L);
        String headerName = env.getProperty("summer.security.csrf.header-name", String.class, "X-XSRF-TOKEN");
        String parameterName = env.getProperty("summer.security.csrf.parameter-name", String.class, "_csrf");
        return new CsrfProperties(enabled, cookieName, httpOnly, secure, sameSite, maxAge, headerName, parameterName);
    }
}
