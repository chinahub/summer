package cn.jiebaba.summer.admin.auth.captcha;

/** 验证码文本生成器：生产实现生成随机字符，测试注入确定性序列。 */
@FunctionalInterface
public interface CaptchaCodeGenerator {

    /** 生成一屏验证码文本（通常为 4 位）。 */
    String generate();
}
