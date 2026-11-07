package cn.jiebaba.summer.admin.system.dept;

import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.admin.system.security.DataScopeResolver;
import cn.jiebaba.summer.web.annotation.DeleteMapping;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.PutMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.List;

/** 部门管理接口：部门树列表、按数据范围过滤的可见部门、详情、新增、修改与删除。 */
@RestController
@RequestMapping("/api/system/depts")
public class DeptController {

    private final DeptService deptService;
    private final DataScopeResolver dataScopeResolver;

    public DeptController(DeptService deptService, DataScopeResolver dataScopeResolver) {
        this.deptService = deptService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /** 全部部门列表（部门树由前端基于 parentId 组装）。 */
    @GetMapping("/list")
    public R<List<SysDept>> list() {
        return R.ok(deptService.list());
    }

    /**
     * 当前数据范围可见的部门列表。userId 由后续安全链从令牌解析，
     * 第一阶段显式传参；范围规则在服务端解析，不信任客户端传入。
     */
    @GetMapping("/visible")
    public R<List<SysDept>> visible(@RequestParam("userId") Long userId) {
        return R.ok(deptService.visibleList(dataScopeResolver.resolve(userId), userId));
    }

    /** 部门详情。 */
    @GetMapping("/{deptId}")
    public R<SysDept> get(@PathVariable("deptId") Long deptId) {
        return R.ok(deptService.get(deptId));
    }

    /** 新增部门。 */
    @PostMapping
    public R<Long> create(@RequestBody SysDept dept) {
        return R.ok(deptService.create(dept));
    }

    /** 修改部门（含移动，同步维护后代 ancestors）。 */
    @PutMapping
    public R<Void> update(@RequestBody SysDept dept) {
        deptService.update(dept);
        return R.ok();
    }

    /** 删除部门（有下级部门或归属用户时拒绝）。 */
    @DeleteMapping("/{deptId}")
    public R<Void> delete(@PathVariable("deptId") Long deptId) {
        deptService.delete(deptId);
        return R.ok();
    }
}
