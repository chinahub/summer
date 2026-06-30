package cn.jiebaba.summer.core.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Encryption / digest helpers inspired by {@code cn.hutool.crypto.SecureUtil}.
 *
 * <p>Built only on the JDK ({@code java.security} / {@code javax.crypto}). Provides Base64,
 * hex, message digests, HMAC, symmetric (AES/DES) and asymmetric (RSA) encryption plus
 * signatures and UUID helpers.
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    private static final Base64.Encoder B64 = Base64.getEncoder();
    private static final Base64.Decoder B64D = Base64.getDecoder();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String MD5 = "MD5";
    public static final String SHA1 = "SHA-1";
    public static final String SHA256 = "SHA-256";
    public static final String SHA512 = "SHA-512";
    public static final String HMAC_MD5 = "HmacMD5";
    public static final String HMAC_SHA256 = "HmacSHA256";
    public static final String AES = "AES";
    public static final String DES = "DES";
    public static final String RSA = "RSA";

    // ---- Base64 --------------------------------------------------------------

    public static String encodeBase64(byte[] data) { return data == null ? null : B64.encodeToString(data); }
    public static String encodeBase64(String data) { return data == null ? null : B64.encodeToString(data.getBytes(StandardCharsets.UTF_8)); }
    public static byte[] decodeBase64(String base64) { return base64 == null ? null : B64D.decode(base64); }
    public static byte[] decodeBase64(byte[] base64) { return base64 == null ? null : B64D.decode(base64); }

    // ---- hex -----------------------------------------------------------------

    public static String encodeHex(byte[] data) {
        if (data == null) return null;
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xff;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(out);
    }

    public static byte[] decodeHex(String hex) {
        if (hex == null) return null;
        String s = hex.trim();
        int len = s.length();
        if (len % 2 != 0) throw new IllegalArgumentException("Hex string must have an even length: " + hex);
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(s.charAt(i), 16);
            int lo = Character.digit(s.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("Invalid hex character in: " + hex);
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    // ---- digest --------------------------------------------------------------

    public static byte[] digest(String algorithm, byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return md.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("Digest failed: " + algorithm, e);
        }
    }

    public static String digestHex(String algorithm, String data) {
        return encodeHex(digest(algorithm, data.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] md5(byte[] data) { return digest(MD5, data); }
    public static String md5Hex(String data) { return digestHex(MD5, data); }
    public static byte[] sha1(byte[] data) { return digest(SHA1, data); }
    public static String sha1Hex(String data) { return digestHex(SHA1, data); }
    public static byte[] sha256(byte[] data) { return digest(SHA256, data); }
    public static String sha256Hex(String data) { return digestHex(SHA256, data); }
    public static byte[] sha512(byte[] data) { return digest(SHA512, data); }
    public static String sha512Hex(String data) { return digestHex(SHA512, data); }

    // ---- HMAC ----------------------------------------------------------------

    public static byte[] hmac(String algorithm, byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed: " + algorithm, e);
        }
    }

    public static String hmacHex(String algorithm, String data, String key) {
        return encodeHex(hmac(algorithm, data.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] hmacMd5(byte[] data, byte[] key) { return hmac(HMAC_MD5, data, key); }
    public static String hmacMd5Hex(String data, String key) { return hmacHex(HMAC_MD5, data, key); }
    public static byte[] hmacSha256(byte[] data, byte[] key) { return hmac(HMAC_SHA256, data, key); }
    public static String hmacSha256Hex(String data, String key) { return hmacHex(HMAC_SHA256, data, key); }

    public static byte[] generateHmacKey(String algorithm) {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(algorithm);
            return kg.generateKey().getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate HMAC key: " + algorithm, e);
        }
    }

    // ---- random / uuid -------------------------------------------------------

    public static byte[] randomSalt(int byteLength) {
        byte[] salt = new byte[byteLength];
        RANDOM.nextBytes(salt);
        return salt;
    }

    public static String randomUUID() { return UUID.randomUUID().toString(); }

    public static String simpleUUID() { return UUID.randomUUID().toString().replace("-", ""); }
    // ---- AES -----------------------------------------------------------------

    public static byte[] generateAESKey() { return generateAESKey(128); }

    public static byte[] generateAESKey(int bits) {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(AES);
            kg.init(bits, RANDOM);
            return kg.generateKey().getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate AES key", e);
        }
    }

    public static String generateAESKeyString() { return encodeBase64(generateAESKey(128)); }

    public static byte[] encryptAES(byte[] data, byte[] key) {
        return symmetric("AES/ECB/PKCS5Padding", Cipher.ENCRYPT_MODE, data, key, null);
    }

    public static byte[] decryptAES(byte[] data, byte[] key) {
        return symmetric("AES/ECB/PKCS5Padding", Cipher.DECRYPT_MODE, data, key, null);
    }

    public static String encryptAES(String data, String key) {
        byte[] derived = deriveKey(key, 16);
        return encodeBase64(encryptAES(data.getBytes(StandardCharsets.UTF_8), derived));
    }

    public static String decryptAES(String encryptedBase64, String key) {
        byte[] derived = deriveKey(key, 16);
        return new String(decryptAES(decodeBase64(encryptedBase64), derived), StandardCharsets.UTF_8);
    }

    // ---- DES -----------------------------------------------------------------

    public static byte[] generateDESKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(DES);
            return kg.generateKey().getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate DES key", e);
        }
    }

    public static byte[] encryptDES(byte[] data, byte[] key) {
        return symmetric("DES/ECB/PKCS5Padding", Cipher.ENCRYPT_MODE, data, key, null);
    }

    public static byte[] decryptDES(byte[] data, byte[] key) {
        return symmetric("DES/ECB/PKCS5Padding", Cipher.DECRYPT_MODE, data, key, null);
    }

    public static String encryptDES(String data, String key) {
        byte[] derived = deriveKey(key, 8);
        return encodeBase64(encryptDES(data.getBytes(StandardCharsets.UTF_8), derived));
    }

    public static String decryptDES(String encryptedBase64, String key) {
        byte[] derived = deriveKey(key, 8);
        return new String(decryptDES(decodeBase64(encryptedBase64), derived), StandardCharsets.UTF_8);
    }

    private static byte[] symmetric(String transformation, int mode, byte[] data, byte[] key, byte[] iv) {
        try {
            SecretKeySpec keySpec;
            if (transformation.startsWith("DES") && !"DESede".equals(transformation)) {
                SecretKeyFactory kf = SecretKeyFactory.getInstance(DES);
                keySpec = new SecretKeySpec(kf.generateSecret(new DESKeySpec(key)).getEncoded(), DES);
            } else {
                String algorithm = transformation.contains("/") ? transformation.substring(0, transformation.indexOf('/')) : transformation;
                keySpec = new SecretKeySpec(key, algorithm);
            }
            Cipher cipher = Cipher.getInstance(transformation);
            if (iv != null) cipher.init(mode, keySpec, new IvParameterSpec(iv));
            else cipher.init(mode, keySpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("Symmetric crypto failed: " + transformation, e);
        }
    }

    private static byte[] deriveKey(String key, int length) {
        byte[] digest = md5(key.getBytes(StandardCharsets.UTF_8));
        if (digest.length == length) return digest;
        byte[] out = new byte[length];
        System.arraycopy(digest, 0, out, 0, Math.min(length, digest.length));
        return out;
    }

    // ---- RSA -----------------------------------------------------------------

    public static KeyPair generateRSAKeyPair() { return generateRSAKeyPair(2048); }

    public static KeyPair generateRSAKeyPair(int keySize) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(RSA);
            kpg.initialize(keySize, RANDOM);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate RSA key pair", e);
        }
    }

    public static byte[] encryptRSA(PublicKey publicKey, byte[] data) {
        return rsa("RSA/ECB/PKCS1Padding", Cipher.ENCRYPT_MODE, publicKey, data);
    }

    public static byte[] decryptRSA(PrivateKey privateKey, byte[] data) {
        return rsa("RSA/ECB/PKCS1Padding", Cipher.DECRYPT_MODE, privateKey, data);
    }

    public static String encryptRSABase64(String data, PublicKey publicKey) {
        return encodeBase64(encryptRSA(publicKey, data.getBytes(StandardCharsets.UTF_8)));
    }

    public static String decryptRSABase64(String encryptedBase64, PrivateKey privateKey) {
        return new String(decryptRSA(privateKey, decodeBase64(encryptedBase64)), StandardCharsets.UTF_8);
    }

    private static byte[] rsa(String transformation, int mode, Key key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(mode, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("RSA crypto failed: " + transformation, e);
        }
    }

    public static byte[] sign(PrivateKey privateKey, byte[] data, String algorithm) {
        try {
            Signature sig = Signature.getInstance(algorithm);
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();
        } catch (Exception e) {
            throw new IllegalStateException("Sign failed: " + algorithm, e);
        }
    }

    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature, String algorithm) {
        try {
            Signature sig = Signature.getInstance(algorithm);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Verify failed: " + algorithm, e);
        }
    }

    public static String signBase64(PrivateKey privateKey, String data) {
        return encodeBase64(sign(privateKey, data.getBytes(StandardCharsets.UTF_8), "SHA256withRSA"));
    }

    public static boolean verifyBase64(PublicKey publicKey, String data, String signatureBase64) {
        return verify(publicKey, data.getBytes(StandardCharsets.UTF_8), decodeBase64(signatureBase64), "SHA256withRSA");
    }

    public static PublicKey readPublicKey(byte[] encoded) {
        try {
            return KeyFactory.getInstance(RSA).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read RSA public key", e);
        }
    }

    public static PrivateKey readPrivateKey(byte[] encoded) {
        try {
            return KeyFactory.getInstance(RSA).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read RSA private key", e);
        }
    }
}