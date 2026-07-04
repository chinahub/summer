package cn.jiebaba.summer.security.authentication;

import cn.jiebaba.summer.security.core.Authentication;

/**
 * 处理 {@link Authentication} 请求，返回已填充权限的认证令牌，
 * 或抛出 {@link AuthenticationException}。对应
 * {@code org.springframework.security.authentication.AuthenticationManager}。
 */
@FunctionalInterface
public interface AuthenticationManager {
    Authentication authenticate(Authentication authentication) throws AuthenticationException;
}
