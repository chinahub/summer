package cn.jiebaba.summer.admin.common.api;

/**
 * 统一 API 响应体：{@code {code, msg, data}}。成功 code 固定 200，
 * 失败使用业务状态码（如 400/401/403/404/409/500）。
 *
 * @param <T> 数据负载类型
 */
public class R<T> {

    public static final int CODE_SUCCESS = 200;
    private static final String MSG_SUCCESS = "操作成功";

    private final int code;
    private final String msg;
    private final T data;

    private R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }

    public static <T> R<T> ok(T data) {
        return new R<>(CODE_SUCCESS, MSG_SUCCESS, data);
    }

    public static R<Void> ok() {
        return new R<>(CODE_SUCCESS, MSG_SUCCESS, null);
    }

    public static R<Void> failure(int code, String msg) {
        return new R<>(code, msg, null);
    }
}
