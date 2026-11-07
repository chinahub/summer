package cn.jiebaba.summer.admin.system.user.dto;

import cn.jiebaba.summer.web.validation.NotBlank;
import cn.jiebaba.summer.web.validation.NotNull;
import cn.jiebaba.summer.web.validation.Size;

import java.util.Set;

/** 新增用户请求体：登录名、部门、初始密码必填，角色/岗位关系可选。 */
public class CreateUserRequest {

    /** 登录名（唯一） */
    @NotBlank(message = "登录名不能为空")
    @Size(max = 30, message = "登录名长度不能超过30")
    private String userName;

    /** 昵称 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 30, message = "昵称长度不能超过30")
    private String nickName;

    /** 初始密码（BCrypt 编码后落库，绝不回显） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在6-64之间")
    private String password;

    /** 归属部门 */
    @NotNull(message = "归属部门不能为空")
    private Long deptId;

    /** 邮箱 */
    @Size(max = 50, message = "邮箱长度不能超过50")
    private String email;

    /** 手机号 */
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phonenumber;

    /** 性别（0男 1女 2未知） */
    private String sex;

    /** 状态（0正常 1停用，缺省 0） */
    private String status;

    /** 角色 ID 集合（可选，空表示暂不分配） */
    private Set<Long> roleIds;

    /** 岗位 ID 集合（可选，空表示暂不分配） */
    private Set<Long> postIds;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Set<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(Set<Long> roleIds) {
        this.roleIds = roleIds;
    }

    public Set<Long> getPostIds() {
        return postIds;
    }

    public void setPostIds(Set<Long> postIds) {
        this.postIds = postIds;
    }
}
