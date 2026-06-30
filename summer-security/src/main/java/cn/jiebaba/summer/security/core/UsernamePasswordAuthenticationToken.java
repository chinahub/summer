package cn.jiebaba.summer.security.core;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An {@link Authentication} for username/password credentials.
 * <p>Two states: <b>unauthenticated</b> (principal=username, credentials=password,
 * authorities empty) used as input to {@code AuthenticationManager}; and
 * <b>authenticated</b> (principal={@code UserDetails}, credentials nulled,
 * authorities populated) returned after successful verification.
 */
public class UsernamePasswordAuthenticationToken implements Authentication {

    private final Object principal;
    private final Object credentials;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean authenticated;
    private Object details;

    /** Unauthenticated request token: principal is the username, credentials the raw password. */
    public UsernamePasswordAuthenticationToken(Object principal, Object credentials) {
        this(principal, credentials, Collections.emptyList(), false);
    }

    /** Authenticated token: principal is the verified {@code UserDetails}, authorities populated. */
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
