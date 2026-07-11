package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChain;

import cn.jiebaba.summer.security.jwt.JwtClaims;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.security.jwt.JwtException;
import cn.jiebaba.summer.web.http.HttpStatus;
import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 处理刷新令牌请求（默认 POST /refresh）：读取 JSON 请求体 {"refreshToken":"..."}，
 * 经 JwtDecoder 校验签名、过期时间与令牌类型（必须为 refresh 类型）。校验通过后签发新的
 * 访问令牌；当启用轮转时同时签发新的刷新令牌，实现「滑动过期」的会话续期。校验失败响应 401。
 * 短路过滤器链（不分派路由）。对应 Spring Security 的刷新令牌端点。
 * <p>注意：纯无状态实现下，旧刷新令牌在其过期前仍然有效（无法服务端吊销）。
 */
public final class JwtRefreshFilter implements Filter {

    private final JwtDecoder decoder;
    private final JwtEncoder encoder;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;
    private final String refreshUrl;
    private final boolean rotateRefreshToken;

    public JwtRefreshFilter(JwtDecoder decoder, JwtEncoder encoder,
                           long accessTokenTtlSeconds, long refreshTokenTtlSeconds,
                           String refreshUrl, boolean rotateRefreshToken) {
        this.decoder = decoder;
        this.encoder = encoder;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.refreshUrl = refreshUrl;
        this.rotateRefreshToken = rotateRefreshToken;
    }

    @Override
    /**
     * 过滤器入口：匹配刷新路径与 POST 方法时读取 refreshToken 并校验，成功则签发新的访问令牌
     * （启用轮转时一并签发新的刷新令牌），失败返回 401。
     */
    public void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception {
        if (!refreshUrl.equalsIgnoreCase(request.path()) || !"POST".equalsIgnoreCase(request.method().name())) {
            chain.doFilter(request, response);
            return;
        }

        Map<String, Object> body = LoginBodyParser.parse(request.body());
        Object refreshObj = body.get("refreshToken");
        if (refreshObj == null) {
            writeError(response, HttpStatus.BAD_REQUEST.code(), "Bad Request", "refreshToken is required");
            return;
        }
        String refreshToken = refreshObj.toString();

        try {
            JwtClaims claims = decoder.decode(refreshToken, JwtClaims.TYPE_REFRESH);
            long now = System.currentTimeMillis() / 1000L;
            JwtClaims accessClaims = JwtClaims.builder()
                    .subject(claims.getSubject())
                    .issuedAt(now)
                    .expiresAt(now + accessTokenTtlSeconds)
                    .type(JwtClaims.TYPE_ACCESS)
                    .id(UUID.randomUUID().toString())
                    .claim("authorities", claims.getAuthorities())
                    .build();
            String accessToken = encoder.encode(accessClaims);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("accessToken", accessToken);
            resp.put("tokenType", "Bearer");
            resp.put("expiresIn", accessTokenTtlSeconds);

            String responseRefreshToken;
            long refreshExpiresIn;
            if (rotateRefreshToken) {
                JwtClaims newRefreshClaims = JwtClaims.builder()
                        .subject(claims.getSubject())
                        .issuedAt(now)
                        .expiresAt(now + refreshTokenTtlSeconds)
                        .type(JwtClaims.TYPE_REFRESH)
                        .id(UUID.randomUUID().toString())
                        .claim("authorities", claims.getAuthorities())
                        .build();
                responseRefreshToken = encoder.encode(newRefreshClaims);
                refreshExpiresIn = refreshTokenTtlSeconds;
            } else {
                responseRefreshToken = refreshToken;
                refreshExpiresIn = Math.max(0L, claims.getExpiresAt() - now);
            }
            resp.put("refreshToken", responseRefreshToken);
            resp.put("refreshExpiresIn", refreshExpiresIn);
            resp.put("username", claims.getSubject());
            resp.put("authorities", claims.getAuthorities());

            response.status(HttpStatus.OK.code());
            response.contentType(MediaType.APPLICATION_JSON_UTF8);
            response.body(LoginBodyParser.stringify(resp));
        } catch (JwtException e) {
            writeError(response, HttpStatus.UNAUTHORIZED.code(), "Unauthorized", "Invalid or expired refresh token");
        }
    }

    private static void writeError(WebResponse response, int status, String error, String message) {
        response.status(status);
        response.contentType(MediaType.APPLICATION_JSON_UTF8);
        response.body("{\"status\":" + status + ",\"error\":\"" + JsonEscape.escape(error)
                + "\",\"message\":\"" + JsonEscape.escape(message) + "\"}");
    }
}
