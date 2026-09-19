package cn.jiebaba.summer.security.core;

/**
 * 当前 {@link SecurityContext} 的线程级存储。
 * <p>基于 {@link ThreadLocal}，因此对虚拟线程透明：每个请求运行在各自的虚拟线程上，
 * 拥有各自的 {@code ThreadLocal} 值。web 层在每个请求结束后的 {@code finally} 块中
 * 清理上下文，避免在共享的 carrier 线程之间泄漏。
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

    /** 清除绑定到当前线程的上下文。须在请求结束时调用。 */
    public static void clearContext() {
        HOLDER.remove();
    }

    public static boolean isSet() {
        return HOLDER.get() != null && HOLDER.get().getAuthentication() != null;
    }
}
