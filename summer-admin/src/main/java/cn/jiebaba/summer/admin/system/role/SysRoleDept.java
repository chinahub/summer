package cn.jiebaba.summer.admin.system.role;

import cn.jiebaba.summer.data.annotation.TableName;

/** 角色自定义数据范围的部门集合（物理删除，无审计与逻辑删除字段）。 */
@TableName("sys_role_dept")
public class SysRoleDept {

    /** 角色ID */
    private Long roleId;

    /** 部门ID */
    private Long deptId;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }
}
