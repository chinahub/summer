package cn.jiebaba.summer.security.userdetails;

import cn.jiebaba.summer.security.core.GrantedAuthority;
import cn.jiebaba.summer.security.core.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** 默认的不可变 {@link UserDetails} 实现。 */
public final class User implements UserDetails {

    private final String username;
    private final String password;
    private final List<GrantedAuthority> authorities;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;
    private final boolean enabled;

    public User(String username, String password, Collection<? extends GrantedAuthority> authorities) {
        this(username, password, authorities, true, true, true, true);
    }

    public User(String username, String password, Collection<? extends GrantedAuthority> authorities,
                boolean accountNonExpired, boolean accountNonLocked,
                boolean credentialsNonExpired, boolean enabled) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        this.username = username;
        this.password = password;
        this.authorities = List.copyOf(authorities);
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
        this.enabled = enabled;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return accountNonExpired; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return credentialsNonExpired; }
    @Override public boolean isEnabled() { return enabled; }

    @Override
    public String toString() { return username; }

    /** 用于内存用户的便捷构造器，接受裸角色名。 */
    public static User withRoles(String username, String password, String... roles) {
        return withRoles(username, password, true, roles);
    }

    public static User withRoles(String username, String password, boolean enabled, String... roles) {
        return new User(username, password,
                Arrays.stream(roles).map(SimpleGrantedAuthority::roleOf).toList(),
                true, true, true, enabled);
    }
}
