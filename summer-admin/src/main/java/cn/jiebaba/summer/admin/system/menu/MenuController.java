package cn.jiebaba.summer.admin.system.menu;

import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.security.annotation.PreAuthorize;
import cn.jiebaba.summer.web.annotation.DeleteMapping;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.PutMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.List;

/**
 * 菜单管理接口：菜单树列表、详情、新增、修改与删除。
 * 所有端点声明精确权限标识，安全激活（Task 7）后由框架强制执行。
 */
@RestController
@RequestMapping("/api/system/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /** 全部菜单列表（菜单树由前端基于 parentId 组装）。 */
    @PreAuthorize(authorities = {"system:menu:query"})
    @GetMapping("/list")
    public R<List<SysMenu>> list() {
        return R.ok(menuService.list());
    }

    /** 菜单详情。 */
    @PreAuthorize(authorities = {"system:menu:query"})
    @GetMapping("/{menuId}")
    public R<SysMenu> get(@PathVariable("menuId") Long menuId) {
        return R.ok(menuService.get(menuId));
    }

    /** 新增菜单。 */
    @PreAuthorize(authorities = {"system:menu:add"})
    @PostMapping
    public R<Long> create(@RequestBody SysMenu menu) {
        return R.ok(menuService.create(menu));
    }

    /** 修改菜单。 */
    @PreAuthorize(authorities = {"system:menu:edit"})
    @PutMapping
    public R<Void> update(@RequestBody SysMenu menu) {
        menuService.update(menu);
        return R.ok();
    }

    /** 删除菜单（有子菜单或已分配角色时拒绝）。 */
    @PreAuthorize(authorities = {"system:menu:remove"})
    @DeleteMapping("/{menuId}")
    public R<Void> delete(@PathVariable("menuId") Long menuId) {
        menuService.delete(menuId);
        return R.ok();
    }
}
