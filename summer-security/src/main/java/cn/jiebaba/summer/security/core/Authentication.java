package cn.jiebaba.summer.security.core;

import java.util.Collection;

/**
 * Represents the token for an authentication request or for an authenticated
 * principal once the request has been processed.
 * <p>Mirrors {@code org.springframework.security.core.Authentication}.
 */
public interface Authentication {
    /** The identity of the principal being authenticated (often a username or {@code UserDetails}). */
    Object getPrincipal();

    /** The credentials proving the principal's identity (e.g. a password or raw token). */
    Object getCredentials();

    /** The authorities granted to the principal; empty until authenticated. */
    Collection<? extends GrantedAuthority> getAuthorities();

    /** Extra details about the authentication request (e.g. remote address). */
    Object getDetails();

    /** {@code true} once the credentials have been successfully verified. */
    boolean isAuthenticated();

    void setAuthenticated(boolean authenticated) throws IllegalArgumentException;

    /** Convenience: the principal rendered as a name (username). */
    default String getName() {
        Object principal = getPrincipal();
        return principal == null ? "" : principal.toString();
    }
}
