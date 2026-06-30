package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.core.util.SecurityUtil;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public class SecurityUtilTest {

    @Test
    public void base64RoundTrip() {
        String s = SecurityUtil.encodeBase64("hello".getBytes());
        Assert.assertEquals("aGVsbG8=", s);
        Assert.assertEquals("hello", new String(SecurityUtil.decodeBase64(s)));
        Assert.assertEquals("aGVsbG8=", SecurityUtil.encodeBase64("hello"));
    }

    @Test
    public void hexRoundTrip() {
        Assert.assertEquals("48656c6c6f", SecurityUtil.encodeHex("Hello".getBytes()));
        Assert.assertEquals("Hello", new String(SecurityUtil.decodeHex("48656c6c6f")));
    }

    @Test
    public void digestVectors() {
        Assert.assertEquals("d41d8cd98f00b204e9800998ecf8427e", SecurityUtil.md5Hex(""));
        Assert.assertEquals("900150983cd24fb0d6963f7d28e17f72", SecurityUtil.md5Hex("abc"));
        Assert.assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", SecurityUtil.sha256Hex("abc"));
        Assert.assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", SecurityUtil.sha1Hex("abc"));
    }

    @Test
    public void hmac() {
        String mac = SecurityUtil.hmacSha256Hex("data", "secret");
        Assert.assertTrue(mac.length() == 64);
        Assert.assertEquals(mac, SecurityUtil.hmacSha256Hex("data", "secret"));
        Assert.assertFalse(mac.equals(SecurityUtil.hmacSha256Hex("data2", "secret")));
        Assert.assertNotNull(SecurityUtil.generateHmacKey("HmacSHA256"));
    }

    @Test
    public void aesStringRoundTrip() {
        String key = SecurityUtil.generateAESKeyString();
        String enc = SecurityUtil.encryptAES("summer secret", key);
        Assert.assertFalse(enc.isEmpty());
        Assert.assertEquals("summer secret", SecurityUtil.decryptAES(enc, key));
        Assert.assertEquals("summer secret", SecurityUtil.decryptAES(SecurityUtil.encryptAES("summer secret", "pwd"), "pwd"));
    }

    @Test
    public void aesRawRoundTrip() {
        byte[] key = SecurityUtil.generateAESKey();
        byte[] enc = SecurityUtil.encryptAES("data".getBytes(), key);
        Assert.assertEquals("data", new String(SecurityUtil.decryptAES(enc, key)));
    }

    @Test
    public void desRoundTrip() {
        String enc = SecurityUtil.encryptDES("hello", "pass");
        Assert.assertEquals("hello", SecurityUtil.decryptDES(enc, "pass"));
    }

    @Test
    public void rsaRoundTrip() {
        KeyPair kp = SecurityUtil.generateRSAKeyPair(2048);
        String enc = SecurityUtil.encryptRSABase64("rsa payload", kp.getPublic());
        Assert.assertEquals("rsa payload", SecurityUtil.decryptRSABase64(enc, kp.getPrivate()));
    }

    @Test
    public void rsaSignVerify() {
        KeyPair kp = SecurityUtil.generateRSAKeyPair(2048);
        String sig = SecurityUtil.signBase64(kp.getPrivate(), "message");
        Assert.assertTrue(SecurityUtil.verifyBase64(kp.getPublic(), "message", sig));
        Assert.assertFalse(SecurityUtil.verifyBase64(kp.getPublic(), "tampered", sig));
    }

    @Test
    public void rsaKeyEncoding() {
        KeyPair kp = SecurityUtil.generateRSAKeyPair(2048);
        PublicKey pub = SecurityUtil.readPublicKey(kp.getPublic().getEncoded());
        PrivateKey priv = SecurityUtil.readPrivateKey(kp.getPrivate().getEncoded());
        String enc = SecurityUtil.encryptRSABase64("k", pub);
        Assert.assertEquals("k", SecurityUtil.decryptRSABase64(enc, priv));
    }

    @Test
    public void uuidAndSalt() {
        Assert.assertTrue(SecurityUtil.randomUUID().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        Assert.assertEquals(32, SecurityUtil.simpleUUID().length());
        Assert.assertEquals(16, SecurityUtil.randomSalt(16).length);
    }
}