package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.security.crypto.BCryptPasswordEncoder;
import cn.jiebaba.summer.security.crypto.PasswordEncoder;

/** 纯 JDK 实现 BCrypt 密码编码器的单元测试。 */
public class SecurityCryptoTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(6); // 低强度以加快测试

    @Test
    public void encodeThenMatches() {
        String hash = encoder.encode("s3cret!");
        Assert.assertTrue(hash.startsWith("$2a$"), "hash should use $2a$ format: " + hash);
        Assert.assertTrue(encoder.matches("s3cret!", hash), "correct password must match");
    }

    @Test
    public void wrongPasswordDoesNotMatch() {
        String hash = encoder.encode("correct-horse-battery-staple");
        Assert.assertFalse(encoder.matches("wrong", hash), "wrong password must not match");
    }

    @Test
    public void differentSaltsProduceDifferentHashes() {
        String a = encoder.encode("same");
        String b = encoder.encode("same");
        Assert.assertTrue(!a.equals(b), "two encodings of the same password differ (random salt)");
        Assert.assertTrue(encoder.matches("same", a), "both hashes match the password");
        Assert.assertTrue(encoder.matches("same", b), "both hashes match the password");
    }

    @Test
    public void nullAndBlankEncodedRejected() {
        Assert.assertFalse(encoder.matches("x", null), "null encoded password -> no match");
        Assert.assertFalse(encoder.matches("x", ""), "blank encoded password -> no match");
        Assert.assertFalse(encoder.matches("x", "not-a-bcrypt-hash"), "non-bcrypt string -> no match");
    }

    @Test
    public void costFactorRespected() {
        String hash = encoder.encode("pw");
        // $2a$06$ -> cost 为 6（编码器强度 6）
        Assert.assertTrue(hash.startsWith("$2a$06$"), "cost factor should be 06: " + hash);
    }
}
