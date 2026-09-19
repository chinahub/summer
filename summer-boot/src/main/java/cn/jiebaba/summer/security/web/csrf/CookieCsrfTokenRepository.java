package cn.jiebaba.summer.security.web.csrf;

import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 基于 Cookie 的 CSRF 令牌仓库，采用双重提交 Cookie 模式：服务端将随机令牌写入
 * {@code httpOnly=false} 的 Cookie，客户端读取后通过请求头回传，过滤器比对二者是否一致。
 * 对应 Spring Security 的 {@code CookieCsrfTokenRepository}。
 */
public final class CookieCsrfTokenRepository implements CsrfTokenRepository {

    private static final String COOKIE_HEADER = "Cookie";
    private static final String SET_COOKIE = "Set-Cookie";
    private static final int TOKEN_BYTES = 16;

    private final String cookieName;
    private final String headerName;
    private final String parameterName;
    private final boolean httpOnly;
    private final boolean secure;
    private final String sameSite;
    private final long maxAge;

    private final SecureRandom random = new SecureRandom();

    public CookieCsrfTokenRepository(String cookieName, String headerName, String parameterName,
                                     boolean httpOnly, boolean secure, String sameSite, long maxAge) {
        this.cookieName = cookieName;
        this.headerName = headerName;
        this.parameterName = parameterName;
        this.httpOnly = httpOnly;
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAge = maxAge;
    }

    /** 以默认配置构造仓库：Cookie 名 XSRF-TOKEN、头名 X-XSRF-TOKEN、参数名 _csrf、SameSite=Lax。 */
    public static CookieCsrfTokenRepository withDefaults() {
        return new CookieCsrfTokenRepository("XSRF-TOKEN", "X-XSRF-TOKEN", "_csrf",
                false, false, "Lax", -1L);
    }

    /** 从 CSRF 配置属性构造仓库，使 Cookie 行为可由 summer.security.csrf.* 控制。 */
    public static CookieCsrfTokenRepository from(CsrfProperties props) {
        return new CookieCsrfTokenRepository(props.cookieName(), props.headerName(), props.parameterName(),
                props.cookieHttpOnly(), props.cookieSecure(), props.cookieSameSite(), props.cookieMaxAge());
    }

    /**
     * 从请求的 {@code Cookie} 头解析出指定名称的 Cookie 值；不存在时返回 {@code null}。
     * Cookie 头形如 {@code name1=value1; name2=value2}，按 {@code ;} 分割后按 {@code =} 取键值。
     *
     * @param request 当前 Web 请求
     * @return 加载到的 CSRF 令牌；无对应 Cookie 时返回 {@code null}
     */
    @Override
    public CsrfToken loadToken(WebRequest request) {
        String cookieHeader = request.header(COOKIE_HEADER);
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String name = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (name.equals(cookieName)) {
                return new CsrfToken(headerName, parameterName, value);
            }
        }
        return null;
    }

    /**
     * 生成一个新的随机令牌：使用 {@link SecureRandom} 产生 16 字节随机数，
     * 以 URL 安全的 Base64（无填充）编码，得到约 22 个字符的令牌字符串。
     *
     * @param request 当前 Web 请求
     * @return 新生成的 CSRF 令牌
     */
    @Override
    public CsrfToken generateToken(WebRequest request) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new CsrfToken(headerName, parameterName, token);
    }

    /**
     * 将令牌写入响应的 {@code Set-Cookie} 头；令牌为 {@code null} 时以 {@code Max-Age=0} 立即清除该 Cookie。
     * Cookie 属性按配置逐项追加：Path、Max-Age、HttpOnly、Secure、SameSite。
     *
     * @param token    待保存的令牌；为 {@code null} 时清除 Cookie
     * @param request   当前 Web 请求
     * @param response  当前 Web 响应
     */
    @Override
    public void saveToken(CsrfToken token, WebRequest request, WebResponse response) {
        StringBuilder cookie = new StringBuilder();
        if (token == null) {
            cookie.append(cookieName).append("=; Max-Age=0; Path=/");
        } else {
            cookie.append(cookieName).append('=').append(token.token());
            cookie.append("; Path=/");
            if (maxAge >= 0) {
                cookie.append("; Max-Age=").append(maxAge);
            }
            if (httpOnly) {
                cookie.append("; HttpOnly");
            }
            if (secure) {
                cookie.append("; Secure");
            }
            if (sameSite != null && !sameSite.isBlank()) {
                cookie.append("; SameSite=").append(sameSite);
            }
        }
        response.header(SET_COOKIE, cookie.toString());
    }
}
