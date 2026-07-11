package cn.jiebaba.summer.security.web.csrf;

import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

/**
 * CSRF 令牌的存取策略。内置基于 Cookie 的无状态实现（双重提交 Cookie 模式），
 * 应用亦可提供自定义实现（如基于会话）。对应 Spring Security 的
 * {@code CsrfTokenRepository}。
 */
public interface CsrfTokenRepository {

    /** 从当前请求加载已存储的令牌；不存在时返回 {@code null}。 */
    CsrfToken loadToken(WebRequest request);

    /** 生成一个新的随机令牌。 */
    CsrfToken generateToken(WebRequest request);

    /** 将令牌持久化（如写入响应 Cookie）；{@code token} 为 {@code null} 时清除令牌。 */
    void saveToken(CsrfToken token, WebRequest request, WebResponse response);
}
