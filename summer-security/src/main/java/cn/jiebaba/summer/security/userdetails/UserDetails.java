package cn.jiebaba.summer.security.userdetails;

import cn.jiebaba.summer.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * 由 {@link UserDetailsService} 获取的核心用户信息。
 * 对应 {@code org.springframework.security.core.userdetails.UserDetails}。
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
