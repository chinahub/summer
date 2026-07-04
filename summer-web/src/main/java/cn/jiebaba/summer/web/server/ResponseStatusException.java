package cn.jiebaba.summer.web.server;

/**
 * 携带 HTTP 状态码与原因的异常，由 dispatcher 转换为对应的响应。它是通用的
 * （非 security 专属）；使上层（如 security）能表达 401/403，而无需 {@code summer-web}
 * 依赖它们。
 */
public class ResponseStatusException extends RuntimeException {

    private final int status;
    private final String reason;

    public ResponseStatusException(int status, String reason) {
        super(reason == null ? "HTTP " + status : status + " " + reason);
        this.status = status;
        this.reason = reason;
    }

    public ResponseStatusException(int status, String reason, Throwable cause) {
        super(reason == null ? "HTTP " + status : status + " " + reason, cause);
        this.status = status;
        this.reason = reason;
    }

    public int status() { return status; }
    public String reason() { return reason; }
}
