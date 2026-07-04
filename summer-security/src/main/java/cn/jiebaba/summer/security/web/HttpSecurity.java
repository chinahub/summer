package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.filter.Filter;

import cn.jiebaba.summer.security.authentication.AuthenticationManager;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.web.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SecurityFilterChain} 的流畅构建器。在精神上对应 Spring Security 的
 * {@code HttpSecurity} DSL（不含 SpEL）：
 * <pre>
 *   http.authorize(
 *         match("/public/**").permitAll(),
 *         match("/admin/**").hasRole("ADMIN"),
 *         anyRequest().authenticated())
 *       .jwt(jwt -&gt; jwt.loginUrl("/login").tokenTtl(3600))
 *       .build();
 * </pre>
 * 登录过滤器最先运行，随后是 JWT 认证，最后是授权。
 */
public final class HttpSecurity {

    private final List<AuthorizationRule> rules = new ArrayList<>();
    private AuthorizationRule defaultRule = new AuthorizationRule("/**", null,
            AuthorizationRule.Decision.PERMIT_ALL);
    private JwtConfigurer jwt;

    private HttpSecurity() {}

    public static HttpSecurity security() {
        return new HttpSecurity();
    }

    /** 添加授权规则；最后一条作为兜底默认规则。 */
    public HttpSecurity authorize(AuthorizationSpec... specs) {
        for (AuthorizationSpec spec : specs) {
            AuthorizationRule rule = spec.toRule();
            if (spec.isCatchAll()) {
                this.defaultRule = rule;
            } else {
                this.rules.add(rule);
            }
        }
        return this;
    }

    public HttpSecurity jwt(java.util.function.Consumer<JwtConfigurer> configurer) {
        this.jwt = new JwtConfigurer();
        configurer.accept(this.jwt);
        return this;
    }

    /** 构建过滤器链。需先设置 {@link JwtConfigurer}（含编码器/解码器/管理器）。 */
    public SecurityFilterChain build() {
        List<Filter> filters = new ArrayList<>();
        if (jwt == null || !jwt.configured()) {
            // 未配置 JWT：构建全放行链（安全实际被禁用）。
            return new SecurityFilterChain(filters);
        }
        if (jwt.loginEnabled()) {
            filters.add(new JwtLoginFilter(jwt.authenticationManager, jwt.encoder,
                    jwt.tokenTtlSeconds, jwt.loginUrl));
        }
        filters.add(new JwtAuthenticationFilter(jwt.decoder));
        filters.add(new AuthorizationFilter(rules, defaultRule));
        return new SecurityFilterChain(filters);
    }

    // ---- 规则定义 -------------------------------------------------------

    public static AuthorizationSpec match(String pattern) {
        return new AuthorizationSpec(pattern, null, false);
    }

    public static AuthorizationSpec match(HttpMethod method, String pattern) {
        return new AuthorizationSpec(pattern, method, false);
    }

    public static AuthorizationSpec anyRequest() {
        return new AuthorizationSpec("/**", null, true);
    }

    /** 描述一条 URL 规则及其决策的定义。 */
    public static final class AuthorizationSpec {
        private final String pattern;
        private final HttpMethod method;
        private final boolean catchAll;
        private AuthorizationRule.Decision decision = AuthorizationRule.Decision.AUTHENTICATED;
        private String[] required = new String[0];

        AuthorizationSpec(String pattern, HttpMethod method, boolean catchAll) {
            this.pattern = pattern;
            this.method = method;
            this.catchAll = catchAll;
        }

        public AuthorizationSpec permitAll() { this.decision = AuthorizationRule.Decision.PERMIT_ALL; return this; }
        public AuthorizationSpec denyAll() { this.decision = AuthorizationRule.Decision.DENY_ALL; return this; }
        public AuthorizationSpec authenticated() { this.decision = AuthorizationRule.Decision.AUTHENTICATED; return this; }
        public AuthorizationSpec hasRole(String role) { return hasAnyRole(role); }
        public AuthorizationSpec hasAnyRole(String... roles) {
            this.decision = AuthorizationRule.Decision.HAS_ANY_ROLE;
            this.required = roles;
            return this;
        }
        public AuthorizationSpec hasAuthority(String authority) { return hasAnyAuthority(authority); }
        public AuthorizationSpec hasAnyAuthority(String... authorities) {
            this.decision = AuthorizationRule.Decision.HAS_ANY_AUTHORITY;
            this.required = authorities;
            return this;
        }

        boolean isCatchAll() { return catchAll; }

        AuthorizationRule toRule() {
            return new AuthorizationRule(pattern, method, decision, required);
        }
    }

    /** JWT 登录与令牌编码的配置器。 */
    public static final class JwtConfigurer {
        private JwtEncoder encoder;
        private JwtDecoder decoder;
        private AuthenticationManager authenticationManager;
        private String loginUrl = "/login";
        private long tokenTtlSeconds = 3600L;

        public JwtConfigurer encoder(JwtEncoder encoder) { this.encoder = encoder; return this; }
        public JwtConfigurer decoder(JwtDecoder decoder) { this.decoder = decoder; return this; }
        public JwtConfigurer authenticationManager(AuthenticationManager m) { this.authenticationManager = m; return this; }
        public JwtConfigurer loginUrl(String loginUrl) { this.loginUrl = loginUrl; return this; }
        public JwtConfigurer tokenTtl(long seconds) { this.tokenTtlSeconds = seconds; return this; }

        boolean configured() {
            return encoder != null && decoder != null;
        }

        boolean loginEnabled() {
            return authenticationManager != null && configured();
        }
    }
}
