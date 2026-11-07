package cn.jiebaba.summer.admin.system.role;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.admin.common.api.TableData;
import cn.jiebaba.summer.admin.system.security.DataScopeType;
import cn.jiebaba.summer.data.page.IPage;
import cn.jiebaba.summer.security.annotation.PreAuthorize;
import cn.jiebaba.summer.web.annotation.DeleteMapping;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.PutMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.Set;

/**
 * 角色管理接口：条件分页列表、详情、新增、修改、删除、菜单授权与数据范围授权。
 * 所有端点声明精确权限标识，安全激活（Task 7）后由框架强制执行。
 */
@RestController
@RequestMapping("/api/system/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** 角色分页列表：支持名称/权限字符模糊与状态过滤。 */
    @PreAuthorize(authorities = {"system:role:query"})
    @GetMapping("/list")
    public R<TableData<SysRole>> list(PageQuery pageQuery,
                                      @RequestParam(value = "roleName", required = false) String roleName,
                                      @RequestParam(value = "roleKey", required = false) String roleKey,
                                      @RequestParam(value = "status", required = false) String status) {
        IPage<SysRole> page = roleService.page(pageQuery, roleName, roleKey, status);
        return R.ok(TableData.of(page));
    }

    /** 角色详情。 */
    @PreAuthorize(authorities = {"system:role:query"})
    @GetMapping("/{roleId}")
    public R<SysRole> get(@PathVariable("roleId") Long roleId) {
        return R.ok(roleService.get(roleId));
    }

    /** 新增角色。 */
    @PreAuthorize(authorities = {"system:role:add"})
    @PostMapping
    public R<Long> create(@RequestBody SysRole role) {
        return R.ok(roleService.create(role));
    }

    /** 修改角色。 */
    @PreAuthorize(authorities = {"system:role:edit"})
    @PutMapping
    public R<Void> update(@RequestBody SysRole role) {
        roleService.update(role);
        return R.ok();
    }

    /** 删除角色（内置超级管理员角色或已分配用户时拒绝）。 */
    @PreAuthorize(authorities = {"system:role:remove"})
    @DeleteMapping("/{roleId}")
    public R<Void> delete(@PathVariable("roleId") Long roleId) {
        roleService.delete(roleId);
        return R.ok();
    }

    /** 替换角色菜单授权（整体一个事务，失败回滚）。 */
    @PreAuthorize(authorities = {"system:role:edit"})
    @PutMapping("/{roleId}/menus")
    public R<Void> assignMenus(@PathVariable("roleId") Long roleId,
                               @RequestBody MenuAssignBody body) {
        roleService.assignMenus(roleId, body == null ? null : body.getMenuIds());
        return R.ok();
    }

    /** 保存角色数据范围：CUSTOM 显式保存部门集合，其余类型清空自定义集合。 */
    @PreAuthorize(authorities = {"system:role:dataScope"})
    @PutMapping("/{roleId}/data-scope")
    public R<Void> assignDataScope(@PathVariable("roleId") Long roleId,
                                   @RequestBody DataScopeBody body) {
        roleService.assignDataScope(roleId,
                body == null ? null : body.getScopeType(),
                body == null ? null : body.getDeptIds());
        return R.ok();
    }

    /** 菜单授权请求体：menuIds 为空表示清空角色全部菜单授权。 */
    public static class MenuAssignBody {

        /** 菜单 ID 集合 */
        private Set<Long> menuIds;

        public Set<Long> getMenuIds() {
            return menuIds;
        }

        public void setMenuIds(Set<Long> menuIds) {
            this.menuIds = menuIds;
        }
    }

    /** 数据范围授权请求体：scopeType 为枚举名（ALL/CUSTOM/DEPT/DEPT_AND_CHILDREN/SELF）。 */
    public static class DataScopeBody {

        /** 数据范围枚举 */
        private DataScopeType scopeType;

        /** 自定义部门集合（仅 CUSTOM 使用） */
        private Set<Long> deptIds;

        public DataScopeType getScopeType() {
            return scopeType;
        }

        public void setScopeType(DataScopeType scopeType) {
            this.scopeType = scopeType;
        }

        public Set<Long> getDeptIds() {
            return deptIds;
        }

        public void setDeptIds(Set<Long> deptIds) {
            this.deptIds = deptIds;
        }
    }
}
