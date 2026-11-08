package cn.jiebaba.summer.admin.system.role;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.common.log.OperLog;
import cn.jiebaba.summer.admin.system.dept.SysDept;
import cn.jiebaba.summer.admin.system.dept.SysDeptMapper;
import cn.jiebaba.summer.admin.system.menu.SysMenu;
import cn.jiebaba.summer.admin.system.menu.SysMenuMapper;
import cn.jiebaba.summer.admin.system.security.DataScopeType;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.page.IPage;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.data.support.SqlExecutor;
import cn.jiebaba.summer.data.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 角色服务：角色分页查询与 CRUD（名称/权限字符唯一性校验）、内置超级角色保护、
 * 菜单授权替换（assignMenus）与数据范围授权（assignDataScope）。
 * 关系表（sys_role_menu/sys_role_dept）为物理删除表且 Central BaseMapper
 * 缺少条件删除，统一经 {@link SqlExecutor} 预编译参数删除；
 * 关系替换整体在一个事务内完成：先清旧关系再校验并写入新关系，
 * 任何一步失败整体回滚，绝不留下半套关系。
 */
@Service
public class RoleService {

    /** 内置超级管理员角色固定 role_id=1（RuoYi 惯例），不可删除。 */
    public static final long BUILT_IN_SUPER_ROLE_ID = 1L;

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final SysDeptMapper deptMapper;
    private final SqlExecutor sqlExecutor;

    public RoleService(SysRoleMapper roleMapper, SysRoleMenuMapper roleMenuMapper,
                       SysRoleDeptMapper roleDeptMapper, SysUserRoleMapper userRoleMapper,
                       SysMenuMapper menuMapper, SysDeptMapper deptMapper,
                       SqlExecutor sqlExecutor) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.userRoleMapper = userRoleMapper;
        this.menuMapper = menuMapper;
        this.deptMapper = deptMapper;
        this.sqlExecutor = sqlExecutor;
    }

    /**
     * 分页查询角色列表：名称/权限字符模糊匹配、状态精确过滤，按显示排序升序。
     * 注意框架 Wrapper 查询不自动附加逻辑删除条件，需显式声明 del_flag = '0'。
     *
     * @param query    分页参数
     * @param roleName 角色名称模糊词（可空）
     * @param roleKey  权限字符模糊词（可空）
     * @param status   状态过滤（可空）
     * @return 分页结果
     */
    public IPage<SysRole> page(PageQuery query, String roleName, String roleKey, String status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq("del_flag", "0");
        if (roleName != null && !roleName.isBlank()) {
            wrapper.like(SysRole::getRoleName, roleName);
        }
        if (roleKey != null && !roleKey.isBlank()) {
            wrapper.like(SysRole::getRoleKey, roleKey);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysRole::getStatus, status);
        }
        wrapper.orderByAsc(SysRole::getRoleSort);
        return roleMapper.selectPage(query.toPage(), wrapper);
    }

    /** 按 ID 查询角色，不存在抛 404。 */
    public SysRole get(Long roleId) {
        if (roleId == null) {
            throw new BusinessException(400, "角色ID不能为空");
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        return role;
    }

    /**
     * 新增角色：名称与权限字符必填且在未删除角色中唯一；数据范围、排序、
     * 状态与删除标志取默认值（数据范围默认 ALL）。
     *
     * @param role 角色实体（roleName/roleKey 必填）
     * @return 生成的角色 ID
     */
    @OperLog(module = "角色管理", action = "新增")
    public Long create(SysRole role) {
        validateUnique(role, null);
        if (role.getDataScope() == null || role.getDataScope().isBlank()) {
            role.setDataScope(DataScopeType.ALL.code());
        }
        if (role.getRoleSort() == null) {
            role.setRoleSort(0);
        }
        if (role.getStatus() == null || role.getStatus().isBlank()) {
            role.setStatus("0");
        }
        role.setDelFlag("0");
        roleMapper.insert(role);
        return role.getRoleId();
    }

    /**
     * 更新角色：目标必须存在；非空字段覆盖旧值后整体校验（名称与权限字符
     * 唯一性排除自身）。
     *
     * @param role 变更字段（roleId 必填；为 null 的字段保持不变）
     */
    @OperLog(module = "角色管理", action = "修改")
    public void update(SysRole role) {
        if (role == null || role.getRoleId() == null) {
            throw new BusinessException(400, "角色ID不能为空");
        }
        SysRole existing = get(role.getRoleId());
        if (role.getRoleName() != null) {
            existing.setRoleName(role.getRoleName());
        }
        if (role.getRoleKey() != null) {
            existing.setRoleKey(role.getRoleKey());
        }
        if (role.getRoleSort() != null) {
            existing.setRoleSort(role.getRoleSort());
        }
        if (role.getDataScope() != null && !role.getDataScope().isBlank()) {
            existing.setDataScope(role.getDataScope());
        }
        if (role.getStatus() != null && !role.getStatus().isBlank()) {
            existing.setStatus(role.getStatus());
        }
        validateUnique(existing, existing.getRoleId());
        roleMapper.updateById(existing);
    }

    /**
     * 删除角色（逻辑删除 del_flag 0→2）：内置超级管理员角色或被用户引用时
     * 抛 409 拒绝；删除同时清理角色-菜单与角色-部门关系，同一事务内完成。
     *
     * @param roleId 角色 ID
     */
    @OperLog(module = "角色管理", action = "删除")
    @Transactional
    public void delete(Long roleId) {
        SysRole role = get(roleId);
        if (roleId == BUILT_IN_SUPER_ROLE_ID || "admin".equals(role.getRoleKey())) {
            throw new BusinessException(409, "内置超级管理员角色不允许删除");
        }
        for (SysUserRole userRole : userRoleMapper.selectList()) {
            if (Objects.equals(userRole.getRoleId(), roleId)) {
                throw new BusinessException(409, "角色已分配给用户，不允许删除");
            }
        }
        removeRoleMenus(roleId);
        removeRoleDepts(roleId);
        roleMapper.deleteById(roleId);
    }

    /**
     * 替换角色的菜单授权（物理关系表）：先删除旧关系，再校验并写入新关系，
     * 整体一个事务；menuIds 为 null/空表示清空授权。校验失败时事务回滚，
     * 旧关系保持完整。
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 集合（可空，表示清空）
     */
    @OperLog(module = "角色管理", action = "分配")
    @Transactional
    public void assignMenus(Long roleId, Set<Long> menuIds) {
        get(roleId);
        Set<Long> normalized = menuIds == null
                ? Set.of() : new LinkedHashSet<>(menuIds);
        removeRoleMenus(roleId);
        List<SysRoleMenu> relations = new ArrayList<>();
        for (Long menuId : normalized) {
            SysMenu menu = menuMapper.selectById(menuId);
            if (menu == null) {
                throw new BusinessException(404, "菜单不存在: " + menuId);
            }
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            relations.add(relation);
        }
        if (!relations.isEmpty()) {
            roleMenuMapper.insertBatch(relations);
        }
    }

    /**
     * 保存角色数据范围：data_scope 落五枚举编码；CUSTOM 显式保存自定义部门
     * 集合，其余类型清空 sys_role_dept 自定义集合。先更新角色、清旧关系，
     * 再校验并写入新集合，整体一个事务，失败回滚不留半套数据。
     *
     * @param roleId    角色 ID
     * @param scopeType 数据范围枚举（必填）
     * @param deptIds   自定义部门集合（仅 CUSTOM 需要，其余类型忽略）
     */
    @OperLog(module = "角色管理", action = "分配")
    @Transactional
    public void assignDataScope(Long roleId, DataScopeType scopeType, Set<Long> deptIds) {
        SysRole role = get(roleId);
        if (scopeType == null) {
            throw new BusinessException(400, "数据范围不能为空");
        }
        role.setDataScope(scopeType.code());
        roleMapper.updateById(role);
        removeRoleDepts(roleId);
        if (scopeType != DataScopeType.CUSTOM) {
            return;
        }
        Set<Long> normalized = deptIds == null ? Set.of() : new LinkedHashSet<>(deptIds);
        if (normalized.isEmpty()) {
            throw new BusinessException(400, "自定义数据范围必须选择部门");
        }
        List<SysRoleDept> relations = new ArrayList<>();
        for (Long deptId : normalized) {
            SysDept dept = deptMapper.selectById(deptId);
            if (dept == null) {
                throw new BusinessException(404, "部门不存在: " + deptId);
            }
            SysRoleDept relation = new SysRoleDept();
            relation.setRoleId(roleId);
            relation.setDeptId(deptId);
            relations.add(relation);
        }
        roleDeptMapper.insertBatch(relations);
    }

    /**
     * 物理删除角色的全部菜单关联：Central BaseMapper 缺少条件删除，
     * 使用预编译参数经 SqlExecutor 执行，杜绝 SQL 拼接注入。
     */
    protected int removeRoleMenus(Long roleId) {
        return sqlExecutor.update(new SqlBuilder.Sql(
                "DELETE FROM sys_role_menu WHERE role_id = ?", List.of(roleId)));
    }

    /** 物理删除角色的全部自定义部门关联，语义同 {@link #removeRoleMenus(Long)}。 */
    protected int removeRoleDepts(Long roleId) {
        return sqlExecutor.update(new SqlBuilder.Sql(
                "DELETE FROM sys_role_dept WHERE role_id = ?", List.of(roleId)));
    }

    /** 校验角色名称与权限字符非空且在未删除角色中唯一（excludeId 用于更新时排除自身）。 */
    private void validateUnique(SysRole role, Long excludeId) {
        if (role == null || role.getRoleName() == null || role.getRoleName().isBlank()) {
            throw new BusinessException(400, "角色名称不能为空");
        }
        if (role.getRoleKey() == null || role.getRoleKey().isBlank()) {
            throw new BusinessException(400, "权限字符不能为空");
        }
        for (SysRole other : roleMapper.selectList()) {
            if (Objects.equals(other.getRoleId(), excludeId)) {
                continue;
            }
            if (role.getRoleName().equals(other.getRoleName())) {
                throw new BusinessException(409, "角色名称已存在");
            }
            if (role.getRoleKey().equals(other.getRoleKey())) {
                throw new BusinessException(409, "权限字符已存在");
            }
        }
    }
}
