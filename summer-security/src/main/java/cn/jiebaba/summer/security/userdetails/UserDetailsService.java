package cn.jiebaba.summer.security.userdetails;

/**
 * 加载用户专属数据。对应 {@code org.springframework.security.core.userdetails.UserDetailsService}。
 */
@FunctionalInterface
public interface UserDetailsService {
    UserDetails loadUserByUsername(String username) throws cn.jiebaba.summer.security.authentication.AuthenticationException;
}
