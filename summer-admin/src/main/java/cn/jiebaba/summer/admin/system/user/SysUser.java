package cn.jiebaba.summer.admin.system.user;

import cn.jiebaba.summer.admin.common.persistence.BaseEntity;
import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.annotation.TableId;
import cn.jiebaba.summer.data.annotation.TableLogic;
import cn.jiebaba.summer.data.annotation.TableName;

import java.time.LocalDateTime;

/** 用户实体：登录名、部门归属、状态、BCrypt 密码哈希和基础资料。 */
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 用户ID，user_id=1 固定为超级管理员 */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /** 部门ID */
    private Long deptId;

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

    /** 密码 BCrypt 哈希 */
    private String password;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    @TableLogic
    private String delFlag;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginDate;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(String delFlag) {
        this.delFlag = delFlag;
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
}
