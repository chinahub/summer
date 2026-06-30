package cn.jiebaba.summer.web.server;

/**
 * Exception carrying an HTTP status and reason, translated by the dispatcher into
 * a matching response. Generic (not security-specific); lets higher layers (e.g.
 * security) signal 401/403 without {@code summer-web} depending on them.
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
