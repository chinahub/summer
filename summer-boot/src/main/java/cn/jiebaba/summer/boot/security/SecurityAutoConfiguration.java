package cn.jiebaba.summer.boot.security;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.security.authentication.AuthenticationManager;
import cn.jiebaba.summer.security.authentication.DaoAuthenticationProvider;
import cn.jiebaba.summer.security.authentication.ProviderManager;
import cn.jiebaba.summer.security.crypto.BCryptPasswordEncoder;
import cn.jiebaba.summer.security.crypto.PasswordEncoder;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.security.userdetails.InMemoryUserDetailsManager;
import cn.jiebaba.summer.security.userdetails.UserDetailsService;
import cn.jiebaba.summer.security.web.AuthenticationPrincipalArgumentResolver;
import cn.jiebaba.summer.security.web.HttpSecurity;
import cn.jiebaba.summer.security.web.MethodSecurityEnforcer;
import cn.jiebaba.summer.security.web.SecurityFilterChain;
import cn.jiebaba.summer.security.web.csrf.CookieCsrfTokenRepository;
import cn.jiebaba.summer.security.web.csrf.CsrfProperties;
import cn.jiebaba.summer.web.bind.HandlerMethodAccessChecker;
import cn.jiebaba.summer.web.bind.HandlerMethodArgumentResolver;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

/**
 * 自动配置安全层，在理念上借鉴 Spring Security 的自动配置。
 * <b>可选启用</b>：仅当 {@code summer.security.enabled=true}
 * （或应用自行提供 {@link SecurityFilterChain} Bean）时才激活安全功能。
 * 禁用时，{@link SecurityFilterChain} 不含任何过滤器，{@link MethodSecurityEnforcer}
 * 为空操作，因此对现有应用完全没有影响。
 *
 * <p>配置属性：
 * <pre>
 *   summer.security.enabled=true
 *   summer.security.jwt.secret=...           # &gt;=32 字节；缺失时自动生成并告警
 *   summer.security.jwt.access-token-ttl=3600
 *   summer.security.jwt.login-url=/login
 *   summer.security.password.bcrypt.strength=10
 *   summer.security.users.&lt;name&gt;.password=$2a$...
 *   summer.security.users.&lt;name&gt;.roles=ADMIN,USER
 * </pre>
 */
@Configuration
public class SecurityAutoConfiguration {

    private static final Logger LOG = Logger.getLogger(SecurityAutoConfiguration.class.getName());

    private volatile byte[] cachedSecret;

    @Bean
    public PasswordEncoder passwordEncoder(Environment env) {
        int strength = env.getProperty("summer.security.password.bcrypt.strength", Integer.class, 10);
        return new BCryptPasswordEncoder(strength);
    }

    @Bean
    public UserDetailsService userDetailsService(Environment env) {
        return InMemoryUserDetailsManager.fromEnvironment(env.all());
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        return new DaoAuthenticationProvider(userDetailsService, passwordEncoder);
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(List.of(provider));
    }

    @Bean
    public JwtEncoder jwtEncoder(Environment env) {
        return new JwtEncoder(sharedSecret(env));
    }

    @Bean
    public JwtDecoder jwtDecoder(Environment env) {
        return new JwtDecoder(sharedSecret(env));
    }

    @Bean
    public HandlerMethodAccessChecker accessChecker(Environment env) {
        boolean enabled = env.getProperty("summer.security.enabled", Boolean.class, false);
        return new MethodSecurityEnforcer(enabled);
    }

    @Bean
    public HandlerMethodArgumentResolver authenticationPrincipalResolver(UserDetailsService userDetailsService) {
        return new AuthenticationPrincipalArgumentResolver(userDetailsService);
    }

    /** 绑定 summer.security.csrf.* 配置项为 CsrfProperties。 */
    @Bean
    public CsrfProperties csrfProperties(Environment env) {
        return CsrfProperties.from(env);
    }

    /**
     * 构建安全过滤器链。当 {@code summer.security.enabled} 与 {@code summer.security.csrf.enabled}
     * 均为 false 时返回空过滤器链；否则分别按需装配 CSRF 与 JWT（登录/认证/授权）过滤器。
     * CSRF 过滤器置于链首，先于认证执行。
     *
     * @param env                  环境配置
     * @param csrfProperties       CSRF 配置
     * @param authenticationManager 认证管理器
     * @param jwtEncoder           JWT 编码器
     * @param jwtDecoder           JWT 解码器
     * @return 安全过滤器链
     */
    public static SecurityFilterChain buildDefaultSecurityFilterChain(Environment env,
                                                   CsrfProperties csrfProperties,
                                                   AuthenticationManager authenticationManager,
                                                   JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        boolean securityEnabled = env.getProperty("summer.security.enabled", Boolean.class, false);
        boolean csrfEnabled = csrfProperties.enabled();
        if (!securityEnabled && !csrfEnabled) {
            return new SecurityFilterChain(List.of());
        }
        HttpSecurity http = HttpSecurity.security();
        if (csrfEnabled) {
            http.csrf(csrf -> csrf.repository(CookieCsrfTokenRepository.from(csrfProperties)));
        }
        if (securityEnabled) {
            long ttl = env.getProperty("summer.security.jwt.access-token-ttl", Long.class, 3600L);
            String loginUrl = env.getProperty("summer.security.jwt.login-url", "/login");
            long refreshTtl = env.getProperty("summer.security.jwt.refresh-token-ttl", Long.class, 604800L);
            String refreshUrl = env.getProperty("summer.security.jwt.refresh-url", "/refresh");
            boolean rotate = env.getProperty("summer.security.jwt.rotate-refresh-token", Boolean.class, true);
            http.authorize(HttpSecurity.anyRequest().authenticated())
                    .jwt(jwt -> jwt
                            .encoder(jwtEncoder)
                            .decoder(jwtDecoder)
                            .authenticationManager(authenticationManager)
                            .loginUrl(loginUrl)
                            .tokenTtl(ttl)
                            .refreshTokenTtl(refreshTtl)
                            .refreshUrl(refreshUrl)
                            .rotateRefreshToken(rotate));
        }
        return http.build();
    }

    /** 共享的 HS256 密钥：仅计算一次，编码器与解码器共用。 */
    private byte[] sharedSecret(Environment env) {
        if (cachedSecret == null) {
            synchronized (this) {
                if (cachedSecret == null) cachedSecret = resolveSecret(env);
            }
        }
        return cachedSecret;
    }

    /** 解析 HS256 密钥：取配置值，否则生成随机 32 字节密钥（启用时告警）。 */
    private byte[] resolveSecret(Environment env) {
        String secret = env.getProperty("summer.security.jwt.secret");
        if (secret != null && !secret.isBlank()) {
            byte[] bytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("summer.security.jwt.secret must be at least 32 bytes for HS256");
            }
            return bytes;
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        boolean enabled = env.getProperty("summer.security.enabled", Boolean.class, false);
        if (enabled) {
            LOG.warning("summer.security.jwt.secret not set; generated an ephemeral 32-byte key ("
                    + Base64.getEncoder().encodeToString(random) + "). "
                    + "Set summer.security.jwt.secret for stable tokens across restarts.");
        }
        return random;
    }
}
