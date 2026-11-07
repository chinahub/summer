package cn.jiebaba.summer.admin.common.exception;

/**
 * 业务异常：携带对外可安全展示的 HTTP 状态码与提示消息，
 * 由 {@link GlobalExceptionHandler} 统一映射为 R 响应。
 */
public class BusinessException extends RuntimeException {

    private final int status;

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
