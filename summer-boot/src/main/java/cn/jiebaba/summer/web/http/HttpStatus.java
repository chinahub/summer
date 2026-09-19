package cn.jiebaba.summer.web.http;

public enum HttpStatus {
    CONTINUE(100), OK(200), CREATED(201), ACCEPTED(202), NO_CONTENT(204),
    MOVED_PERMANENTLY(301), FOUND(302), NOT_MODIFIED(304),
    BAD_REQUEST(400), UNAUTHORIZED(401), FORBIDDEN(403), NOT_FOUND(404), METHOD_NOT_ALLOWED(405),
    CONFLICT(409), UNSUPPORTED_MEDIA_TYPE(415), UNPROCESSABLE_ENTITY(422),
    INTERNAL_SERVER_ERROR(500), NOT_IMPLEMENTED(501), SERVICE_UNAVAILABLE(503);

    private final int code;

    HttpStatus(int code) { this.code = code; }

    public int code() { return code; }

    public String reason() { return name().replace('_', ' '); }

    public static HttpStatus valueOf(int code) {
        for (HttpStatus s : values()) {
            if (s.code == code) return s;
        }
        return INTERNAL_SERVER_ERROR;
    }
}
