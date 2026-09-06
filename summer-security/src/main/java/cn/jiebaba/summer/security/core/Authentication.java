package cn.jiebaba.summer.security.core;

import cn.jiebaba.summer.security.userdetails.UserDetails;

import java.util.Collection;

/**
 * 表示认证请求的令牌，或在请求处理后表示已认证主体。
 * <p>对应 {@code org.springframework.security.core.Authentication}。
 */
public interface Authentication {
    /** 正在认证的主体的身份（通常是用户名或 {@code UserDetails}）。 */
    Object getPrincipal();

    /** 证明主体身份的凭据（如密码或原始令牌）。 */
    Object getCredentials();

    /** 授予主体的权限；认证前为空。 */
    Collection<? extends GrantedAuthority> getAuthorities();

    /** 认证请求的附加细节（如远程地址）。 */
    Object getDetails();

    /** 凭据校验成功后为 {@code true}。 */
    boolean isAuthenticated();

    void setAuthenticated(boolean authenticated) throws IllegalArgumentException;

    /**
     * 便捷方法：将主体呈现为名称（用户名）。
     * 主体为 {@link UserDetails} 时返回其 {@code getUsername()}（JWT 签发等场景依赖）；
     * 否则返回其 {@code toString()}。
     */
    default String getName() {
        Object principal = getPrincipal();
        if (principal instanceof UserDetails ud) return ud.getUsername();
        return principal == null ? "" : principal.toString();
    }
}
