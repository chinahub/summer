package cn.jiebaba.summer.admin.auth;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.security.web.HttpSecurity;
import cn.jiebaba.summer.security.web.SecurityFilterChain;

/**
 * summer-admin 安全配置：提供自定义 {@link SecurityFilterChain}，
 * 仅放行健康检查、验证码/登录与前端静态资源，其余请求（含全部 /api 管理接口）
 * 需持有效 JWT。JWT 只配置 encoder/decoder，<b>不传 AuthenticationManager</b>，
 * 框架因此不装配内置登录/刷新过滤器，登录完全由自定义 AuthController 接管。
 * 数据库 {@link cn.jiebaba.summer.security.userdetails.UserDetailsService} 由
 * {@link DatabaseUserDetailsService} 以 @Service 提供，自动配置按类型退避。
 */
@Configuration
public class SecurityConfig {

    /**
     * 管理端安全链：health、captcha、login 与静态资源匿名可访问；
     * 任何其他请求必须已通过 JWT 认证过滤器（Bearer token）。
     *
     * @param encoder JWT 编码器（本配置不使用，链上仅需解码侧）
     * @param decoder JWT 解码器
     * @return 安全过滤器链
     */
    @Bean
    public SecurityFilterChain adminSecurityFilterChain(JwtEncoder encoder, JwtDecoder decoder) {
        return HttpSecurity.security()
                .authorize(
                        HttpSecurity.match("/api/health").permitAll(),
                        HttpSecurity.match("/api/auth/captcha").permitAll(),
                        HttpSecurity.match("/api/auth/login").permitAll(),
                        HttpSecurity.match("/").permitAll(),
                        HttpSecurity.match("/index.html").permitAll(),
                        HttpSecurity.match("/assets/**").permitAll(),
                        HttpSecurity.match("/static/**").permitAll(),
                        HttpSecurity.match("/*.js").permitAll(),
                        HttpSecurity.match("/*.css").permitAll(),
                        HttpSecurity.match("/favicon.ico").permitAll(),
                        HttpSecurity.anyRequest().authenticated())
                // 不传 authenticationManager：禁用内置登录/刷新过滤器，登录由 AuthController 负责
                .jwt(jwt -> jwt.encoder(encoder).decoder(decoder))
                .build();
    }
}
