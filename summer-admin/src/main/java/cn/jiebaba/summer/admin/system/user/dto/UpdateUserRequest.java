package cn.jiebaba.summer.admin.system.user.dto;

import cn.jiebaba.summer.web.validation.NotNull;
import cn.jiebaba.summer.web.validation.Size;

import java.util.Set;

/**
 * 修改用户请求体：userId 必填；其余字段为 null 表示保持不变；
 * roleIds/postIds 为 null 表示保持原关系，传入集合（可为空）表示整体替换。
 */
public class UpdateUserRequest {

    /** 用户 ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 登录名（null 表示不变，变更需保持唯一） */
    @Size(max = 30, message = "登录名长度不能超过30")
    private String userName;

    /** 昵称 */
    @Size(max = 30, message = "昵称长度不能超过30")
    private String nickName;

    /** 归属部门 */
    private Long deptId;

    /** 邮箱 */
    @Size(max = 50, message = "邮箱长度不能超过50")
    private String email;

    /** 手机号 */
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phonenumber;

    /** 性别（0男 1女 2未知） */
    private String sex;

    /** 状态（0正常 1停用） */
    private String status;

    /** 角色 ID 集合（null 保持，集合整体替换） */
    private Set<Long> roleIds;

    /** 岗位 ID 集合（null 保持，集合整体替换） */
    private Set<Long> postIds;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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
