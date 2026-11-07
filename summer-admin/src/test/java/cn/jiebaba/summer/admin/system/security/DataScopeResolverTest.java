package cn.jiebaba.summer.admin.system.security;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
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
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据范围解析与应用的纯内存单测：以固定内存 Mapper 覆盖
 * ALL/CUSTOM/DEPT/DEPT_AND_CHILDREN/SELF 五种范围、多角色并集、
 * 空角色/空自定义集合的永假拒绝、SELF 与 DEPT 的 OR 组合以及按 ID 越权 403。
 */
class DataScopeResolverTest {

    private DeptFake deptMapper;
    private UserFake userMapper;
    private UserRoleFake userRoleMapper;
    private RoleFake roleMapper;
    private RoleDeptFake roleDeptMapper;
    private DataScopeResolver resolver;

    @BeforeEach
    void setUp() {
        deptMapper = new DeptFake();
        userMapper = new UserFake();
        userRoleMapper = new UserRoleFake();
        roleMapper = new RoleFake();
        roleDeptMapper = new RoleDeptFake();
        resolver = new DataScopeResolver(userMapper, userRoleMapper, roleMapper, roleDeptMapper, deptMapper);
        // 部门树：1(总公司) → 2(研发)、3(市场)，2 → 4(质量测试)
        dept(1L, 0L, "0", "总公司");
        dept(2L, 1L, "0,1", "研发部门");
        dept(3L, 1L, "0,1", "市场部门");
        dept(4L, 2L, "0,1,2", "质量测试组");
    }

    @Test
    void allScopeIsUnrestricted() {
        user(10L, 2L);
        role(20L, "1", "0");
        assign(10L, 20L);

        DataScopeRule rule = resolver.resolve(10L);
        assertTrue(rule.isUnrestricted());

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        rule.applyToUser(wrapper, 10L);
        assertTrue(wrapper.isEmpty());
    }

    @Test
    void deptScopeLimitsToOwnDept() {
        user(10L, 2L);
        role(20L, "3", "0");
        assign(10L, 20L);

        DataScopeRule rule = resolver.resolve(10L);
        assertFalse(rule.isUnrestricted());
        assertFalse(rule.includeSelf());
        assertEquals(Set.of(2L), rule.deptIds());
    }

    @Test
    void deptAndChildrenIncludesDescendants() {
        user(10L, 2L);
        role(20L, "4", "0");
        assign(10L, 20L);

        assertEquals(Set.of(2L, 4L), resolver.resolve(10L).deptIds());
    }

    @Test
    void selfScopeConvertsToUserIdCondition() {
        user(10L, 2L);
        role(20L, "5", "0");
        assign(10L, 20L);

        DataScopeRule rule = resolver.resolve(10L);
        assertTrue(rule.includeSelf());
        assertTrue(rule.deptIds().isEmpty());

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        rule.applyToUser(wrapper, 10L);
        assertEquals("userId = ?", wrapper.sqlSegment());
        assertEquals(List.of(10L), wrapper.params());
    }

    @Test
    void customScopeUsesRoleDeptEntries() {
        user(10L, 2L);
        role(20L, "2", "0");
        assign(10L, 20L);
        customDept(20L, 3L);

        assertEquals(Set.of(3L), resolver.resolve(10L).deptIds());
    }

    @Test
    void multipleRolesUnionScopes() {
        user(10L, 2L);
        role(20L, "3", "0");
        role(21L, "2", "0");
        assign(10L, 20L);
        assign(10L, 21L);
        customDept(21L, 3L);

        assertEquals(Set.of(2L, 3L), resolver.resolve(10L).deptIds());
    }

    @Test
    void anyAllRoleGrantsUnrestricted() {
        user(10L, 2L);
        role(20L, "5", "0");
        role(21L, "1", "0");
        assign(10L, 20L);
        assign(10L, 21L);

        assertTrue(resolver.resolve(10L).isUnrestricted());
    }

    @Test
    void emptyCustomSetDeniesWithAlwaysFalseCondition() {
        user(10L, 2L);
        role(20L, "2", "0");
        assign(10L, 20L);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        resolver.resolve(10L).applyToUser(wrapper, 10L);
        assertEquals("1=0", wrapper.sqlSegment());
        assertTrue(wrapper.params().isEmpty());
    }

    @Test
    void noRolesDeniesWithAlwaysFalseCondition() {
        user(10L, 2L);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        resolver.resolve(10L).applyToUser(wrapper, 10L);
        assertEquals("1=0", wrapper.sqlSegment());
    }

    @Test
    void disabledRoleIsIgnoredAndDenies() {
        user(10L, 2L);
        role(20L, "1", "1");
        assign(10L, 20L);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        resolver.resolve(10L).applyToUser(wrapper, 10L);
        assertEquals("1=0", wrapper.sqlSegment());
    }

    @Test
    void unknownScopeCodeContributesNothing() {
        user(10L, 2L);
        role(20L, "9", "0");
        assign(10L, 20L);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        resolver.resolve(10L).applyToUser(wrapper, 10L);
        assertEquals("1=0", wrapper.sqlSegment());
    }

    @Test
    void selfAndDeptCombineWithOr() {
        user(10L, 2L);
        role(20L, "3", "0");
        role(21L, "5", "0");
        assign(10L, 20L);
        assign(10L, 21L);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        resolver.resolve(10L).applyToUser(wrapper, 10L);
        assertEquals("(dept_id IN (?) OR userId = ?)", wrapper.sqlSegment());
        assertEquals(List.of(2L, 10L), wrapper.params());
    }

    @Test
    void assertVisibleRejectsOutOfScopeUserWith403() {
        user(10L, 2L);
        user(11L, 3L);
        role(20L, "3", "0");
        assign(10L, 20L);

        DataScopeRule rule = resolver.resolve(10L);
        SysUser foreign = userMapper.selectById(11L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> rule.assertVisible(foreign, 10L));
        assertEquals(403, ex.status());
    }

    @Test
    void assertVisibleAllowsSameDeptAndSelf() {
        user(10L, 2L);
        user(12L, 2L);
        role(20L, "3", "0");
        assign(10L, 20L);

        DataScopeRule rule = resolver.resolve(10L);
        assertDoesNotThrow(() -> rule.assertVisible(userMapper.selectById(12L), 10L));
    }

    @Test
    void assertVisibleForSelfScopeOnlyAllowsOwnId() {
        user(10L, 2L);
        user(12L, 2L);
        role(20L, "5", "0");
        assign(10L, 20L);

        DataScopeRule rule = resolver.resolve(10L);
        assertDoesNotThrow(() -> rule.assertVisible(userMapper.selectById(10L), 10L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> rule.assertVisible(userMapper.selectById(12L), 10L));
        assertEquals(403, ex.status());
    }

    @Test
    void superAdminBypassesScopeResolution() {
        // user_id=1 不查角色直接无限制
        assertTrue(resolver.resolve(1L).isUnrestricted());
    }

    private void dept(Long id, Long parentId, String ancestors, String name) {
        SysDept d = new SysDept();
        d.setDeptId(id);
        d.setParentId(parentId);
        d.setAncestors(ancestors);
        d.setDeptName(name);
        d.setDelFlag("0");
        deptMapper.insert(d);
    }

    private void user(Long id, Long deptId) {
        SysUser u = new SysUser();
        u.setUserId(id);
        u.setDeptId(deptId);
        u.setDelFlag("0");
        userMapper.insert(u);
    }

    private void role(Long id, String dataScope, String status) {
        SysRole r = new SysRole();
        r.setRoleId(id);
        r.setDataScope(dataScope);
        r.setStatus(status);
        roleMapper.insert(r);
    }

    private void assign(Long userId, Long roleId) {
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        userRoleMapper.insert(ur);
    }

    private void customDept(Long roleId, Long deptId) {
        SysRoleDept rd = new SysRoleDept();
        rd.setRoleId(roleId);
        rd.setDeptId(deptId);
        roleDeptMapper.insert(rd);
    }

    /** sys_dept 内存 Mapper。 */
    private static final class DeptFake extends InMemoryBaseMapper<SysDept> implements SysDeptMapper {
        @Override
        protected Long idOf(SysDept entity) {
            return entity.getDeptId();
        }
    }

    /** sys_user 内存 Mapper。 */
    private static final class UserFake extends InMemoryBaseMapper<SysUser> implements SysUserMapper {
        @Override
        protected Long idOf(SysUser entity) {
            return entity.getUserId();
        }
    }

    /** sys_role 内存 Mapper。 */
    private static final class RoleFake extends InMemoryBaseMapper<SysRole> implements SysRoleMapper {
        @Override
        protected Long idOf(SysRole entity) {
            return entity.getRoleId();
        }
    }

    /** sys_user_role 内存 Mapper（无主键）。 */
    private static final class UserRoleFake extends InMemoryBaseMapper<SysUserRole> implements SysUserRoleMapper {
        @Override
        protected Long idOf(SysUserRole entity) {
            return null;
        }
    }

    /** sys_role_dept 内存 Mapper（无主键）。 */
    private static final class RoleDeptFake extends InMemoryBaseMapper<SysRoleDept> implements SysRoleDeptMapper {
        @Override
        protected Long idOf(SysRoleDept entity) {
            return null;
        }
    }
}
