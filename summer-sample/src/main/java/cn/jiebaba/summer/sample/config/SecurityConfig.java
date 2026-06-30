package cn.jiebaba.summer.sample.config;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.annotation.Primary;
import cn.jiebaba.summer.security.authentication.AuthenticationManager;
import cn.jiebaba.summer.security.crypto.PasswordEncoder;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.security.userdetails.InMemoryUserDetailsManager;
import cn.jiebaba.summer.security.userdetails.User;
import cn.jiebaba.summer.security.userdetails.UserDetailsService;
import cn.jiebaba.summer.security.web.HttpSecurity;
import cn.jiebaba.summer.security.web.SecurityFilterChain;

/**
 * Sample security configuration. Demonstrates the HttpSecurity DSL:
 * <ul>
 *   <li>{@code /admin/**} requires the {@code ADMIN} role (URL-level).</li>
 *   <li>{@code /me} requires authentication.</li>
 *   <li>everything else (including unmatched paths) is permitted, so existing
 *       public routes and 404s behave unchanged.</li>
 * </ul>
 * Provides a {@code @Primary} in-memory user store with BCrypt-encoded passwords
 * (admin/admin123, user/user123), overriding the auto-configured one.
 */
@Configuration
public class SecurityConfig {

    @Bean("sampleUserDetailsService")
    @Primary
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withRoles("admin", encoder.encode("admin123"), "ADMIN", "USER"),
                User.withRoles("user", encoder.encode("user123"), "USER"));
    }

    @Bean("sampleSecurityFilterChain")
    @Primary
    public SecurityFilterChain securityFilterChain(AuthenticationManager authenticationManager,
                                                   JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        return HttpSecurity.security()
                .authorize(
                        HttpSecurity.match("/admin/**").hasRole("ADMIN"),
                        HttpSecurity.match("/me").authenticated(),
                        HttpSecurity.anyRequest().permitAll())
                .jwt(jwt -> jwt
                        .encoder(jwtEncoder)
                        .decoder(jwtDecoder)
                        .authenticationManager(authenticationManager)
                        .loginUrl("/login")
                        .tokenTtl(3600))
                .build();
    }
}
