package cn.jiebaba.summer.security.authentication;

/**
 * Base exception for authentication failures. Maps to HTTP 401 in the web layer.
 */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) { super(message); }
    public AuthenticationException(String message, Throwable cause) { super(message, cause); }
}
