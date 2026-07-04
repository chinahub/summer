package cn.jiebaba.summer.security.core;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 用于用户名/密码凭据的 {@link Authentication}。
 * <p>两种状态：<b>未认证</b>（principal=username、credentials=password、authorities 为空），
 * 作为 {@code AuthenticationManager} 的输入；以及 <b>已认证</b>
 * （principal={@code UserDetails}、credentials 置空、authorities 已填充），在验证成功后返回。
 */
public class UsernamePasswordAuthenticationToken implements Authentication {

    private final Object principal;
    private final Object credentials;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean authenticated;
    private Object details;

    /** 未认证请求令牌：主体为用户名，凭据为原始密码。 */
    public UsernamePasswordAuthenticationToken(Object principal, Object credentials) {
        this(principal, credentials, Collections.emptyList(), false);
    }

    /** 已认证令牌：主体为已校验的 {@code UserDetails}，权限已填充。 */
    public UsernamePasswordAuthenticationToken(Object principal, Object credentials,
                                               Collection<? extends GrantedAuthority> authorities) {
        this(principal, credentials, authorities, true);
    }

    private UsernamePasswordAuthenticationToken(Object principal, Object credentials,
                                                Collection<? extends GrantedAuthority> authorities,
                                                boolean authenticated) {
        this.principal = principal;
        this.credentials = credentials;
        this.authorities = authorities == null ? Collections.emptyList() : List.copyOf(authorities);
        this.authenticated = authenticated;
    }

    @Override
    public Object getPrincipal() { return principal; }

    @Override
    public Object getCredentials() { return credentials; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public Object getDetails() { return details; }
    public void setDetails(Object details) { this.details = details; }

    @Override
    public boolean isAuthenticated() { return authenticated; }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException(
                    "Cannot set this token to trusted; use the authenticated constructor");
        }
    }

    @Override
    public String getName() {
        if (principal == null) return "";
        return principal.toString();
    }
}
