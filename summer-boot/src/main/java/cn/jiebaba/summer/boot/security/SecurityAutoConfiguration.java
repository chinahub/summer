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
import cn.jiebaba.summer.web.bind.HandlerMethodAccessChecker;
import cn.jiebaba.summer.web.bind.HandlerMethodArgumentResolver;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

/**
 * Auto-configures the security layer. Mirrors Spring Security's auto-configuration
 * in spirit. <b>Opt-in</b>: security is activated only when
 * {@code summer.security.enabled=true} (or when the application supplies its own
 * {@link SecurityFilterChain} bean). When disabled, the {@link SecurityFilterChain}
 * has no filters and {@link MethodSecurityEnforcer} is a no-op, so existing
 * applications are completely unaffected.
 *
 * <p>Configuration properties:
 * <pre>
 *   summer.security.enabled=true
 *   summer.security.jwt.secret=...           # &gt;=32 bytes; auto-generated + WARN if absent
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

    @Bean
    public SecurityFilterChain securityFilterChain(Environment env,
                                                   AuthenticationManager authenticationManager,
                                                   JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        boolean enabled = env.getProperty("summer.security.enabled", Boolean.class, false);
        if (!enabled) {
            return new SecurityFilterChain(List.of());
        }
        long ttl = env.getProperty("summer.security.jwt.access-token-ttl", Long.class, 3600L);
        String loginUrl = env.getProperty("summer.security.jwt.login-url", "/login");

        HttpSecurity.AuthorizationSpec anyRequest = HttpSecurity.anyRequest().authenticated();
        return HttpSecurity.security()
                .authorize(anyRequest)
                .jwt(jwt -> jwt
                        .encoder(jwtEncoder)
                        .decoder(jwtDecoder)
                        .authenticationManager(authenticationManager)
                        .loginUrl(loginUrl)
                        .tokenTtl(ttl))
                .build();
    }

    /** Shared HS256 secret: computed once and reused by both encoder and decoder. */
    private byte[] sharedSecret(Environment env) {
        if (cachedSecret == null) {
            synchronized (this) {
                if (cachedSecret == null) cachedSecret = resolveSecret(env);
            }
        }
        return cachedSecret;
    }

    /** Resolve the HS256 secret: configured value, else a random 32-byte key (WARN when enabled). */
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
