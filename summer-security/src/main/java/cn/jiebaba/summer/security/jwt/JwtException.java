package cn.jiebaba.summer.security.jwt;

import cn.jiebaba.summer.security.authentication.AuthenticationException;

/** 当 JWT 无法编码或解码（格式错误、签名错误、过期）时抛出。 */
public class JwtException extends AuthenticationException {
    public JwtException(String message) { super(message); }
    public JwtException(String message, Throwable cause) { super(message, cause); }
}
