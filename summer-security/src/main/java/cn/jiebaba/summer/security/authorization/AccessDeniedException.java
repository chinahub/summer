package cn.jiebaba.summer.security.authorization;

import cn.jiebaba.summer.security.authentication.AuthenticationException;

/**
 * 当已认证主体缺少访问资源所需权限时抛出。映射为 HTTP 403。继承自
 * {@link AuthenticationException}，使 Web 层可统一转换安全失败，
 * 同时调用方可通过 {@code instanceof} 区分 401（未认证）与 403（禁止访问）。
 */
public class AccessDeniedException extends AuthenticationException {
    public AccessDeniedException(String message) { super(message); }
}
