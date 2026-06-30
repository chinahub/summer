package cn.jiebaba.summer.security.authentication;

import cn.jiebaba.summer.security.core.Authentication;

/**
 * Indicates a class can process a specific {@link Authentication} implementation.
 * Mirrors {@code org.springframework.security.authentication.AuthenticationProvider}.
 */
public interface AuthenticationProvider {
    Authentication authenticate(Authentication authentication) throws AuthenticationException;
    boolean supports(Class<?> authentication);
}
