package cn.jiebaba.summer.security.authorization;

import cn.jiebaba.summer.security.authentication.AuthenticationException;

/**
 * Thrown when an authenticated principal lacks the authority required to access
 * a resource. Maps to HTTP 403. Subclasses {@link AuthenticationException} so the
 * web layer can uniformly translate security failures, while callers can
 * distinguish 401 (unauthenticated) from 403 (forbidden) via {@code instanceof}.
 */
public class AccessDeniedException extends AuthenticationException {
    public AccessDeniedException(String message) { super(message); }
}
