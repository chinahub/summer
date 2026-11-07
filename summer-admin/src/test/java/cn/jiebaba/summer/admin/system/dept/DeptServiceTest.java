package cn.jiebaba.summer.admin.system.dept;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.system.security.DataScopeRule;
import cn.jiebaba.summer.admin.system.user.SysUser;
import cn.jiebaba.summer.admin.system.user.SysUserMapper;
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 部门服务纯内存单测：有子节点/有归属用户时禁止删除、移动部门同步更新
 * 全部后代 ancestors、禁止移动成环、新增生成 ancestors 路径，以及按数据范围
 * 过滤可见部门列表。
 */
class DeptServiceTest {

    private DeptFake deptMapper;
    private UserFake userMapper;
    private DeptService deptService;

    @BeforeEach
    void setUp() {
        deptMapper = new DeptFake();
        userMapper = new UserFake();
        deptService = new DeptService(deptMapper, userMapper);
        dept(1L, 0L, "0", "总公司");
        dept(2L, 1L, "0,1", "研发部门");
        dept(3L, 1L, "0,1", "市场部门");
        dept(4L, 2L, "0,1,2", "质量测试组");
    }

    @Test
    void deleteRejectsWhenChildrenExist() {
        BusinessException ex = assertThrows(BusinessException.class, () -> deptService.delete(1L));
        assertEquals(409, ex.status());
        assertEquals("0", deptMapper.selectById(1L).getDelFlag());
    }

    @Test
    void deleteRejectsWhenUsersAssigned() {
        user(30L, 4L);

        BusinessException ex = assertThrows(BusinessException.class, () -> deptService.delete(4L));
        assertEquals(409, ex.status());
    }

    @Test
    void deleteRejectsUnknownDeptWith404() {
        BusinessException ex = assertThrows(BusinessException.class, () -> deptService.delete(99L));
        assertEquals(404, ex.status());
    }

    @Test
    void deleteRemovesLeafDeptLogically() {
        SysDept before = deptMapper.selectById(3L);
        deptService.delete(3L);

        assertEquals("2", before.getDelFlag());
        assertEquals(3, deptService.list().size());
    }

    @Test
    void moveUpdatesAllDescendantAncestors() {
        SysDept move = new SysDept();
        move.setDeptId(2L);
        move.setParentId(3L);
        deptService.update(move);

        assertEquals(3L, deptMapper.selectById(2L).getParentId());
        assertEquals("0,1,3", deptMapper.selectById(2L).getAncestors());
        assertEquals("0,1,3,2", deptMapper.selectById(4L).getAncestors());
        // 未受影响的兄弟部门保持不变
        assertEquals("0,1", deptMapper.selectById(3L).getAncestors());
    }

    @Test
    void moveRejectsCycleToDescendant() {
        SysDept move = new SysDept();
        move.setDeptId(1L);
        move.setParentId(4L);

        BusinessException ex = assertThrows(BusinessException.class, () -> deptService.update(move));
        assertEquals(409, ex.status());
    }

    @Test
    void moveRejectsParentItself() {
        SysDept move = new SysDept();
        move.setDeptId(2L);
        move.setParentId(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> deptService.update(move));
        assertEquals(409, ex.status());
    }

    @Test
    void moveRejectsMissingParent() {
        SysDept move = new SysDept();
        move.setDeptId(2L);
        move.setParentId(99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> deptService.update(move));
        assertEquals(404, ex.status());
    }

    @Test
    void createBuildsAncestorsFromParent() {
        SysDept child = new SysDept();
        child.setParentId(4L);
        child.setDeptName("自动化组");
        Long id = deptService.create(child);

        assertEquals("0,1,2,4", deptMapper.selectById(id).getAncestors());
        assertEquals("0", deptMapper.selectById(id).getDelFlag());
    }

    @Test
    void createRootUsesZeroAncestor() {
        SysDept root = new SysDept();
        root.setParentId(0L);
        root.setDeptName("分公司");
        Long id = deptService.create(root);

        assertEquals("0", deptMapper.selectById(id).getAncestors());
    }

    @Test
    void createRejectsMissingParent() {
        SysDept orphan = new SysDept();
        orphan.setParentId(99L);
        orphan.setDeptName("孤儿部门");

        BusinessException ex = assertThrows(BusinessException.class, () -> deptService.create(orphan));
        assertEquals(404, ex.status());
    }

    @Test
    void visibleListFiltersByScopeRule() {
        assertEquals(4, deptService.visibleList(DataScopeRule.unrestricted(), 10L).size());
        assertEquals(2, deptService.visibleList(DataScopeRule.of(Set.of(2L, 4L), false), 10L).size());
        assertTrue(deptService.visibleList(DataScopeRule.deny(), 10L).isEmpty());
    }

    @Test
    void visibleListIncludesOwnDeptForSelfScope() {
        user(10L, 3L);

        List<SysDept> visible = deptService.visibleList(DataScopeRule.of(Set.of(), true), 10L);
        assertEquals(1, visible.size());
        assertEquals(3L, visible.get(0).getDeptId());
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

    /** 带逻辑删除语义的 sys_dept 内存 Mapper。 */
    private static final class DeptFake extends InMemoryBaseMapper<SysDept> implements SysDeptMapper {
        @Override
        protected Long idOf(SysDept entity) {
            return entity.getDeptId();
        }

        @Override
        public int deleteById(Object id) {
            SysDept dept = selectById(id);
            if (dept == null) {
                return 0;
            }
            dept.setDelFlag("2");
            return 1;
        }

        @Override
        public SysDept selectById(Object id) {
            return rows.stream()
                    .filter(d -> java.util.Objects.equals(d.getDeptId(), id) && !"2".equals(d.getDelFlag()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<SysDept> selectList() {
            return rows.stream().filter(d -> !"2".equals(d.getDelFlag())).toList();
        }
    }

    /** 带逻辑删除语义的 sys_user 内存 Mapper。 */
    private static final class UserFake extends InMemoryBaseMapper<SysUser> implements SysUserMapper {
        @Override
        protected Long idOf(SysUser entity) {
            return entity.getUserId();
        }

        @Override
        public List<SysUser> selectList() {
            return rows.stream().filter(u -> !"2".equals(u.getDelFlag())).toList();
        }
    }
}
