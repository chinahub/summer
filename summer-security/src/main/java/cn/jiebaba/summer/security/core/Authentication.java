package cn.jiebaba.summer.security.core;

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

    /** 便捷方法：将主体呈现为名称（用户名）。 */
    default String getName() {
        Object principal = getPrincipal();
        return principal == null ? "" : principal.toString();
    }
}
