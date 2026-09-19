package cn.jiebaba.summer.security.crypto;

/**
 * 不做任何哈希（直接存储明文）的 {@link PasswordEncoder}。仅用于测试或遗留系统迁移。
 * 对应 Spring 的 {@code NoOpPasswordEncoder}。
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
