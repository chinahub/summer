package cn.jiebaba.summer.security.jwt;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.jiebaba.summer.security.core.GrantedAuthority;

/**
 * JWT access token 携带的 claims。标准 claims {@code sub}/{@code iat}/{@code exp}
 * 之外，另有一个自定义的 {@code authorities} claim，保存 principal 的已授予权限（字符串）。
 */
public final class JwtClaims {

    private final Map<String, Object> claims;

    public JwtClaims(Map<String, Object> claims) {
        this.claims = claims == null ? new LinkedHashMap<>() : new LinkedHashMap<>(claims);
    }

    public String getSubject() {
        Object v = claims.get("sub");
        return v == null ? null : v.toString();
    }

    public long getIssuedAt() {
        return asLong(claims.get("iat"));
    }

    public long getExpiresAt() {
        return asLong(claims.get("exp"));
    }

    @SuppressWarnings("unchecked")
    public List<String> getAuthorities() {
        Object v = claims.get("authorities");
        if (v instanceof Collection<?> c) return c.stream().map(String::valueOf).toList();
        if (v == null) return List.of();
        return List.of(v.toString());
    }

    public Object get(String name) {
        return claims.get(name);
    }

    public Map<String, Object> asMap() {
        return new LinkedHashMap<>(claims);
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return 0L;
        return Long.parseLong(v.toString());
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<String, Object> claims = new LinkedHashMap<>();

        public Builder subject(String subject) { claims.put("sub", subject); return this; }
        public Builder issuedAt(long epochSeconds) { claims.put("iat", epochSeconds); return this; }
        public Builder expiresAt(long epochSeconds) { claims.put("exp", epochSeconds); return this; }
        public Builder authorities(Collection<? extends GrantedAuthority> authorities) {
            claims.put("authorities", authorities.stream().map(GrantedAuthority::getAuthority).toList());
            return this;
        }
        public Builder claim(String name, Object value) { claims.put(name, value); return this; }
        public Builder claims(Map<String, ?> extra) { claims.putAll(extra); return this; }

        public JwtClaims build() { return new JwtClaims(claims); }
    }
}
