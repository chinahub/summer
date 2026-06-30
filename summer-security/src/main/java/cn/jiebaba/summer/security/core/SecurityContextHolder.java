package cn.jiebaba.summer.security.core;

/**
 * Thread-local store for the current {@link SecurityContext}.
 * <p>Built on {@link ThreadLocal} and therefore transparent to virtual threads: each
 * request runs on its own virtual thread, which has its own {@code ThreadLocal} value.
 * The web layer clears the context in a {@code finally} block after each request to
 * avoid leakage between pooled carrier threads.
 */
public final class SecurityContextHolder {

    private static final ThreadLocal<SecurityContext> HOLDER = new ThreadLocal<>();

    private SecurityContextHolder() {}

    public static SecurityContext getContext() {
        SecurityContext ctx = HOLDER.get();
        if (ctx == null) {
            ctx = new SecurityContext(null);
            HOLDER.set(ctx);
        }
        return ctx;
    }

    public static void setContext(SecurityContext ctx) {
        HOLDER.set(ctx);
    }

    public static Authentication getAuthentication() {
        return HOLDER.get() == null ? null : HOLDER.get().getAuthentication();
    }

    /** Clears the context bound to the current thread. Must be called at request end. */
    public static void clearContext() {
        HOLDER.remove();
    }

    public static boolean isSet() {
        return HOLDER.get() != null && HOLDER.get().getAuthentication() != null;
    }
}
