package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.security.core.SimpleGrantedAuthority;
import cn.jiebaba.summer.security.jwt.JwtClaims;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.security.jwt.JwtException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** HS256 JWT 编码器/解码器的单元测试。 */
public class SecurityJwtTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef-test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private final JwtEncoder encoder = new JwtEncoder(SECRET);
    private final JwtDecoder decoder = new JwtDecoder(SECRET);

    @Test
    public void roundTrip() {
        long now = System.currentTimeMillis() / 1000L;
        JwtClaims claims = JwtClaims.builder()
                .subject("alice")
                .issuedAt(now)
                .expiresAt(now + 3600)
                .authorities(List.of(SimpleGrantedAuthority.roleOf("ADMIN")))
                .build();
        String token = encoder.encode(claims);
        Assertions.assertTrue(token.split("\\.").length == 3, "token has 3 segments");

        JwtClaims decoded = decoder.decode(token);
        Assertions.assertEquals("alice", decoded.getSubject(), "subject preserved");
        Assertions.assertTrue(decoded.getAuthorities().contains("ROLE_ADMIN"), "authorities preserved");
        Assertions.assertTrue(decoded.getExpiresAt() == now + 3600, "exp preserved");
    }

    @Test
    public void tamperedSignatureRejected() {
        long now = System.currentTimeMillis() / 1000L;
        String token = encoder.encode(JwtClaims.builder().subject("bob").issuedAt(now).expiresAt(now + 60).build());
        String tampered = token.substring(0, token.length() - 2) + "AA";
        Assertions.assertThrows(JwtException.class, () -> decoder.decode(tampered), "tampered signature must fail");
    }

    @Test
    public void wrongSecretRejected() {
        long now = System.currentTimeMillis() / 1000L;
        String token = encoder.encode(JwtClaims.builder().subject("bob").issuedAt(now).expiresAt(now + 60).build());
        JwtDecoder other = new JwtDecoder("another-32-byte-secret-xxxxxxxxxxxxxxxx!".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Assertions.assertThrows(JwtException.class, () -> other.decode(token), "token signed with a different secret must fail");
    }

    @Test
    public void expiredTokenRejected() {
        long now = System.currentTimeMillis() / 1000L;
        String token = encoder.encode(JwtClaims.builder().subject("bob").issuedAt(now - 10).expiresAt(now - 1).build());
        Assertions.assertThrows(JwtException.class, () -> decoder.decode(token), "expired token must fail");
    }

    @Test
    public void malformedTokenRejected() {
        Assertions.assertThrows(JwtException.class, () -> decoder.decode("not.a.jwt"), "malformed token must fail");
        Assertions.assertThrows(JwtException.class, () -> decoder.decode(""), "empty token must fail");
    }

    @Test
    public void typedDecodeEnforcesTokenType() {
        long now = System.currentTimeMillis() / 1000L;
        JwtClaims access = JwtClaims.builder()
                .subject("alice")
                .issuedAt(now)
                .expiresAt(now + 60)
                .type(JwtClaims.TYPE_ACCESS)
                .authorities(List.of(SimpleGrantedAuthority.roleOf("USER")))
                .build();
        JwtClaims refresh = JwtClaims.builder()
                .subject("alice")
                .issuedAt(now)
                .expiresAt(now + 600)
                .type(JwtClaims.TYPE_REFRESH)
                .id("refresh-jti-1")
                .authorities(List.of(SimpleGrantedAuthority.roleOf("USER")))
                .build();
        String accessToken = encoder.encode(access);
        String refreshToken = encoder.encode(refresh);

        // 类型匹配时正常解码，且 typ/jti 可读
        JwtClaims decodedAccess = decoder.decode(accessToken, JwtClaims.TYPE_ACCESS);
        Assertions.assertEquals(JwtClaims.TYPE_ACCESS, decodedAccess.getType(), "access typ preserved");
        JwtClaims decodedRefresh = decoder.decode(refreshToken, JwtClaims.TYPE_REFRESH);
        Assertions.assertEquals(JwtClaims.TYPE_REFRESH, decodedRefresh.getType(), "refresh typ preserved");
        Assertions.assertEquals("refresh-jti-1", decodedRefresh.getId(), "jti preserved");

        // refresh 令牌不能当作 access 令牌使用（反之亦然）
        Assertions.assertThrows(JwtException.class,
                () -> decoder.decode(refreshToken, JwtClaims.TYPE_ACCESS),
                "refresh token must not be accepted as access token");
        Assertions.assertThrows(JwtException.class,
                () -> decoder.decode(accessToken, JwtClaims.TYPE_REFRESH),
                "access token must not be accepted as refresh token");

        // 无类型声明（typ 缺失）的令牌在按类型校验时被拒绝
        JwtClaims untyped = JwtClaims.builder().subject("bob").issuedAt(now).expiresAt(now + 60).build();
        String untypedToken = encoder.encode(untyped);
        Assertions.assertThrows(JwtException.class,
                () -> decoder.decode(untypedToken, JwtClaims.TYPE_ACCESS),
                "token without typ must be rejected when type is expected");
    }
}
