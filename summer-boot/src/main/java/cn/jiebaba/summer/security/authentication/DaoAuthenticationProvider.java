package cn.jiebaba.summer.security.authentication;

import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.GrantedAuthority;
import cn.jiebaba.summer.security.core.UsernamePasswordAuthenticationToken;
import cn.jiebaba.summer.security.crypto.PasswordEncoder;
import cn.jiebaba.summer.security.userdetails.UserDetails;
import cn.jiebaba.summer.security.userdetails.UserDetailsService;

import java.util.Collection;

/**
 * 通过 {@link UserDetailsService} 加载 {@link UserDetails} 并用 {@link PasswordEncoder}
 * 校验密码，从而认证 {@link UsernamePasswordAuthenticationToken}。对应 Spring 的
 * {@code DaoAuthenticationProvider}。
 */
public class DaoAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public DaoAuthenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    /**
     * 认证用户名密码令牌：按用户名加载用户、校验账号状态与密码，成功则返回带权限的已认证令牌。
     */
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!supports(authentication.getClass())) {
            throw new AuthenticationException("Unsupported authentication: " + authentication.getClass().getName());
        }
        String username = authentication.getName();
        UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(username);
        } catch (AuthenticationException e) {
            // 避免泄露用户名是否存在；将查找失败视为凭据错误。
            throw new BadCredentialsException("Bad credentials");
        }
        if (!user.isEnabled()) {
            throw new AccountStatusException("User account is disabled: " + username);
        }
        if (!user.isAccountNonLocked()) {
            throw new AccountStatusException("User account is locked: " + username);
        }
        if (!user.isAccountNonExpired()) {
            throw new AccountStatusException("User account is expired: " + username);
        }
        if (!user.isCredentialsNonExpired()) {
            throw new AccountStatusException("User credentials have expired: " + username);
        }

        Object presented = authentication.getCredentials();
        String rawPassword = presented == null ? null : presented.toString();
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("Bad credentials");
        }

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
        UsernamePasswordAuthenticationToken result =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        result.setDetails(authentication.getDetails());
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
