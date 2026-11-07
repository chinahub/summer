package cn.jiebaba.summer.admin.system.role;

import cn.jiebaba.summer.admin.common.persistence.BaseEntity;
import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.annotation.TableId;
import cn.jiebaba.summer.data.annotation.TableLogic;
import cn.jiebaba.summer.data.annotation.TableName;

/** 角色实体：角色名、权限字符、数据范围和状态。 */
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色ID */
    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;

    /** 角色名称 */
    private String roleName;

    /** 权限字符 */
    private String roleKey;

    /** 显示排序 */
    private Integer roleSort;

    /** 数据范围（1全部 2自定义 3本部门 4本部门及以下 5仅本人） */
    private String dataScope;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    @TableLogic
    private String delFlag;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    public Integer getRoleSort() {
        return roleSort;
    }

    public void setRoleSort(Integer roleSort) {
        this.roleSort = roleSort;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
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
}
