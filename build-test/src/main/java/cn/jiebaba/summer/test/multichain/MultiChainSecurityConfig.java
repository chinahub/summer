package cn.jiebaba.summer.test.multichain;

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
 * 多链装配：定义两条 {@link SecurityFilterChain}，验证按 {@code securityMatcher} 分流。
 * <ul>
 *   <li>{@code apiChain}（order=1，匹配 {@code /api/**}）：JWT 登录 {@code /api/login}，其余需认证。</li>
 *   <li>{@code publicChain}（order=2，匹配 {@code /**}）：全放行，使非 {@code /api} 路径无需认证即可访问。</li>
 * </ul>
 * 由于用户自定义了链，{@code SecurityAutoConfiguration} 不再构建默认链。
 */
@Configuration
public class MultiChainSecurityConfig {

    @Bean("mcUserDetailsService")
    @Primary
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withRoles("admin", encoder.encode("admin123"), "ADMIN", "USER"),
                User.withRoles("user", encoder.encode("user123"), "USER"));
    }

    /** {@code /api/**} 受保护链：登录签发 JWT，其余路径要求认证。 */
    @Bean
    public SecurityFilterChain apiChain(AuthenticationManager authenticationManager,
                                        JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        return HttpSecurity.security()
                .securityMatcher("/api/**").order(1)
                .authorize(HttpSecurity.anyRequest().authenticated())
                .jwt(jwt -> jwt
                        .encoder(jwtEncoder)
                        .decoder(jwtDecoder)
                        .authenticationManager(authenticationManager)
                        .loginUrl("/api/login")
                        .tokenTtl(3600))
                .build();
    }

    /** {@code /**} 兜底链：全放行（未配置 JWT，链不含过滤器），使非 {@code /api} 路径直接进入路由。 */
    @Bean
    public SecurityFilterChain publicChain() {
        return HttpSecurity.security()
                .securityMatcher("/**").order(2)
                .authorize(HttpSecurity.anyRequest().permitAll())
                .build();
    }
}
