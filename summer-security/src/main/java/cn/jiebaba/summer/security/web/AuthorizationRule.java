package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.WebRequest;

import java.util.Set;

/**
 * A URL-level authorization rule: a path pattern (Ant-style with {@code **}),
 * an optional HTTP method restriction, and a {@link Decision} that applies to
 * matching requests. Evaluated by {@link AuthorizationFilter} in registration
 * order; the first matching rule wins.
 */
public final class AuthorizationRule {

    public enum Decision {
        /** Allow without authentication. */
        PERMIT_ALL,
        /** Require an authenticated principal (any authority). */
        AUTHENTICATED,
        /** Deny everyone. */
        DENY_ALL,
        /** Require one of the given role names (matched against {@code ROLE_<name>} authorities). */
        HAS_ANY_ROLE,
        /** Require one of the given authority strings. */
        HAS_ANY_AUTHORITY
    }

    private final String pattern;
    private final HttpMethod method;       // null = any method
    private final Decision decision;
    private final Set<String> required;     // role/authority names depending on decision

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
