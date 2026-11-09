package cn.jiebaba.summer.admin.system.role;

import cn.jiebaba.summer.admin.SummerAdminApplication;
import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.system.dept.SysDept;
import cn.jiebaba.summer.admin.system.dept.SysDeptMapper;
import cn.jiebaba.summer.admin.system.menu.SysMenu;
import cn.jiebaba.summer.admin.system.menu.SysMenuMapper;
import cn.jiebaba.summer.admin.system.security.DataScopeType;
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import cn.jiebaba.summer.admin.testsupport.MySqlTestDatabase;
import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.data.page.IPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 角色服务测试：内存替身覆盖 CRUD 校验（唯一性、内置超级角色保护、引用约束、
 * 数据范围五枚举与 CUSTOM 部门集合语义）；真实 MySQL 测试库覆盖关系替换的
 * 事务提交与回滚（失败时不出现半套关系），通过应用上下文拿到被事务拦截器
 * 代理的 RoleService Bean 验证真实 @Transactional 行为。
 */
class RoleServiceTest {

    private RoleFake roleMapper;
    private RoleMenuFake roleMenuMapper;
    private RoleDeptFake roleDeptMapper;
    private UserRoleFake userRoleMapper;
    private MenuFake menuMapper;
    private DeptFake deptMapper;
    private TestRoleService roleService;

    private static SummerApplication app;

    /**
     * 启动完整应用获取真实 Bean：只有容器中的 RoleService 才被
     * TransactionInterceptor 代理，手工 new 出来的对象无法验证事务回滚。
     * 启动前重建隔离测试库，绝不触碰 summer_admin。
     */
    @BeforeAll
    static void startApplication() {
        MySqlTestDatabase.reset();
        app = SummerApplication.run(SummerAdminApplication.class,
                new String[]{"--server.port=0", "--summer.profiles.active=test"});
    }

    @AfterAll
    static void stopApplication() {
        if (app != null) {
            app.webServer().stop();
            app.context().close();
            app = null;
        }
    }

    @BeforeEach
    void setUp() {
        roleMapper = new RoleFake();
        roleMenuMapper = new RoleMenuFake();
        roleDeptMapper = new RoleDeptFake();
        userRoleMapper = new UserRoleFake();
        menuMapper = new MenuFake();
        deptMapper = new DeptFake();
        roleService = new TestRoleService(roleMapper, roleMenuMapper, roleDeptMapper,
                userRoleMapper, menuMapper, deptMapper);
        role(1L, "超级管理员", "admin");
        role(2L, "普通角色", "common");
        role(6L, "审计角色", "auditor");
        dept(2L);
        dept(3L);
        menu(100L);
        menu(101L);
        userRole(2L, 2L);
    }

    // ---------- 内存替身：CRUD 与校验 ----------

    @Test
    void createRequiresNameAndKey() {
        SysRole role = new SysRole();
        role.setRoleKey("k");
        assertEquals(400, assertThrows(BusinessException.class,
                () -> roleService.create(role)).status());

        SysRole noKey = new SysRole();
        noKey.setRoleName("名称");
        assertEquals(400, assertThrows(BusinessException.class,
                () -> roleService.create(noKey)).status());
    }

    @Test
    void createRejectsDuplicateNameOrKey() {
        SysRole dupKey = new SysRole();
        dupKey.setRoleName("另一个名字");
        dupKey.setRoleKey("common");
        assertEquals(409, assertThrows(BusinessException.class,
                () -> roleService.create(dupKey)).status());

        SysRole dupName = new SysRole();
        dupName.setRoleName("普通角色");
        dupName.setRoleKey("other");
        assertEquals(409, assertThrows(BusinessException.class,
                () -> roleService.create(dupName)).status());
    }

    @Test
    void createAppliesDefaults() {
        SysRole role = new SysRole();
        role.setRoleName("新角色");
        role.setRoleKey("new");
        Long id = roleService.create(role);

        SysRole saved = roleMapper.selectById(id);
        assertEquals("1", saved.getDataScope());
        assertEquals("0", saved.getStatus());
        assertEquals("0", saved.getDelFlag());
        assertEquals(0, saved.getRoleSort());
    }

    @Test
    void updateUniqueExcludesSelf() {
        SysRole rename = new SysRole();
        rename.setRoleId(2L);
        rename.setRoleName("审计角色");
        rename.setRoleKey("common");
        assertEquals(409, assertThrows(BusinessException.class,
                () -> roleService.update(rename)).status());

        SysRole keep = new SysRole();
        keep.setRoleId(2L);
        keep.setRoleName("普通角色改名");
        keep.setRoleKey("common");
        roleService.update(keep);
        assertEquals("普通角色改名", roleMapper.selectById(2L).getRoleName());
    }

    @Test
    void getUnknownReturns404() {
        assertEquals(404, assertThrows(BusinessException.class,
                () -> roleService.get(999L)).status());
    }

    @Test
    void deleteRejectsBuiltInSuperRole() {
        roleMenu(1L, 100L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.delete(1L));

        assertEquals(409, ex.status());
        assertEquals("0", roleMapper.selectById(1L).getDelFlag());
        assertEquals(1, roleMenuMapper.rowsOfRole(1L).size());
    }

    @Test
    void deleteRejectsRoleAssignedToUsers() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.delete(2L));

        assertEquals(409, ex.status());
        assertEquals("0", roleMapper.selectById(2L).getDelFlag());
    }

    @Test
    void deleteRemovesUnreferencedRoleAndRelationsLogically() {
        roleMenu(6L, 100L);
        roleDept(6L, 2L);

        roleService.delete(6L);

        assertNull(roleMapper.selectById(6L));
        assertEquals("2", roleMapper.delFlagOf(6L));
        assertTrue(roleMenuMapper.rowsOfRole(6L).isEmpty());
        assertTrue(roleDeptMapper.rowsOfRole(6L).isEmpty());
    }

    @Test
    void assignMenusReplacesRelations() {
        roleMenu(6L, 100L);

        roleService.assignMenus(6L, Set.of(101L, 100L));

        assertEquals(Set.of(100L, 101L), menuIdsOfRole(6L));
    }

    @Test
    void assignMenusClearsRelationsForEmptySet() {
        roleMenu(6L, 100L);

        roleService.assignMenus(6L, null);

        assertTrue(menuIdsOfRole(6L).isEmpty());
    }

    @Test
    void assignMenusRejectsUnknownRoleOrMenu() {
        assertEquals(404, assertThrows(BusinessException.class,
                () -> roleService.assignMenus(999L, Set.of(100L))).status());
        assertEquals(404, assertThrows(BusinessException.class,
                () -> roleService.assignMenus(6L, Set.of(999L))).status());
    }

    @Test
    void assignDataScopeRequiresScopeType() {
        assertEquals(400, assertThrows(BusinessException.class,
                () -> roleService.assignDataScope(6L, null, Set.of(2L))).status());
    }

    @Test
    void assignDataScopeCustomRequiresDepts() {
        assertEquals(400, assertThrows(BusinessException.class,
                () -> roleService.assignDataScope(6L, DataScopeType.CUSTOM, null)).status());
        assertEquals(400, assertThrows(BusinessException.class,
                () -> roleService.assignDataScope(6L, DataScopeType.CUSTOM, Set.of())).status());
    }

    @Test
    void assignDataScopeCustomRejectsUnknownDept() {
        assertEquals(404, assertThrows(BusinessException.class,
                () -> roleService.assignDataScope(6L, DataScopeType.CUSTOM, Set.of(2L, 999L)))
                .status());
    }

    @Test
    void assignDataScopeCustomPersistsDeptRelations() {
        roleDept(6L, 2L);

        roleService.assignDataScope(6L, DataScopeType.CUSTOM, Set.of(2L, 3L));

        assertEquals("2", roleMapper.selectById(6L).getDataScope());
        assertEquals(Set.of(2L, 3L), deptIdsOfRole(6L));
    }

    @Test
    void assignDataScopeNonCustomClearsCustomRelations() {
        roleDept(6L, 2L);

        roleService.assignDataScope(6L, DataScopeType.DEPT_AND_CHILDREN, null);

        assertEquals("4", roleMapper.selectById(6L).getDataScope());
        assertTrue(deptIdsOfRole(6L).isEmpty());
    }

    @Test
    void assignDataScopePersistsAllScopeCodes() {
        roleService.assignDataScope(6L, DataScopeType.ALL, null);
        assertEquals("1", roleMapper.selectById(6L).getDataScope());
        roleService.assignDataScope(6L, DataScopeType.DEPT, null);
        assertEquals("3", roleMapper.selectById(6L).getDataScope());
        roleService.assignDataScope(6L, DataScopeType.SELF, null);
        assertEquals("5", roleMapper.selectById(6L).getDataScope());
    }

    // ---------- 真实数据库：关系替换事务提交与回滚 ----------

    @Test
    void pageQueryReturnsSeededRolesAndExcludesLogicallyDeleted() {
        RoleService bean = app.context().getBean(RoleService.class);
        SysRoleMapper mapper = app.context().getBean(SysRoleMapper.class);

        IPage<SysRole> page = bean.page(new PageQuery(), null, null, null);
        assertEquals(2, page.total());

        SysRole temp = new SysRole();
        temp.setRoleName("事务验证临时角色");
        temp.setRoleKey("tx_temp_role");
        bean.create(temp);
        try {
            bean.delete(temp.getRoleId());
            assertEquals(2, bean.page(new PageQuery(), null, null, null).total());
        } finally {
            mapper.deleteById(temp.getRoleId());
            try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
                st.execute("DELETE FROM sys_role WHERE role_id = " + temp.getRoleId());
            } catch (SQLException ignore) {
                // 物理清理失败不影响断言
            }
        }
    }

    @Test
    void assignMenusCommitsReplacementOnRealDatabase() throws Exception {
        RoleService bean = app.context().getBean(RoleService.class);

        bean.assignMenus(2L, Set.of(100L, 101L));

        assertEquals(Set.of(100L, 101L), roleMenuIdsFromDb(2L));
    }

    @Test
    void assignMenusRollsBackWhenAnyMenuMissing() throws Exception {
        RoleService bean = app.context().getBean(RoleService.class);
        bean.assignMenus(2L, Set.of(1L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bean.assignMenus(2L, Set.of(100L, 999999L)));

        assertEquals(404, ex.status());
        // 删除旧关系已发生但校验失败：事务回滚后不得出现半套关系
        assertEquals(Set.of(1L), roleMenuIdsFromDb(2L));
    }

    @Test
    void assignDataScopePersistsCustomDeptsOnRealDatabase() throws Exception {
        RoleService bean = app.context().getBean(RoleService.class);

        bean.assignDataScope(2L, DataScopeType.CUSTOM, Set.of(2L, 3L));

        assertEquals("2", roleDataScopeFromDb(2L));
        assertEquals(Set.of(2L, 3L), roleDeptIdsFromDb(2L));
    }

    @Test
    void assignDataScopeNonCustomClearsDeptsOnRealDatabase() throws Exception {
        RoleService bean = app.context().getBean(RoleService.class);
        bean.assignDataScope(2L, DataScopeType.CUSTOM, Set.of(2L, 3L));

        bean.assignDataScope(2L, DataScopeType.ALL, null);

        assertEquals("1", roleDataScopeFromDb(2L));
        assertTrue(roleDeptIdsFromDb(2L).isEmpty());
    }

    @Test
    void assignDataScopeRollsBackWhenAnyDeptMissing() throws Exception {
        RoleService bean = app.context().getBean(RoleService.class);
        bean.assignDataScope(2L, DataScopeType.ALL, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bean.assignDataScope(2L, DataScopeType.CUSTOM, Set.of(2L, 999999L)));

        assertEquals(404, ex.status());
        // data_scope 更新与旧关系删除都已发生：回滚后两者都必须保持原状
        assertEquals("1", roleDataScopeFromDb(2L));
        assertTrue(roleDeptIdsFromDb(2L).isEmpty());
    }

    @Test
    void menuIdsOfReadsBackAssignedMenusOnRealDatabase() {
        RoleService bean = app.context().getBean(RoleService.class);

        bean.assignMenus(2L, Set.of(100L, 101L));
        assertEquals(Set.of(100L, 101L), new HashSet<>(bean.menuIdsOf(2L)));

        // 清空授权后读回为空集合；未知角色抛 404
        bean.assignMenus(2L, Set.of());
        assertTrue(bean.menuIdsOf(2L).isEmpty());
        assertEquals(404, assertThrows(BusinessException.class,
                () -> bean.menuIdsOf(999L)).status());
    }

    @Test
    void deptIdsOfReadsBackCustomDeptsOnRealDatabase() {
        RoleService bean = app.context().getBean(RoleService.class);

        bean.assignDataScope(2L, DataScopeType.CUSTOM, Set.of(2L, 3L));
        assertEquals(Set.of(2L, 3L), new HashSet<>(bean.deptIdsOf(2L)));

        // 非 CUSTOM 范围清空自定义部门后读回为空集合；未知角色抛 404
        bean.assignDataScope(2L, DataScopeType.ALL, null);
        assertTrue(bean.deptIdsOf(2L).isEmpty());
        assertEquals(404, assertThrows(BusinessException.class,
                () -> bean.deptIdsOf(999L)).status());
    }

    // ---------- 辅助 ----------

    private Set<Long> menuIdsOfRole(Long roleId) {
        Set<Long> ids = new TreeSet<>();
        for (SysRoleMenu relation : roleMenuMapper.rowsOfRole(roleId)) {
            ids.add(relation.getMenuId());
        }
        return ids;
    }

    private Set<Long> deptIdsOfRole(Long roleId) {
        Set<Long> ids = new TreeSet<>();
        for (SysRoleDept relation : roleDeptMapper.rowsOfRole(roleId)) {
            ids.add(relation.getDeptId());
        }
        return ids;
    }

    private Set<Long> roleMenuIdsFromDb(Long roleId) throws SQLException {
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT menu_id FROM sys_role_menu WHERE role_id = " + roleId)) {
            Set<Long> ids = new HashSet<>();
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
            return ids;
        }
    }

    private Set<Long> roleDeptIdsFromDb(Long roleId) throws SQLException {
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT dept_id FROM sys_role_dept WHERE role_id = " + roleId)) {
            Set<Long> ids = new HashSet<>();
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
            return ids;
        }
    }

    private String roleDataScopeFromDb(Long roleId) throws SQLException {
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT data_scope FROM sys_role WHERE role_id = " + roleId)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private static Connection testConnection() throws SQLException {
        return DriverManager.getConnection(MySqlTestDatabase.testJdbcUrl(),
                requiredEnv("SUMMER_DATASOURCE_USERNAME"), requiredEnv("SUMMER_DATASOURCE_PASSWORD"));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    private void role(Long id, String name, String key) {
        SysRole role = new SysRole();
        role.setRoleId(id);
        role.setRoleName(name);
        role.setRoleKey(key);
        role.setStatus("0");
        role.setDelFlag("0");
        roleMapper.insert(role);
    }

    private void dept(Long id) {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setDelFlag("0");
        deptMapper.insert(dept);
    }

    private void menu(Long id) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setDelFlag("0");
        menuMapper.insert(menu);
    }

    private void userRole(Long userId, Long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        userRoleMapper.insert(relation);
    }

    private void roleMenu(Long roleId, Long menuId) {
        SysRoleMenu relation = new SysRoleMenu();
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        roleMenuMapper.insert(relation);
    }

    private void roleDept(Long roleId, Long deptId) {
        SysRoleDept relation = new SysRoleDept();
        relation.setRoleId(roleId);
        relation.setDeptId(deptId);
        roleDeptMapper.insert(relation);
    }

    /**
     * 内存版 RoleService：用关系表内存行替换 SqlExecutor 物理删除，
     * 使关系替换语义（先清后插）可以在无数据库环境下断言。
     */
    private static final class TestRoleService extends RoleService {
        private final RoleMenuFake roleMenus;
        private final RoleDeptFake roleDepts;

        TestRoleService(RoleFake roleMapper, RoleMenuFake roleMenuMapper, RoleDeptFake roleDeptMapper,
                        UserRoleFake userRoleMapper, MenuFake menuMapper, DeptFake deptMapper) {
            super(roleMapper, roleMenuMapper, roleDeptMapper, userRoleMapper, menuMapper, deptMapper, null);
            this.roleMenus = roleMenuMapper;
            this.roleDepts = roleDeptMapper;
        }

        @Override
        protected int removeRoleMenus(Long roleId) {
            return roleMenus.deleteByRoleId(roleId);
        }

        @Override
        protected int removeRoleDepts(Long roleId) {
            return roleDepts.deleteByRoleId(roleId);
        }
    }

    /** 带逻辑删除语义的 sys_role 内存 Mapper。 */
    private static final class RoleFake extends InMemoryBaseMapper<SysRole> implements SysRoleMapper {
        @Override
        protected Long idOf(SysRole entity) {
            return entity.getRoleId();
        }

        @Override
        public int deleteById(Object id) {
            SysRole role = selectById(id);
            if (role == null) {
                return 0;
            }
            role.setDelFlag("2");
            return 1;
        }

        @Override
        public SysRole selectById(Object id) {
            return rows.stream()
                    .filter(r -> java.util.Objects.equals(r.getRoleId(), id) && !"2".equals(r.getDelFlag()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<SysRole> selectList() {
            return rows.stream().filter(r -> !"2".equals(r.getDelFlag())).toList();
        }

        String delFlagOf(Long id) {
            return rows.stream()
                    .filter(r -> java.util.Objects.equals(r.getRoleId(), id))
                    .findFirst().map(SysRole::getDelFlag).orElse(null);
        }
    }

    /** sys_role_menu 关系内存 Mapper。 */
    private static final class RoleMenuFake extends InMemoryBaseMapper<SysRoleMenu> implements SysRoleMenuMapper {
        @Override
        protected Long idOf(SysRoleMenu entity) {
            return null;
        }

        List<SysRoleMenu> rowsOfRole(Long roleId) {
            List<SysRoleMenu> result = new ArrayList<>();
            for (SysRoleMenu row : rows) {
                if (java.util.Objects.equals(row.getRoleId(), roleId)) {
                    result.add(row);
                }
            }
            return result;
        }

        int deleteByRoleId(Long roleId) {
            return rows.removeIf(r -> java.util.Objects.equals(r.getRoleId(), roleId)) ? 1 : 0;
        }
    }

    /** sys_role_dept 关系内存 Mapper。 */
    private static final class RoleDeptFake extends InMemoryBaseMapper<SysRoleDept> implements SysRoleDeptMapper {
        @Override
        protected Long idOf(SysRoleDept entity) {
            return null;
        }

        List<SysRoleDept> rowsOfRole(Long roleId) {
            List<SysRoleDept> result = new ArrayList<>();
            for (SysRoleDept row : rows) {
                if (java.util.Objects.equals(row.getRoleId(), roleId)) {
                    result.add(row);
                }
            }
            return result;
        }

        int deleteByRoleId(Long roleId) {
            return rows.removeIf(r -> java.util.Objects.equals(r.getRoleId(), roleId)) ? 1 : 0;
        }
    }

    /** sys_user_role 关系内存 Mapper。 */
    private static final class UserRoleFake extends InMemoryBaseMapper<SysUserRole> implements SysUserRoleMapper {
        @Override
        protected Long idOf(SysUserRole entity) {
            return null;
        }
    }

    /** 带逻辑删除语义的 sys_menu 内存 Mapper。 */
    private static final class MenuFake extends InMemoryBaseMapper<SysMenu> implements SysMenuMapper {
        @Override
        protected Long idOf(SysMenu entity) {
            return entity.getMenuId();
        }

        @Override
        public SysMenu selectById(Object id) {
            return rows.stream()
                    .filter(m -> java.util.Objects.equals(m.getMenuId(), id) && !"2".equals(m.getDelFlag()))
                    .findFirst().orElse(null);
        }
    }

    /** 带逻辑删除语义的 sys_dept 内存 Mapper。 */
    private static final class DeptFake extends InMemoryBaseMapper<SysDept> implements SysDeptMapper {
        @Override
        protected Long idOf(SysDept entity) {
            return entity.getDeptId();
        }

        @Override
        public SysDept selectById(Object id) {
            return rows.stream()
                    .filter(d -> java.util.Objects.equals(d.getDeptId(), id) && !"2".equals(d.getDelFlag()))
                    .findFirst().orElse(null);
        }
    }
}
