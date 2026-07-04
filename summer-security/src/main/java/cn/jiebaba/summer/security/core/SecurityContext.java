package cn.jiebaba.summer.security.core;

/**
 * 持有与当前执行线程关联的 {@link Authentication}。
 * 对应 {@code org.springframework.security.core.context.SecurityContext}。
 */
public final class SecurityContext {
    private final Authentication authentication;

    public SecurityContext(Authentication authentication) {
        this.authentication = authentication;
    }

    public Authentication getAuthentication() { return authentication; }

    @Override
    public String toString() {
        return "SecurityContext[" + (authentication == null ? "empty" : authentication.getName()) + "]";
    }
}
