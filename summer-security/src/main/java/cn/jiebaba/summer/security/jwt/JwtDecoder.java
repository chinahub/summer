package cn.jiebaba.summer.security.jwt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Decodes and verifies a compact JWS (HS256) produced by {@link JwtEncoder}.
 * Verifies the signature in constant time and enforces {@code exp} expiry.
 */
public final class JwtDecoder {

    private final byte[] secret;

    public JwtDecoder(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.secret = secret.clone();
    }

    public JwtDecoder(String secret) {
        this(secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8));
    }

    public JwtClaims decode(String token) throws JwtException {
        if (token == null || token.isBlank()) {
            throw new JwtException("Missing JWT token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("Invalid JWT: expected 3 segments");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmac(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Invalid JWT signature encoding", e);
        }
        if (!constantTimeEquals(expected, actual)) {
            throw new JwtException("Invalid JWT signature");
        }

        byte[] payload;
        try {
            payload = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Invalid JWT payload encoding", e);
        }
        java.util.Map<String, Object> claims;
        try {
            claims = JsonReader.read(new String(payload, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new JwtException("Invalid JWT payload JSON", e);
        }

        long now = System.currentTimeMillis() / 1000L;
        long exp = asLong(claims.get("exp"));
        if (exp > 0 && now >= exp) {
            throw new JwtException("JWT token expired at " + exp);
        }

        return new JwtClaims(claims);
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new JwtException("Failed to compute HMAC-SHA256", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return 0L;
        return Long.parseLong(v.toString());
    }
}
