package cn.jiebaba.summer.admin.common.exception;

import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.web.annotation.ExceptionHandler;
import cn.jiebaba.summer.web.annotation.RestControllerAdvice;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全局异常映射：业务异常按携带的状态码返回 R；未知异常只回安全通用消息，
 * 完整堆栈仅记录服务端日志，绝不外泄框架堆栈、SQL 或敏感请求体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class.getName());
    private static final String GENERIC_ERROR_MSG = "系统异常，请稍后重试";

    /**
     * 业务异常：显式设置动态状态码，消息原样对外（BusinessException 消息即安全文案）。
     *
     * @param ex       业务异常
     * @param response 当前响应（用于显式设置动态状态）
     * @return R 形错误体
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException ex, WebResponse response) {
        response.status(ex.status());
        return R.failure(ex.status(), ex.getMessage());
    }

    /**
     * 参数非法（框架视为 400 的业务参数错误）：消息对外，状态固定 400。
     *
     * @param ex       参数异常
     * @param response 当前响应
     * @return R 形错误体
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgument(IllegalArgumentException ex, WebResponse response) {
        response.status(400);
        return R.failure(400, ex.getMessage() == null ? "请求参数错误" : ex.getMessage());
    }

    /**
     * 未知异常兜底：服务端记录完整堆栈，对外只回固定通用消息，避免泄露任何内部细节。
     *
     * @param ex       未处理异常
     * @param request  当前请求（用于日志定位）
     * @param response 当前响应
     * @return R 形错误体（500）
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleUnknown(Exception ex, WebRequest request, WebResponse response) {
        LOG.log(Level.SEVERE, "未处理异常 " + request.method() + " " + request.path(), ex);
        response.status(500);
        return R.failure(500, GENERIC_ERROR_MSG);
    }
}
