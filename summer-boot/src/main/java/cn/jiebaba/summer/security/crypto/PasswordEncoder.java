package cn.jiebaba.summer.security.crypto;

/**
 * 对原始密码进行编码，并校验其与已存储编码值是否匹配。
 * 对应 {@code org.springframework.security.crypto.password.PasswordEncoder}。
 */
public interface PasswordEncoder {
    /** 将原始密码编码为可存储形式（如 bcrypt 哈希）。 */
    String encode(CharSequence rawPassword);

    /** 校验原始密码是否与此前编码结果匹配。 */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}
