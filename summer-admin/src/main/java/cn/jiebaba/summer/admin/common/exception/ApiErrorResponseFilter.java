package cn.jiebaba.summer.admin.common.exception;

import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.json.Json;
import cn.jiebaba.summer.web.bind.HandlerException;
import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChain;
import cn.jiebaba.summer.web.http.HttpStatus;
import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;
import cn.jiebaba.summer.web.server.ResponseStatusException;
import cn.jiebaba.summer.web.validation.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * API 错误响应规范化过滤器：包住安全链与路由分派，仅作用于 /api 前缀路径。
 * 分发器在过滤器链之外兜底异常，因此本过滤器同时覆盖两条路径：
 * <ul>
 *   <li>链内抛出的异常（含未来安全链的 401/403、框架校验错误）——按类型映射为 R；</li>
 *   <li>链正常返回但状态为 4xx/5xx 且体仍是框架内置错误格式（如无路由 404）——改写为 R。</li>
 * </ul>
 * 已是 R 的响应不重复改写；非 /api 路径完全不动；未知异常仅回安全通用消息，
 * 完整堆栈只记服务端日志。
 */
@Component
public class ApiErrorResponseFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(ApiErrorResponseFilter.class.getName());
    private static final String API_PREFIX = "/api";
    private static final String GENERIC_ERROR_MSG = "系统异常，请稍后重试";

    @Override
    public void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception {
        String path = request.path() == null ? "" : request.path();
        if (!path.equals(API_PREFIX) && !path.startsWith(API_PREFIX + "/")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            chain.doFilter(request, response);
        } catch (Throwable t) {
            // 已提交（SSE 等流式响应）无法改写，交回分发器兜底
            if (response.committed()) {
                throw t;
            }
            writeThrowable(response, request, t);
            return;
        }
        if (response.committed()) {
            return;
        }
        int status = response.status();
        if (status < 400 || isRBody(response.body())) {
            return;
        }
        writeR(response, status, messageOf(status));
    }

    /**
     * 按异常类型映射为 R 响应：业务异常用其携带的状态与安全消息；框架校验/参数错误为 400；
     * 未知异常仅回通用消息并记录服务端日志，绝不外泄堆栈、SQL 或敏感请求体。
     *
     * @param response 当前响应（未提交）
     * @param request  当前请求（用于服务端日志定位）
     * @param t        过滤器链抛出的异常
     */
    private static void writeThrowable(WebResponse response, WebRequest request, Throwable t) {
        if (t instanceof BusinessException be) {
            writeR(response, be.status(), be.getMessage());
            return;
        }
        if (t instanceof ResponseStatusException rse) {
            String reason = rse.reason();
            writeR(response, rse.status(),
                    reason == null || reason.isBlank() ? messageOf(rse.status()) : reason);
            return;
        }
        if (t instanceof ValidationException ve) {
            String msg = ve.violations().stream()
                    .map(v -> v.field() + ": " + v.message())
                    .collect(Collectors.joining("; "));
            writeR(response, 400, msg.isEmpty() ? "请求参数错误" : msg);
            return;
        }
        if (t instanceof IllegalArgumentException iae) {
            writeR(response, 400, iae.getMessage() == null ? "请求参数错误" : iae.getMessage());
            return;
        }
        if (t instanceof HandlerException he) {
            writeR(response, 400, he.getMessage() == null ? "请求参数错误" : he.getMessage());
            return;
        }
        LOG.log(Level.SEVERE, "未处理异常 " + request.method() + " " + request.path(), t);
        writeR(response, 500, GENERIC_ERROR_MSG);
    }

    private static void writeR(WebResponse response, int status, String msg) {
        response.status(status);
        response.contentType(MediaType.APPLICATION_JSON_UTF8);
        response.body(Json.stringify(R.failure(status, msg)));
    }

    /** 判断响应体是否已是 R 形（紧凑 JSON 以 "code" 为首字段）。 */
    private static boolean isRBody(byte[] body) {
        if (body == null || body.length == 0) {
            return false;
        }
        String text = new String(body, StandardCharsets.UTF_8).trim();
        return text.startsWith("{\"code\":");
    }

    /** 按状态码给出对外安全的中文提示，未知状态回退框架 reason。 */
    private static String messageOf(int status) {
        return switch (status) {
            case 400 -> "请求参数错误";
            case 401 -> "未认证或登录已过期";
            case 403 -> "没有访问权限";
            case 404 -> "接口不存在";
            case 405 -> "请求方法不允许";
            case 409 -> "资源冲突";
            case 500 -> GENERIC_ERROR_MSG;
            default -> HttpStatus.valueOf(status).reason();
        };
    }
}
