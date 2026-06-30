package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.filter.Filter;

import cn.jiebaba.summer.security.authentication.AuthenticationManager;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.web.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for a {@link SecurityFilterChain}. Mirrors Spring Security's
 * {@code HttpSecurity} DSL in spirit (without SpEL):
 * <pre>
 *   http.authorize(
 *         match("/public/**").permitAll(),
 *         match("/admin/**").hasRole("ADMIN"),
 *         anyRequest().authenticated())
 *       .jwt(jwt -&gt; jwt.loginUrl("/login").tokenTtl(3600))
 *       .build();
 * </pre>
 * The login filter runs first, then JWT authentication, then authorization.
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

    /** Add authorization rules; the last entry is treated as the catch-all default. */
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

    /** Build the chain. Requires a {@link JwtConfigurer} (with encoder/decoder/manager) to be set. */
    public SecurityFilterChain build() {
        List<Filter> filters = new ArrayList<>();
        if (jwt == null || !jwt.configured()) {
            // No JWT configured: build a permit-all chain (security effectively disabled).
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

    // ---- rule specs -------------------------------------------------------

    public static AuthorizationSpec match(String pattern) {
        return new AuthorizationSpec(pattern, null, false);
    }

    public static AuthorizationSpec match(HttpMethod method, String pattern) {
        return new AuthorizationSpec(pattern, method, false);
    }

    public static AuthorizationSpec anyRequest() {
        return new AuthorizationSpec("/**", null, true);
    }

    /** A spec describing one URL rule and its decision. */
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

    /** Configurer for JWT login + token encoding. */
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
