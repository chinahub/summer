package cn.jiebaba.summer.security.authentication;

/** Thrown when supplied credentials are invalid. */
public class BadCredentialsException extends AuthenticationException {
    public BadCredentialsException(String message) { super(message); }
    public BadCredentialsException(String message, Throwable cause) { super(message, cause); }
}
