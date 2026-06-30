package cn.jiebaba.summer.security.crypto;

import java.security.SecureRandom;

/**
 * {@link PasswordEncoder} backed by the pure-JDK {@link BCrypt} implementation.
 * Produces the standard {@code $2a$cost$salt+hash} format, interoperable with
 * Spring Security's {@code BCryptPasswordEncoder} and most bcrypt libraries.
 * <p>Mirrors {@code org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder}.
 */
public final class BCryptPasswordEncoder implements PasswordEncoder {

    private final int strength;
    private final SecureRandom random;

    /** Default strength 10 (2^10 = 1024 key-expansion rounds). */
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

    /** Upgrades/encodes only when the stored hash isn't already bcrypt. */
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null && !encodedPassword.startsWith("$2");
    }

    private static String toString(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }
}
