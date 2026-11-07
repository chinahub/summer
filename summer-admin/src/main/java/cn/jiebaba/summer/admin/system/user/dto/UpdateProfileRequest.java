package cn.jiebaba.summer.admin.system.user.dto;

import cn.jiebaba.summer.web.validation.Size;

/**
 * 个人资料更新请求体：仅白名单字段（昵称/邮箱/手机号/性别）允许修改，
 * 登录名、部门、状态、角色等敏感字段一律不在此入口暴露。
 */
public class UpdateProfileRequest {

    /** 昵称 */
    @Size(max = 30, message = "昵称长度不能超过30")
    private String nickName;

    /** 邮箱 */
    @Size(max = 50, message = "邮箱长度不能超过50")
    private String email;

    /** 手机号 */
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phonenumber;

    /** 性别（0男 1女 2未知） */
    private String sex;

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhonenumber() {
        return phonenumber;
    }

    public void setPhonenumber(String phonenumber) {
        this.phonenumber = phonenumber;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }
}
