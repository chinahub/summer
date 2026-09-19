package cn.jiebaba.summer.security.crypto;

import java.security.SecureRandom;

/**
 * 由纯 JDK 实现的 {@link BCrypt} 支撑的 {@link PasswordEncoder}。
 * 产出标准 {@code $2a$cost$salt+hash} 格式，可与 Spring Security 的
 * {@code BCryptPasswordEncoder} 及多数 bcrypt 库互操作。
 * <p>对应 {@code org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder}。
 */
public final class BCryptPasswordEncoder implements PasswordEncoder {

    private final int strength;
    private final SecureRandom random;

    /** 默认强度 10（2^10 = 1024 轮密钥扩展）。 */
    public BCryptPasswordEncoder() {
        this(10);
    }

    public BCryptPasswordEncoder(int strength) {
        if (strength < 4 || strength > 31) {
            throw new IllegalArgumentException("strength must be between 4 and 31");
        }
        this.strength = strength;
        this.random = new SecureRandom();
    }

    @Override
    public String encode(CharSequence rawPassword) {
        String salt = BCrypt.gensalt(strength, random);
        return BCrypt.hashpw(toString(rawPassword), salt);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) return false;
        return BCrypt.checkpw(toString(rawPassword), encodedPassword);
    }

    /** 仅当存储的哈希不是 bcrypt 时才升级/编码。 */
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null && !encodedPassword.startsWith("$2");
    }

    private static String toString(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }
}
