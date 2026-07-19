package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.security.crypto.BCryptPasswordEncoder;
import cn.jiebaba.summer.security.crypto.PasswordEncoder;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** 纯 JDK 实现 BCrypt 密码编码器的单元测试。 */
public class SecurityCryptoTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(6); // 低强度以加快测试

    @Test
    public void encodeThenMatches() {
        String hash = encoder.encode("s3cret!");
        Assertions.assertTrue(hash.startsWith("$2a$"), "hash should use $2a$ format: " + hash);
        Assertions.assertTrue(encoder.matches("s3cret!", hash), "correct password must match");
    }

    @Test
    public void wrongPasswordDoesNotMatch() {
        String hash = encoder.encode("correct-horse-battery-staple");
        Assertions.assertFalse(encoder.matches("wrong", hash), "wrong password must not match");
    }

    @Test
    public void differentSaltsProduceDifferentHashes() {
        String a = encoder.encode("same");
        String b = encoder.encode("same");
        Assertions.assertTrue(!a.equals(b), "two encodings of the same password differ (random salt)");
        Assertions.assertTrue(encoder.matches("same", a), "both hashes match the password");
        Assertions.assertTrue(encoder.matches("same", b), "both hashes match the password");
    }

    @Test
    public void nullAndBlankEncodedRejected() {
        Assertions.assertFalse(encoder.matches("x", null), "null encoded password -> no match");
        Assertions.assertFalse(encoder.matches("x", ""), "blank encoded password -> no match");
        Assertions.assertFalse(encoder.matches("x", "not-a-bcrypt-hash"), "non-bcrypt string -> no match");
    }

    @Test
    public void costFactorRespected() {
        String hash = encoder.encode("pw");
        // $2a$06$ -> cost 为 6（编码器强度 6）
        Assertions.assertTrue(hash.startsWith("$2a$06$"), "cost factor should be 06: " + hash);
    }
}
