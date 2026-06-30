package cn.jiebaba.summer.security.userdetails;

import cn.jiebaba.summer.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Core user information retrieved by a {@link UserDetailsService}.
 * Mirrors {@code org.springframework.security.core.userdetails.UserDetails}.
 */
public interface UserDetails {
    Collection<? extends GrantedAuthority> getAuthorities();
    String getPassword();
    String getUsername();
    default boolean isAccountNonExpired() { return true; }
    default boolean isAccountNonLocked() { return true; }
    default boolean isCredentialsNonExpired() { return true; }
    default boolean isEnabled() { return true; }
}
