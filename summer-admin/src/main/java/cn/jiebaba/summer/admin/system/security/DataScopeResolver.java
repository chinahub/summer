package cn.jiebaba.summer.admin.system.security;

import cn.jiebaba.summer.admin.system.dept.SysDept;
import cn.jiebaba.summer.admin.system.dept.SysDeptMapper;
import cn.jiebaba.summer.admin.system.role.SysRole;
import cn.jiebaba.summer.admin.system.role.SysRoleDept;
import cn.jiebaba.summer.admin.system.role.SysRoleDeptMapper;
import cn.jiebaba.summer.admin.system.role.SysRoleMapper;
import cn.jiebaba.summer.admin.system.role.SysUserRole;
import cn.jiebaba.summer.admin.system.role.SysUserRoleMapper;
import cn.jiebaba.summer.admin.system.user.SysUser;
import cn.jiebaba.summer.admin.system.user.SysUserMapper;
import cn.jiebaba.summer.core.annotation.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 数据范围解析器：根据当前用户全部启用且未逻辑删除的角色求可见范围并集，返回
 * {@link DataScopeRule}。超级管理员（user_id=1）绕过限制直接无限制；
 * 多角色按并集处理，任一 ALL 即无限制；无角色/空自定义集合/未知范围
 * 均按拒绝默认收敛为永假条件，不扩大权限。
 */
@Service
public class DataScopeResolver {

    /** 超级管理员固定 user_id=1（RuoYi 惯例），在数据范围计算中绕过限制。 */
    public static final long SUPER_ADMIN_ID = 1L;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysDeptMapper deptMapper;

    public DataScopeResolver(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                             SysRoleMapper roleMapper, SysRoleDeptMapper roleDeptMapper,
                             SysDeptMapper deptMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.deptMapper = deptMapper;
    }

    /**
     * 解析指定用户的数据范围：读取其用户-角色关系与启用角色，按
     * ALL/CUSTOM/DEPT/DEPT_AND_CHILDREN/SELF 语义求并集；
     * DEPT_AND_CHILDREN 基于部门 ancestors 路径计算全部后代部门。
     *
     * @param userId 用户 ID
     * @return 数据范围规则；用户不存在或无有效角色时为永假规则
     */
    public DataScopeRule resolve(Long userId) {
        if (userId != null && userId == SUPER_ADMIN_ID) {
            return DataScopeRule.unrestricted();
        }
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            return DataScopeRule.deny();
        }
        Set<Long> roleIds = new HashSet<>();
        for (SysUserRole userRole : userRoleMapper.selectList()) {
            if (Objects.equals(userRole.getUserId(), userId)) {
                roleIds.add(userRole.getRoleId());
            }
        }
        List<SysRole> roles = new ArrayList<>();
        for (SysRole role : roleMapper.selectList()) {
            // 仅启用且未逻辑删除的角色参与数据范围（与 DatabaseUserDetailsService 一致，拒绝默认）
            if (roleIds.contains(role.getRoleId()) && "0".equals(role.getStatus())
                    && "0".equals(role.getDelFlag())) {
                roles.add(role);
            }
        }
        if (roles.isEmpty()) {
            return DataScopeRule.deny();
        }
        List<SysDept> allDepts = deptMapper.selectList();
        Set<Long> deptIds = new LinkedHashSet<>();
        boolean includeSelf = false;
        for (SysRole role : roles) {
            DataScopeType type = DataScopeType.fromCode(role.getDataScope());
            if (type == null) {
                continue;
            }
            switch (type) {
                case ALL -> {
                    return DataScopeRule.unrestricted();
                }
                case CUSTOM -> {
                    for (SysRoleDept roleDept : roleDeptMapper.selectList()) {
                        if (Objects.equals(roleDept.getRoleId(), role.getRoleId())) {
                            deptIds.add(roleDept.getDeptId());
                        }
                    }
                }
                case DEPT -> {
                    if (user.getDeptId() != null) {
                        deptIds.add(user.getDeptId());
                    }
                }
                case DEPT_AND_CHILDREN -> {
                    if (user.getDeptId() != null) {
                        deptIds.add(user.getDeptId());
                        deptIds.addAll(descendantIds(allDepts, user.getDeptId()));
                    }
                }
                case SELF -> includeSelf = true;
            }
        }
        return DataScopeRule.of(deptIds, includeSelf);
    }

    /**
     * 基于 ancestors 路径前缀计算部门的全部后代部门 ID
     * （ancestors 等于"自身路径"或以"自身路径,"开头的部门）。
     *
     * @param allDepts 全部未删除部门
     * @param deptId   目标部门 ID
     * @return 后代部门 ID 集合（不含自身）
     */
    private static Set<Long> descendantIds(List<SysDept> allDepts, Long deptId) {
        SysDept base = null;
        for (SysDept dept : allDepts) {
            if (Objects.equals(dept.getDeptId(), deptId)) {
                base = dept;
                break;
            }
        }
        if (base == null) {
            return Set.of();
        }
        String path = base.getAncestors() + "," + deptId;
        Set<Long> ids = new LinkedHashSet<>();
        for (SysDept dept : allDepts) {
            String ancestors = dept.getAncestors();
            if (ancestors != null
                    && (ancestors.equals(path) || ancestors.startsWith(path + ","))) {
                ids.add(dept.getDeptId());
            }
        }
        return ids;
    }
}
