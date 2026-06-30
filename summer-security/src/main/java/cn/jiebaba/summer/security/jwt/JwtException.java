package cn.jiebaba.summer.security.jwt;

import cn.jiebaba.summer.security.authentication.AuthenticationException;

/** Thrown when a JWT cannot be encoded or decoded (malformed, bad signature, expired). */
public class JwtException extends AuthenticationException {
    public JwtException(String message) { super(message); }
    public JwtException(String message, Throwable cause) { super(message, cause); }
}
