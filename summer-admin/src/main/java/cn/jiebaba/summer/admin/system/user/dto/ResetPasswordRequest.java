package cn.jiebaba.summer.admin.system.user.dto;

import cn.jiebaba.summer.web.validation.NotBlank;
import cn.jiebaba.summer.web.validation.Size;

/** 管理端重置密码请求体：仅允许输入新密码，BCrypt 编码后落库，绝不回显。 */
public class ResetPasswordRequest {

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在6-64之间")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
