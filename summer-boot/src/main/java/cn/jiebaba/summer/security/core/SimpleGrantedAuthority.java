package cn.jiebaba.summer.security.core;

import java.util.Objects;

/** 由单个权限字符串支撑的默认 {@link GrantedAuthority} 实现。 */
public final class SimpleGrantedAuthority implements GrantedAuthority {

    /** 标记角色权限的前缀，如 {@code ROLE_ADMIN}。 */
    public static final String ROLE_PREFIX = "ROLE_";

    private final String authority;

    public SimpleGrantedAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            throw new IllegalArgumentException("Authority must not be blank");
        }
        this.authority = authority;
    }

    /** 将裸角色名（如 {@code "ADMIN"}）包装为 {@code "ROLE_ADMIN"}。 */
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
