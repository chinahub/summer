package cn.jiebaba.summer.security.authentication;

import cn.jiebaba.summer.security.core.Authentication;

/**
 * Processes an {@link Authentication} request, returning a fully populated
 * authenticated token (authorities set) or throwing {@link AuthenticationException}.
 * Mirrors {@code org.springframework.security.authentication.AuthenticationManager}.
 */
@FunctionalInterface
public interface AuthenticationManager {
    Authentication authenticate(Authentication authentication) throws AuthenticationException;
}
