package cn.jiebaba.summer.security.jwt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 将 {@link JwtClaims} 编码为紧凑 JWS（HS256）。token 结构为
 * {@code base64url(header).base64url(payload).base64url(signature)}，header 为
 * {@code {"alg":"HS256","typ":"JWT"}}。纯 JDK 实现，经 {@link Mac}。
 */
public final class JwtEncoder {

    private final byte[] secret;
    private static final byte[] HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8);

    public JwtEncoder(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.secret = secret.clone();
    }

    public JwtEncoder(String secret) {
        this(secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8));
    }

    public String encode(JwtClaims claims) {
        if (claims == null) throw new IllegalArgumentException("claims must not be null");
        String headerSegment = base64Url(HEADER_JSON);
        byte[] payloadJson = JsonWriter.write(claims.asMap()).getBytes(StandardCharsets.UTF_8);
        String payloadSegment = base64Url(payloadJson);
        String signingInput = headerSegment + "." + payloadSegment;
        byte[] signature = hmac(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(signature);
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

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** 暴露密钥长度/是否存在以供诊断（不暴露字节）。 */
    public int secretLength() { return secret.length; }
}
