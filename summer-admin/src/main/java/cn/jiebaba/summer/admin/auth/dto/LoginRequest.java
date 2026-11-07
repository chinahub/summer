package cn.jiebaba.summer.admin.auth.dto;

import cn.jiebaba.summer.web.validation.NotBlank;

/** 登录请求体：账号、密码与可选验证码（uuid + code）。 */
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码文本（验证码开启时必填） */
    private String code;

    /** 验证码 UUID，与 code 成对回传 */
    private String uuid;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
