package cn.jiebaba.summer.security.web.csrf;

/**
 * CSRF 令牌：包含回传令牌所用的请求头名、表单参数名与令牌值本身。
 * 对应 Spring Security 的 {@code org.springframework.security.web.csrf.CsrfToken}。
 *
 * @param headerName     客户端回传令牌时使用的请求头名（如 {@code X-XSRF-TOKEN}）
 * @param parameterName  客户端回传令牌时使用的表单/查询参数名（如 {@code _csrf}）
 * @param token          令牌值，用于与存储中的令牌做常量时间比对
 */
public record CsrfToken(String headerName, String parameterName, String token) {
}
