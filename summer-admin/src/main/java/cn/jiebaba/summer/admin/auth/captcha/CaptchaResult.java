package cn.jiebaba.summer.admin.auth.captcha;

/** 验证码下发结果：开关、UUID 与 Base64 PNG 图片（data URL）。 */
public class CaptchaResult {

    /** 验证码开关（关闭时前端不渲染验证码输入框） */
    private final boolean captchaEnabled;

    /** 验证码唯一标识，登录时与 code 一并回传 */
    private final String uuid;

    /** Base64 PNG 图片（data:image/png;base64,...）；开关关闭时为空串 */
    private final String img;

    public CaptchaResult(boolean captchaEnabled, String uuid, String img) {
        this.captchaEnabled = captchaEnabled;
        this.uuid = uuid;
        this.img = img;
    }

    public boolean isCaptchaEnabled() {
        return captchaEnabled;
    }

    public String getUuid() {
        return uuid;
    }

    public String getImg() {
        return img;
    }
}
