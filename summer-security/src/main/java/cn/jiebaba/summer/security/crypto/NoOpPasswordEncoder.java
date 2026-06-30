package cn.jiebaba.summer.security.crypto;

/**
 * {@link PasswordEncoder} that performs no hashing (stores plaintext). Intended
 * only for testing or legacy migration. Mirrors Spring's {@code NoOpPasswordEncoder}.
 */
public final class NoOpPasswordEncoder implements PasswordEncoder {

    public static final NoOpPasswordEncoder INSTANCE = new NoOpPasswordEncoder();

    private NoOpPasswordEncoder() {}

    @Override public String encode(CharSequence rawPassword) { return rawPassword == null ? null : rawPassword.toString(); }
    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
        String raw = rawPassword == null ? null : rawPassword.toString();
        return raw != null && raw.equals(encodedPassword);
    }
}
