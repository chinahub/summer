package cn.jiebaba.summer.security.authentication;

/** 当提供的凭据无效时抛出。 */
public class BadCredentialsException extends AuthenticationException {
    public BadCredentialsException(String message) { super(message); }
    public BadCredentialsException(String message, Throwable cause) { super(message, cause); }
}
