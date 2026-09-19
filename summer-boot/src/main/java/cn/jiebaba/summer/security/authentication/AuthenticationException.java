package cn.jiebaba.summer.security.authentication;

/**
 * 认证失败的基类异常。在 Web 层映射为 HTTP 401。
 */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) { super(message); }
    public AuthenticationException(String message, Throwable cause) { super(message, cause); }
}
