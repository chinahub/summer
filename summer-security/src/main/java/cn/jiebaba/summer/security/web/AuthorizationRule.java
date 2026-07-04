package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.WebRequest;

import java.util.Set;

/**
 * 一条 URL 级授权规则：路径模式（带 {@code **} 的 Ant 风格）、可选的 HTTP 方法限制，
 * 以及适用于匹配请求的 {@link Decision}。由 {@link AuthorizationFilter} 按注册顺序求值；
 * 首条匹配规则胜出。
 */
public final class AuthorizationRule {

    public enum Decision {
        /** 无需认证即允许。 */
        PERMIT_ALL,
        /** 要求已认证主体（任意权限）。 */
        AUTHENTICATED,
        /** 拒绝所有人。 */
        DENY_ALL,
        /** 要求具备给定角色名之一（与 {@code ROLE_<name>} 权限匹配）。 */
        HAS_ANY_ROLE,
        /** 要求具备给定权限字符串之一。 */
        HAS_ANY_AUTHORITY
    }

    private final String pattern;
    private final HttpMethod method;       // null = 任意方法
    private final Decision decision;
    private final Set<String> required;     // 角色/权限名称（取决于决策）

    public AuthorizationRule(String pattern, HttpMethod method, Decision decision, String... required) {
        this.pattern = normalize(pattern);
        this.method = method;
        this.decision = decision;
        this.required = Set.of(required);
    }

    public Decision decision() { return decision; }
    public Set<String> required() { return required; }

    public boolean matches(HttpMethod requestMethod, String requestPath) {
        if (method != null && method != requestMethod) return false;
        return AntPathMatcher.match(pattern, requestPath);
    }

    static String normalize(String p) {
        if (p == null || p.isBlank()) return "/**";
        if (!p.startsWith("/")) p = "/" + p;
        return p;
    }
}
