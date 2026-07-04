package cn.jiebaba.summer.security.authentication;

import cn.jiebaba.summer.security.core.Authentication;

/**
 * 表示该类可处理特定的 {@link Authentication} 实现。对应
 * {@code org.springframework.security.authentication.AuthenticationProvider}。
 */
public interface AuthenticationProvider {
    Authentication authenticate(Authentication authentication) throws AuthenticationException;
    boolean supports(Class<?> authentication);
}
