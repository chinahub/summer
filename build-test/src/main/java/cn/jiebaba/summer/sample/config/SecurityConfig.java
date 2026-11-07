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
 * 示例安全配置，演示 HttpSecurity DSL：
 * <ul>
 *   <li>{@code /admin/**} 需要 {@code ADMIN} 角色（URL 级别）。</li>
 *   <li>{@code /me} 需要登录认证。</li>
 *   <li>其余路径（含未匹配路径）一律放行，使现有公开路由与 404 行为保持不变。</li>
 * </ul>
 * 提供一个 {@code @Primary} 的内存用户存储，密码以 BCrypt 加密
 * （admin/admin123、user/user123），覆盖自动配置的默认实现。
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
