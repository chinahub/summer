package cn.jiebaba.summer.web.websocket;

/** Reason a WebSocket connection was closed (RFC 6455 section 7.4 status codes). */
public record CloseReason(int code, String reason) {
    public static final int NORMAL = 1000;
    public static final int GOING_AWAY = 1001;
    public static final int PROTOCOL_ERROR = 1002;
    public static final int UNSUPPORTED_DATA = 1003;
    public static final int INTERNAL_ERROR = 1011;

    public static CloseReason normal() { return new CloseReason(NORMAL, "normal closure"); }
    public static CloseReason of(int code, String reason) { return new CloseReason(code, reason); }
}