package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChain;

import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.GrantedAuthority;
import cn.jiebaba.summer.security.core.SecurityContextHolder;
import cn.jiebaba.summer.security.core.SimpleGrantedAuthority;
import cn.jiebaba.summer.security.web.AuthorizationRule.Decision;
import cn.jiebaba.summer.web.http.HttpStatus;
import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 强制执行 URL 级授权规则。在 {@link JwtAuthenticationFilter} 之后求值，
 * 使 {@link SecurityContextHolder} 反映所提交的 token。首条匹配规则胜出；
 * 若无规则匹配，则当配置了登录过滤器时默认策略为 {@code AUTHENTICATED}
 * （默认受保护），否则为 {@code PERMIT_ALL}。
 */
public final class AuthorizationFilter implements Filter {

    private final List<AuthorizationRule> rules;
    private final AuthorizationRule defaultRule;

    public AuthorizationFilter(List<AuthorizationRule> rules, AuthorizationRule defaultRule) {
        this.rules = List.copyOf(rules);
        this.defaultRule = defaultRule;
    }

    @Override
    /**
     * 过滤器入口：按 URL 授权规则校验当前请求，未授权时抛出 401/403，否则继续过滤器链。
     */
    public void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception {
        AuthorizationRule rule = null;
        for (AuthorizationRule r : rules) {
            if (r.matches(request.method(), request.path())) {
                rule = r;
                break;
            }
        }
        if (rule == null) rule = defaultRule;

        switch (rule.decision()) {
            case PERMIT_ALL -> chain.doFilter(request, response);
            case DENY_ALL -> forbidden(response, "Access denied");
            case AUTHENTICATED -> {
                if (SecurityContextHolder.isSet()) chain.doFilter(request, response);
                else JwtAuthenticationFilter.writeUnauthorized(response, "Authentication required");
            }
            case HAS_ANY_ROLE, HAS_ANY_AUTHORITY -> {
                Authentication auth = SecurityContextHolder.getAuthentication();
                if (auth == null || !auth.isAuthenticated()) {
                    JwtAuthenticationFilter.writeUnauthorized(response, "Authentication required");
                    return;
                }
                if (hasRequired(auth, rule)) {
                    chain.doFilter(request, response);
                } else {
                    forbidden(response, "Insufficient authority");
                }
            }
        }
    }

    private boolean hasRequired(Authentication auth, AuthorizationRule rule) {
        Set<String> required = rule.required();
        if (required.isEmpty()) return true;
        Set<String> owned = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        for (String r : required) {
            String want = rule.decision() == Decision.HAS_ANY_ROLE
                    ? (r.startsWith(SimpleGrantedAuthority.ROLE_PREFIX) ? r : SimpleGrantedAuthority.ROLE_PREFIX + r)
                    : r;
            if (owned.contains(want)) return true;
        }
        return false;
    }

    private static void forbidden(WebResponse response, String message) {
        response.status(HttpStatus.FORBIDDEN.code());
        response.contentType(MediaType.APPLICATION_JSON_UTF8);
        response.body("{\"status\":403,\"error\":\"Forbidden\",\"message\":\""
                + JsonEscape.escape(message) + "\"}");
    }
}
