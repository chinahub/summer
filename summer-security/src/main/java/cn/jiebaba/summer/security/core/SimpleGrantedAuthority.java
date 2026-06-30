package cn.jiebaba.summer.security.core;

import java.util.Objects;

/** Default {@link GrantedAuthority} backed by a single authority string. */
public final class SimpleGrantedAuthority implements GrantedAuthority {

    /** Prefix marking a role authority, e.g. {@code ROLE_ADMIN}. */
    public static final String ROLE_PREFIX = "ROLE_";

    private final String authority;

    public SimpleGrantedAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            throw new IllegalArgumentException("Authority must not be blank");
        }
        this.authority = authority;
    }

    /** Wraps a bare role name (e.g. {@code "ADMIN"}) into {@code "ROLE_ADMIN"}. */
    public static SimpleGrantedAuthority roleOf(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role must not be blank");
        }
        return new SimpleGrantedAuthority(role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role);
    }

    @Override
    public String getAuthority() {
        return authority;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof GrantedAuthority g) && Objects.equals(authority, g.getAuthority());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(authority);
    }

    @Override
    public String toString() {
        return authority;
    }
}
