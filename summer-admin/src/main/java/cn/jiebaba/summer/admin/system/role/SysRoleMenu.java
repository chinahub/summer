package cn.jiebaba.summer.admin.system.role;

import cn.jiebaba.summer.data.annotation.TableName;

/** 角色与菜单多对多关系（物理删除，无审计与逻辑删除字段）。 */
@TableName("sys_role_menu")
public class SysRoleMenu {

    /** 角色ID */
    private Long roleId;

    /** 菜单ID */
    private Long menuId;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }
}
