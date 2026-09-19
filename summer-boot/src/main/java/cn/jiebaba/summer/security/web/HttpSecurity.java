package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.filter.Filter;

import cn.jiebaba.summer.security.authentication.AuthenticationManager;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.security.web.csrf.CookieCsrfTokenRepository;
import cn.jiebaba.summer.security.web.csrf.CsrfFilter;
import cn.jiebaba.summer.security.web.csrf.CsrfTokenRepository;
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
 *       .csrf(csrf -&gt; csrf.disable())
 *       .build();
 * </pre>
 * 若启用，CSRF 过滤器最先运行，随后是登录过滤器、JWT 认证，最后是授权。
 */
public final class HttpSecurity {

    private final List<AuthorizationRule> rules = new ArrayList<>();
    private AuthorizationRule defaultRule = new AuthorizationRule("/**", null,
            AuthorizationRule.Decision.PERMIT_ALL);
    private JwtConfigurer jwt;
    private CsrfConfigurer csrf;
    private String securityPattern = "/**";
    private HttpMethod securityMethod;
    private int chainOrder = Integer.MAX_VALUE;

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

    /** 配置 CSRF 防护；未调用 {@code csrf()} 时默认不启用。 */
    public HttpSecurity csrf(java.util.function.Consumer<CsrfConfigurer> configurer) {
        this.csrf = new CsrfConfigurer();
        configurer.accept(this.csrf);
        return this;
    }

    /** 指定本链处理的请求路径模式（不限方法）；默认 {@code /**} 匹配所有请求。 */
    public HttpSecurity securityMatcher(String pattern) {
        this.securityPattern = (pattern == null || pattern.isBlank()) ? "/**" : pattern;
        this.securityMethod = null;
        return this;
    }

    /** 指定本链处理的请求方法与路径模式。 */
    public HttpSecurity securityMatcher(HttpMethod method, String pattern) {
        this.securityMethod = method;
        this.securityPattern = (pattern == null || pattern.isBlank()) ? "/**" : pattern;
        return this;
    }

    /** 设置链优先级，值小者优先匹配；未设置时为兜底优先级（最后匹配）。 */
    public HttpSecurity order(int order) {
        this.chainOrder = order;
        return this;
    }

    /**
     * 构建过滤器链。CSRF 过滤器（若启用）置于链首，随后依次为登录、JWT 认证与授权；
     * 未配置 JWT 时仅可能包含 CSRF 过滤器（构建全放行链）。
     *
     * @return 构建得到的安全过滤器链
     */
    public SecurityFilterChain build() {
        List<Filter> filters = new ArrayList<>();
        if (csrf != null && csrf.enabled()) {
            CsrfTokenRepository repository = csrf.repository != null
                    ? csrf.repository : CookieCsrfTokenRepository.withDefaults();
            filters.add(new CsrfFilter(repository));
        }
        if (jwt == null || !jwt.configured()) {
            // 未配置 JWT：构建全放行链（安全实际被禁用），CSRF（若启用）仍保留。
            return new SecurityFilterChain(securityPattern, securityMethod, chainOrder, filters);
        }
        if (jwt.loginEnabled()) {
            filters.add(new JwtLoginFilter(jwt.authenticationManager, jwt.encoder,
                    jwt.tokenTtlSeconds, jwt.refreshTokenTtlSeconds, jwt.loginUrl));
            filters.add(new JwtRefreshFilter(jwt.decoder, jwt.encoder,
                    jwt.tokenTtlSeconds, jwt.refreshTokenTtlSeconds,
                    jwt.refreshUrl, jwt.rotateRefreshToken));
        }
        filters.add(new JwtAuthenticationFilter(jwt.decoder));
        filters.add(new AuthorizationFilter(rules, defaultRule));
        return new SecurityFilterChain(securityPattern, securityMethod, chainOrder, filters);
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
        private long refreshTokenTtlSeconds = 604800L;
        private String refreshUrl = "/refresh";
        private boolean rotateRefreshToken = true;

        public JwtConfigurer encoder(JwtEncoder encoder) { this.encoder = encoder; return this; }
        public JwtConfigurer decoder(JwtDecoder decoder) { this.decoder = decoder; return this; }
        public JwtConfigurer authenticationManager(AuthenticationManager m) { this.authenticationManager = m; return this; }
        public JwtConfigurer loginUrl(String loginUrl) { this.loginUrl = loginUrl; return this; }
        public JwtConfigurer tokenTtl(long seconds) { this.tokenTtlSeconds = seconds; return this; }
        public JwtConfigurer refreshTokenTtl(long seconds) { this.refreshTokenTtlSeconds = seconds; return this; }
        public JwtConfigurer refreshUrl(String refreshUrl) { this.refreshUrl = refreshUrl; return this; }
        public JwtConfigurer rotateRefreshToken(boolean rotate) { this.rotateRefreshToken = rotate; return this; }

        boolean configured() {
            return encoder != null && decoder != null;
        }

        boolean loginEnabled() {
            return authenticationManager != null && configured();
        }
    }

    /** CSRF 防护配置器：可指定令牌仓库，默认使用基于 Cookie 的双重提交实现。 */
    public static final class CsrfConfigurer {
        private CsrfTokenRepository repository;
        private boolean enabled = true;

        public CsrfConfigurer repository(CsrfTokenRepository repository) {
            this.repository = repository;
            return this;
        }

        /** 禁用 CSRF 防护。 */
        public CsrfConfigurer disable() {
            this.enabled = false;
            return this;
        }

        boolean enabled() {
            return enabled;
        }
    }
}
