package cn.jiebaba.summer.admin.system.role;

import cn.jiebaba.summer.data.annotation.TableName;

/** 用户与角色多对多关系（物理删除，无审计与逻辑删除字段）。 */
@TableName("sys_user_role")
public class SysUserRole {

    /** 用户ID */
    private Long userId;

    /** 角色ID */
    private Long roleId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
