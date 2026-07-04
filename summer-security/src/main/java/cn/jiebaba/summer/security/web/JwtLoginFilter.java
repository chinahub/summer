package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChain;

import cn.jiebaba.summer.security.authentication.AuthenticationException;
import cn.jiebaba.summer.security.authentication.AuthenticationManager;
import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.GrantedAuthority;
import cn.jiebaba.summer.security.core.UsernamePasswordAuthenticationToken;
import cn.jiebaba.summer.security.jwt.JwtClaims;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.web.http.HttpStatus;
import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 处理登录请求（默认 {@code POST /login}）：读取 JSON 请求体
 * {@code {"username":"...","password":"..."}} 并经 {@link AuthenticationManager} 认证。
 * 成功响应 {@code 200} 及 JWT 访问令牌；失败响应 {@code 401}。短路过滤器链（不分派路由）。
 * 对应 Spring Security 的 {@code UsernamePasswordAuthenticationFilter}。
 */
public final class JwtLoginFilter implements Filter {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final long tokenTtlSeconds;
    private final String loginPath;

    public JwtLoginFilter(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
                          long tokenTtlSeconds, String loginPath) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.loginPath = loginPath;
    }

    @Override
    /**
     * 过滤器入口：匹配登录路径与 POST 方法时读取用户名密码并认证，成功签发 JWT 令牌，失败返回 401。
     */
    public void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception {
        if (!loginPath.equalsIgnoreCase(request.path()) || !"POST".equalsIgnoreCase(request.method().name())) {
            chain.doFilter(request, response);
            return;
        }

        Map<String, Object> body = LoginBodyParser.parse(request.body());
        Object usernameObj = body.get("username");
        Object passwordObj = body.get("password");
        if (usernameObj == null || passwordObj == null) {
            writeError(response, HttpStatus.BAD_REQUEST.code(), "Bad Request", "username and password are required");
            return;
        }
        String username = usernameObj.toString();
        String password = passwordObj.toString();

        try {
            Authentication result = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            long now = System.currentTimeMillis() / 1000L;
            JwtClaims claims = JwtClaims.builder()
                    .subject(result.getName())
                    .issuedAt(now)
                    .expiresAt(now + tokenTtlSeconds)
                    .authorities(result.getAuthorities())
                    .build();
            String token = jwtEncoder.encode(claims);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("accessToken", token);
            resp.put("tokenType", "Bearer");
            resp.put("expiresIn", tokenTtlSeconds);
            resp.put("username", result.getName());
            resp.put("authorities", result.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
            response.status(HttpStatus.OK.code());
            response.contentType(MediaType.APPLICATION_JSON_UTF8);
            response.body(LoginBodyParser.stringify(resp));
        } catch (AuthenticationException e) {
            writeError(response, HttpStatus.UNAUTHORIZED.code(), "Unauthorized", "Invalid credentials");
        }
    }

    private static void writeError(WebResponse response, int status, String error, String message) {
        response.status(status);
        response.contentType(MediaType.APPLICATION_JSON_UTF8);
        response.body("{\"status\":" + status + ",\"error\":\"" + JsonEscape.escape(error)
                + "\",\"message\":\"" + JsonEscape.escape(message) + "\"}");
    }

    static String ascii(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
