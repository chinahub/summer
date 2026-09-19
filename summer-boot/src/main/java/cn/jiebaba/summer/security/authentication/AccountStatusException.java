package cn.jiebaba.summer.security.authentication;

/** 当账号被禁用、锁定或过期时抛出。 */
public class AccountStatusException extends AuthenticationException {
    public AccountStatusException(String message) { super(message); }
    public AccountStatusException(String message, Throwable cause) { super(message, cause); }
}
