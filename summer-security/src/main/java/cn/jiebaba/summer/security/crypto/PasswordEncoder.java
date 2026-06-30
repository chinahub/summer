package cn.jiebaba.summer.security.crypto;

/**
 * Encodes raw passwords and verifies them against a stored encoded value.
 * Mirrors {@code org.springframework.security.crypto.password.PasswordEncoder}.
 */
public interface PasswordEncoder {
    /** Encode the raw password into a storable representation (e.g. a bcrypt hash). */
    String encode(CharSequence rawPassword);

    /** Verify the raw password matches the previously encoded one. */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}
