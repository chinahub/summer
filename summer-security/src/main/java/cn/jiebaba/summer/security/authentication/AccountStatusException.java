package cn.jiebaba.summer.security.authentication;

/** Thrown when an account is disabled, locked or expired. */
public class AccountStatusException extends AuthenticationException {
    public AccountStatusException(String message) { super(message); }
    public AccountStatusException(String message, Throwable cause) { super(message, cause); }
}
