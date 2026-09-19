package cn.jiebaba.summer.security.core;

/**
 * 表示授予已认证 principal 的权限。
 * <p>对应 {@code org.springframework.security.core.GrantedAuthority}。
 * 权限是普通字符串；约定 {@code "ROLE_xxx"} 表示一个角色
 * （见 {@link SimpleGrantedAuthority#roleOf(String)}）。
 */
public interface GrantedAuthority {
    String getAuthority();
}
