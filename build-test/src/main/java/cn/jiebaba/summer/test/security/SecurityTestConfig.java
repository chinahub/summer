package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.boot.annotation.SummerBootApplication;
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
import cn.jiebaba.summer.security.web.MethodSecurityEnforcer;
import cn.jiebaba.summer.security.web.SecurityFilterChain;
import cn.jiebaba.summer.web.bind.HandlerMethodAccessChecker;

/**
 * {@link SecurityTestApp} 的安全装配。以 {@code @Primary} 实例覆盖自动配置的 Bean：
 * 内存用户存储（BCrypt 密码）、始终启用的方法级执行器，以及一组 URL 规则——
 * 放行公开路径、对 /admin/** 要求 ADMIN、对 /me 要求认证，其余默认放行
 * （使未匹配路径仍返回 404）。
 */
@Configuration
public class SecurityTestConfig {

    @Bean("testUserDetailsService")
    @Primary
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withRoles("admin", encoder.encode("admin123"), "ADMIN", "USER"),
                User.withRoles("user", encoder.encode("user123"), "USER"));
    }

    @Bean("testAccessChecker")
    @Primary
    public HandlerMethodAccessChecker accessChecker() {
        return new MethodSecurityEnforcer(true);
    }

    @Bean("testSecurityFilterChain")
    @Primary
    public SecurityFilterChain securityFilterChain(AuthenticationManager authenticationManager,
                                                   JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        return HttpSecurity.security()
                .authorize(
                        HttpSecurity.match("/public/**").permitAll(),
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
