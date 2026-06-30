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
 * Extracts a Bearer JWT from the {@code Authorization} header, validates it via
 * {@link JwtDecoder}, and populates the {@link SecurityContextHolder} with the
 * resulting {@link Authentication}. Invalid/expired tokens yield 401.
 * <p>Stateless: no session is created; the principal is reconstructed from claims.
 */
public final class JwtAuthenticationFilter implements Filter {

    private static final String BEARER = "Bearer ";
    private final JwtDecoder decoder;

    public JwtAuthenticationFilter(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public void doFilter(WebRequest request, WebResponse response, FilterChain chain) throws Exception {
        String header = request.header("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            try {
                JwtClaims claims = decoder.decode(token);
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
            // Clear per-request context on the virtual thread to avoid leakage.
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
