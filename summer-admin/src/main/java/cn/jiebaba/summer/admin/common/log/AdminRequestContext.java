package cn.jiebaba.summer.admin.common.log;

import cn.jiebaba.summer.web.http.WebRequest;

/**
 * 管理端请求上下文：以 {@link ThreadLocal} 保存当前请求的 {@link WebRequest}，
 * 供操作日志切面读取 URL/请求方式/IP。对虚拟线程透明（每个请求独立虚拟线程），
 * 由 {@link AdminRequestContextFilter} 在请求结束时 finally 清理，绝不跨请求泄漏。
 */
public final class AdminRequestContext {

    private static final ThreadLocal<WebRequest> CURRENT = new ThreadLocal<>();

    private AdminRequestContext() {
    }

    /** 绑定当前请求的 WebRequest（过滤器入口调用）。 */
    public static void set(WebRequest request) {
        CURRENT.set(request);
    }

    /** 清除绑定（过滤器 finally 调用，必须执行）。 */
    public static void clear() {
        CURRENT.remove();
    }

    /** 当前请求上下文；非 Web 调用（如定时任务、单测）返回 null。 */
    public static WebRequest current() {
        return CURRENT.get();
    }

    /** 当前请求路径；无上下文返回空串。 */
    public static String path() {
        WebRequest request = CURRENT.get();
        return request == null || request.path() == null ? "" : request.path();
    }

    /** 当前请求方式（GET/POST/...）；无上下文返回空串。 */
    public static String method() {
        WebRequest request = CURRENT.get();
        return request == null || request.method() == null ? "" : request.method().name();
    }

    /** 当前请求远端地址；无上下文返回空串。 */
    public static String remoteAddress() {
        WebRequest request = CURRENT.get();
        return request == null || request.remoteAddress() == null ? "" : request.remoteAddress();
    }
}
