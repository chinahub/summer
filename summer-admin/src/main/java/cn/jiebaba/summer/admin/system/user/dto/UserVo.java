package cn.jiebaba.summer.admin.system.user.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象：对外暴露的资料、状态与部门/角色/岗位展示名。
 * 绝不包含密码哈希、验证码或令牌等敏感字段；关系字段以批量查询组装的
 * 展示名与 ID 列表返回。
 */
public class UserVo {

    /** 用户ID */
    private Long userId;

    /** 部门ID */
    private Long deptId;

    /** 部门名称（批量组装） */
    private String deptName;

    /** 登录名 */
    private String userName;

    /** 昵称 */
    private String nickName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phonenumber;

    /** 性别（0男 1女 2未知） */
    private String sex;

    /** 头像地址 */
    private String avatar;

    /** 状态（0正常 1停用） */
    private String status;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginDate;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 角色 ID 列表 */
    private List<Long> roleIds = List.of();

    /** 角色名称列表（与 roleIds 对齐） */
    private List<String> roleNames = List.of();

    /** 岗位 ID 列表 */
    private List<Long> postIds = List.of();

    /** 岗位名称列表（与 postIds 对齐） */
    private List<String> postNames = List.of();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public LocalDateTime getLoginDate() {
        return loginDate;
    }

    public void setLoginDate(LocalDateTime loginDate) {
        this.loginDate = loginDate;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    public void setRoleNames(List<String> roleNames) {
        this.roleNames = roleNames == null ? List.of() : List.copyOf(roleNames);
    }

    public List<Long> getPostIds() {
        return postIds;
    }

    public void setPostIds(List<Long> postIds) {
        this.postIds = postIds == null ? List.of() : List.copyOf(postIds);
    }

    public List<String> getPostNames() {
        return postNames;
    }

    public void setPostNames(List<String> postNames) {
        this.postNames = postNames == null ? List.of() : List.copyOf(postNames);
    }
}
