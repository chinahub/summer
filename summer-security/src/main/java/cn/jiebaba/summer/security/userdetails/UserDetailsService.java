package cn.jiebaba.summer.security.userdetails;

/**
 * Loads user-specific data. Mirrors {@code org.springframework.security.core.userdetails.UserDetailsService}.
 */
@FunctionalInterface
public interface UserDetailsService {
    UserDetails loadUserByUsername(String username) throws cn.jiebaba.summer.security.authentication.AuthenticationException;
}
