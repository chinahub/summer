package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChain;

import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.GrantedAuthority;
import cn.jiebaba.summer.security.core.SecurityContext;
import cn.jiebaba.summer.security.core.SecurityContextHolder;
import cn.jiebaba.summer.security.core.SimpleGrantedAuthority;
import cn.jiebaba.summer.security.core.UsernamePasswordAuthenticationToken;
import cn.jiebaba.summer.security.jwt.JwtClaims;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.web.http.HttpStatus;
import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 从 {@code Authorization} 头提取 Bearer JWT，经 {@link JwtDecoder} 校验，
 * 并用结果 {@link Authentication} 填充 {@link SecurityContextHolder}。
 * 无效/过期令牌返回 401。
 * <p>无状态：不创建会话，主体由声明重建。
 */
public final class JwtAuthenticationFilter implements Filter {

    private static final String BEARER = "Bearer ";
    private final JwtDecoder decoder;

    public JwtAuthenticationFilter(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    /**
     * 过滤器入口：从 Authorization 头提取 Bearer JWT 并校验，成功则填充安全上下文，无效则返回 401。
     */
    public void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception {
        String header = request.header("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            try {
                JwtClaims claims = decoder.decode(token, JwtClaims.TYPE_ACCESS);
                List<? extends GrantedAuthority> authorities = claims.getAuthorities().stream()
                        .map(SimpleGrantedAuthority::new)
                        .map(a -> (GrantedAuthority) a)
                        .toList();
                Object principal = claims.getSubject();
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(request.remoteAddress());
                SecurityContextHolder.setContext(new SecurityContext(auth));
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, "Invalid or expired token");
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 清除虚拟线程上的每请求上下文，避免泄漏。
            SecurityContextHolder.clearContext();
        }
    }

    static void writeUnauthorized(WebResponse response, String message) {
        response.status(HttpStatus.UNAUTHORIZED.code());
        response.contentType(MediaType.APPLICATION_JSON_UTF8);
        response.body("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\""
                + JsonEscape.escape(message) + "\"}");
    }
}
