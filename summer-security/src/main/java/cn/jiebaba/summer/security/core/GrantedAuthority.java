package cn.jiebaba.summer.security.core;

/**
 * Represents an authority granted to an authenticated principal.
 * <p>Mirrors {@code org.springframework.security.core.GrantedAuthority}.
 * Authorities are plain strings; the convention {@code "ROLE_xxx"} denotes a role
 * (see {@link SimpleGrantedAuthority#roleOf(String)}).
 */
public interface GrantedAuthority {
    String getAuthority();
}
