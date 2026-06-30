package cn.jiebaba.summer.security.core;

/**
 * Holds the {@link Authentication} associated with the current thread of execution.
 * Mirrors {@code org.springframework.security.core.context.SecurityContext}.
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
