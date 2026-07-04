package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.security.core.SimpleGrantedAuthority;
import cn.jiebaba.summer.security.jwt.JwtClaims;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.security.jwt.JwtException;

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
        Assert.assertTrue(token.split("\\.").length == 3, "token has 3 segments");

        JwtClaims decoded = decoder.decode(token);
        Assert.assertEquals("alice", decoded.getSubject(), "subject preserved");
        Assert.assertTrue(decoded.getAuthorities().contains("ROLE_ADMIN"), "authorities preserved");
        Assert.assertTrue(decoded.getExpiresAt() == now + 3600, "exp preserved");
    }

    @Test
    public void tamperedSignatureRejected() {
        long now = System.currentTimeMillis() / 1000L;
        String token = encoder.encode(JwtClaims.builder().subject("bob").issuedAt(now).expiresAt(now + 60).build());
        String tampered = token.substring(0, token.length() - 2) + "AA";
        Assert.assertThrows(JwtException.class, () -> decoder.decode(tampered), "tampered signature must fail");
    }

    @Test
    public void wrongSecretRejected() {
        long now = System.currentTimeMillis() / 1000L;
        String token = encoder.encode(JwtClaims.builder().subject("bob").issuedAt(now).expiresAt(now + 60).build());
        JwtDecoder other = new JwtDecoder("another-32-byte-secret-xxxxxxxxxxxxxxxx!".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Assert.assertThrows(JwtException.class, () -> other.decode(token), "token signed with a different secret must fail");
    }

    @Test
    public void expiredTokenRejected() {
        long now = System.currentTimeMillis() / 1000L;
        String token = encoder.encode(JwtClaims.builder().subject("bob").issuedAt(now - 10).expiresAt(now - 1).build());
        Assert.assertThrows(JwtException.class, () -> decoder.decode(token), "expired token must fail");
    }

    @Test
    public void malformedTokenRejected() {
        Assert.assertThrows(JwtException.class, () -> decoder.decode("not.a.jwt"), "malformed token must fail");
        Assert.assertThrows(JwtException.class, () -> decoder.decode(""), "empty token must fail");
    }
}
