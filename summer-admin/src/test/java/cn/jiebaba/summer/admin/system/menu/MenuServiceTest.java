package cn.jiebaba.summer.admin.system.menu;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.system.role.SysRole;
import cn.jiebaba.summer.admin.system.role.SysRoleMapper;
import cn.jiebaba.summer.admin.system.role.SysRoleMenu;
import cn.jiebaba.summer.admin.system.role.SysRoleMenuMapper;
import cn.jiebaba.summer.admin.system.role.SysUserRole;
import cn.jiebaba.summer.admin.system.role.SysUserRoleMapper;
import cn.jiebaba.summer.admin.system.user.SysUser;
import cn.jiebaba.summer.admin.system.user.SysUserMapper;
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单服务纯内存单测：权限聚合（user_id=1 全量有效权限、普通用户启用角色并集、
 * 停用角色/菜单排除、按钮只进权限不进路由）、路由树构建（顺序稳定、按钮剔除）、
 * 菜单增删改校验（类型/父子关系/路由/权限标识/同级重名/成环）与删除引用约束。
 */
class MenuServiceTest {

    private MenuFake menuMapper;
    private RoleFake roleMapper;
    private RoleMenuFake roleMenuMapper;
    private UserRoleFake userRoleMapper;
    private UserFake userMapper;
    private MenuService menuService;

    @BeforeEach
    void setUp() {
        menuMapper = new MenuFake();
        roleMapper = new RoleFake();
        roleMenuMapper = new RoleMenuFake();
        userRoleMapper = new UserRoleFake();
        userMapper = new UserFake();
        menuService = new MenuService(menuMapper, roleMapper, roleMenuMapper, userRoleMapper, userMapper);
        seedMenus();
        role(1L, "admin", "0");
        role(2L, "common", "0");
        role(3L, "disabled", "1");
        role(6L, "auditor", "0");
    }

    @Test
    void adminGetsAllEnabledPermissions() {
        Set<String> perms = menuService.permissionsForUser(1L);

        assertEquals(Set.of("system:role:edit", "system:role:list",
                "system:user:add", "system:user:list"), perms);
    }

    @Test
    void adminRoutersExcludeButtonsAndDisabledMenus() {
        List<RouterVo> routers = menuService.routersForUser(1L);

        assertEquals(2, routers.size());
        RouterVo system = routers.get(0);
        assertEquals("System", system.getName());
        assertEquals("/system", system.getPath());
        assertEquals("Layout", system.getComponent());
        assertEquals("系统管理", system.getMeta().getTitle());
        assertEquals(2, system.getChildren().size());
        assertEquals("User", system.getChildren().get(0).getName());
        assertEquals("user", system.getChildren().get(0).getPath());
        assertEquals("system/user/index", system.getChildren().get(0).getComponent());
        assertEquals("Role", system.getChildren().get(1).getName());
        // 日志目录下唯一的子菜单已停用：目录保留但无子路由
        RouterVo log = routers.get(1);
        assertEquals("/log", log.getPath());
        assertTrue(log.getChildren().isEmpty());
        // 按钮与停用菜单绝不进入路由
        assertTrue(routers.stream().noneMatch(r -> "用户新增".equals(r.getMeta().getTitle())));
    }

    @Test
    void ordinaryUserGetsEnabledRoleUnionOnly() {
        user(2L);
        userRole(2L, 2L);
        userRole(2L, 3L);
        roleMenu(2L, 1L);
        roleMenu(2L, 100L);
        roleMenu(2L, 1001L);
        roleMenu(2L, 101L);
        roleMenu(2L, 1011L);
        roleMenu(3L, 100L);
        roleMenu(3L, 1011L);

        Set<String> perms = menuService.permissionsForUser(2L);

        // 角色 3 已停用，不贡献权限；按钮进入权限集合
        assertEquals(Set.of("system:role:edit", "system:role:list",
                "system:user:add", "system:user:list"), perms);
    }

    @Test
    void unionOfMultipleEnabledRolesMergesMenus() {
        user(4L);
        userRole(4L, 2L);
        userRole(4L, 6L);
        roleMenu(2L, 100L);
        roleMenu(6L, 2L);
        roleMenu(6L, 200L);

        Set<String> perms = menuService.permissionsForUser(4L);
        List<RouterVo> routers = menuService.routersForUser(4L);

        // 200 已停用：权限不含 operlog，但启用目录 2 进入路由
        assertEquals(Set.of("system:user:list"), perms);
        assertEquals(2, routers.size());
        RouterVo log = routers.stream()
                .filter(r -> "/log".equals(r.getPath())).findFirst().orElseThrow();
        assertTrue(log.getChildren().isEmpty());
    }

    @Test
    void disabledRoleContributesNothing() {
        user(3L);
        userRole(3L, 3L);
        roleMenu(3L, 100L);
        roleMenu(3L, 1001L);

        assertTrue(menuService.permissionsForUser(3L).isEmpty());
        assertTrue(menuService.routersForUser(3L).isEmpty());
    }

    @Test
    void deletedRoleContributesNothing() {
        // 已逻辑删除的角色（del_flag='2'）必须像停用角色一样被排除，拒绝默认
        deletedRole(7L, "ghost");
        user(5L);
        userRole(5L, 7L);
        roleMenu(7L, 100L);
        roleMenu(7L, 1001L);

        assertTrue(menuService.permissionsForUser(5L).isEmpty());
        assertTrue(menuService.routersForUser(5L).isEmpty());
    }

    @Test
    void deletedRoleDoesNotUnionWithLiveRole() {
        // 已删除角色与存活角色并存时，只合并存活角色的菜单
        deletedRole(7L, "ghost");
        user(6L);
        userRole(6L, 7L);
        userRole(6L, 2L);
        roleMenu(7L, 101L);
        roleMenu(2L, 100L);
        roleMenu(2L, 1001L);

        assertEquals(Set.of("system:user:add", "system:user:list"),
                menuService.permissionsForUser(6L));
    }

    @Test
    void unknownUserGetsNothing() {
        assertTrue(menuService.permissionsForUser(999L).isEmpty());
        assertTrue(menuService.routersForUser(999L).isEmpty());
        assertTrue(menuService.permissionsForUser(null).isEmpty());
    }

    @Test
    void buttonsArePermissionsButNeverRouters() {
        user(2L);
        userRole(2L, 2L);
        roleMenu(2L, 100L);
        roleMenu(2L, 1001L);

        assertEquals(Set.of("system:user:add", "system:user:list"),
                menuService.permissionsForUser(2L));
        List<RouterVo> routers = menuService.routersForUser(2L);
        // 上级目录 1 未授予：用户管理挂为根路由，按钮不出现
        assertEquals(1, routers.size());
        assertEquals("/user", routers.get(0).getPath());
        assertEquals("User", routers.get(0).getName());
        assertTrue(routers.get(0).getChildren().isEmpty());
    }

    @Test
    void createAppliesDefaults() {
        SysMenu menu = new SysMenu();
        menu.setMenuName("参数管理");
        menu.setMenuType("C");
        menu.setPath("config");

        Long id = menuService.create(menu);

        SysMenu saved = menuMapper.selectById(id);
        assertEquals("0", saved.getDelFlag());
        assertEquals("0", saved.getStatus());
        assertEquals("0", saved.getVisible());
        assertEquals(0, saved.getOrderNum());
    }

    @Test
    void createRequiresNameAndType() {
        SysMenu noName = new SysMenu();
        noName.setMenuType("C");
        noName.setPath("x");
        assertEquals(400, assertThrows(BusinessException.class,
                () -> menuService.create(noName)).status());

        SysMenu badType = new SysMenu();
        badType.setMenuName("坏类型");
        badType.setMenuType("X");
        badType.setPath("x");
        assertEquals(400, assertThrows(BusinessException.class,
                () -> menuService.create(badType)).status());
    }

    @Test
    void createMenuAndDirectoryRequirePath() {
        SysMenu menu = new SysMenu();
        menu.setMenuName("无路由");
        menu.setMenuType("C");
        assertEquals(400, assertThrows(BusinessException.class,
                () -> menuService.create(menu)).status());
    }

    @Test
    void createValidatesPermsFormat() {
        SysMenu menu = new SysMenu();
        menu.setMenuName("坏权限");
        menu.setMenuType("F");
        menu.setParentId(100L);
        menu.setPerms("bad perms!");
        assertEquals(400, assertThrows(BusinessException.class,
                () -> menuService.create(menu)).status());
    }

    @Test
    void createRejectsUnknownParent() {
        SysMenu menu = new SysMenu();
        menu.setMenuName("孤儿");
        menu.setMenuType("C");
        menu.setPath("orphan");
        menu.setParentId(999L);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> menuService.create(menu)).status());
    }

    @Test
    void createEnforcesParentTypeRules() {
        // 按钮只能挂在菜单下
        SysMenu buttonUnderDir = button("目录下的按钮", 1L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> menuService.create(buttonUnderDir)).status());
        // 按钮挂在菜单下：合法（内存 Mapper 不回填自增主键，用列表条数断言写入成功）
        int before = menuMapper.selectList().size();
        SysMenu button = button("菜单下的按钮", 100L);
        menuService.create(button);
        assertEquals(before + 1, menuMapper.selectList().size());
        // 菜单不能挂在菜单下
        SysMenu menuUnderMenu = new SysMenu();
        menuUnderMenu.setMenuName("菜单下的菜单");
        menuUnderMenu.setMenuType("C");
        menuUnderMenu.setPath("nested");
        menuUnderMenu.setParentId(100L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> menuService.create(menuUnderMenu)).status());
    }

    @Test
    void createRejectsDuplicateSiblingName() {
        SysMenu dup = new SysMenu();
        dup.setMenuName("用户管理");
        dup.setMenuType("C");
        dup.setPath("user2");
        dup.setParentId(1L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> menuService.create(dup)).status());
    }

    @Test
    void updateRejectsMovingUnderOwnChild() {
        SysMenu move = new SysMenu();
        move.setMenuId(1L);
        move.setParentId(100L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> menuService.update(move)).status());
    }

    @Test
    void updateRejectsMovingUnderItself() {
        SysMenu move = new SysMenu();
        move.setMenuId(100L);
        move.setParentId(100L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> menuService.update(move)).status());
    }

    @Test
    void updateNotFoundReturns404() {
        SysMenu move = new SysMenu();
        move.setMenuId(999L);
        move.setMenuName("不存在");
        assertEquals(404, assertThrows(BusinessException.class,
                () -> menuService.update(move)).status());
    }

    @Test
    void updateAppliesNonNullFields() {
        SysMenu change = new SysMenu();
        change.setMenuId(100L);
        change.setMenuName("用户中心");
        change.setOrderNum(5);
        menuService.update(change);

        SysMenu saved = menuMapper.selectById(100L);
        assertEquals("用户中心", saved.getMenuName());
        assertEquals(5, saved.getOrderNum());
        assertEquals("system/user/index", saved.getComponent());
    }

    @Test
    void deleteRejectsWhenChildrenExist() {
        assertEquals(409, assertThrows(BusinessException.class,
                () -> menuService.delete(1L)).status());
        assertEquals("0", menuMapper.selectById(1L).getDelFlag());
    }

    @Test
    void deleteRejectsWhenAssignedToRole() {
        roleMenu(2L, 200L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> menuService.delete(200L)).status());
    }

    @Test
    void deleteRemovesLeafLogically() {
        // 内存 Mapper 不回填自增主键，显式指定 ID 以便定位
        SysMenu leaf = new SysMenu();
        leaf.setMenuId(500L);
        leaf.setMenuName("叶子菜单");
        leaf.setMenuType("C");
        leaf.setPath("leaf");
        Long id = menuService.create(leaf);

        menuService.delete(id);

        assertNull(menuMapper.selectById(id));
        assertEquals(1, menuMapper.deletedCountOf(id));
    }

    @Test
    void listReturnsUndeletedMenusInStableOrder() {
        List<SysMenu> all = menuService.list();
        assertEquals(8, all.size());
        for (int i = 1; i < all.size(); i++) {
            int prev = all.get(i - 1).getOrderNum();
            int curr = all.get(i).getOrderNum();
            assertTrue(curr >= prev, "列表应按 orderNum 稳定排序");
        }
    }

    private void seedMenus() {
        menu(1L, 0L, "M", "系统管理", "system", 10, null, "0", "0");
        menu(100L, 1L, "C", "用户管理", "user", 1, "system:user:list", "0", "0");
        menuMapper.selectById(100L).setComponent("system/user/index");
        menu(1001L, 100L, "F", "用户新增", null, 1, "system:user:add", "0", "0");
        menu(1002L, 100L, "F", "用户删除", null, 2, "system:user:remove", "1", "0");
        menu(101L, 1L, "C", "角色管理", "role", 2, "system:role:list", "0", "0");
        menu(1011L, 101L, "F", "角色修改", null, 1, "system:role:edit", "0", "0");
        menu(2L, 0L, "M", "日志管理", "log", 20, null, "0", "0");
        menu(200L, 2L, "C", "操作日志", "operlog", 1, "system:operlog:list", "1", "0");
    }

    private SysMenu button(String name, Long parentId) {
        SysMenu button = new SysMenu();
        button.setMenuName(name);
        button.setMenuType("F");
        button.setParentId(parentId);
        button.setPerms("system:test:btn");
        return button;
    }

    private void menu(Long id, Long parentId, String type, String name, String path,
                      int orderNum, String perms, String status, String visible) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setParentId(parentId);
        menu.setMenuType(type);
        menu.setMenuName(name);
        menu.setPath(path);
        menu.setOrderNum(orderNum);
        menu.setPerms(perms);
        menu.setStatus(status);
        menu.setVisible(visible);
        menu.setDelFlag("0");
        menuMapper.insert(menu);
    }

    private void role(Long id, String key, String status) {
        SysRole role = new SysRole();
        role.setRoleId(id);
        role.setRoleName(key);
        role.setRoleKey(key);
        role.setStatus(status);
        role.setDelFlag("0");
        roleMapper.insert(role);
    }

    /** 直接写入已逻辑删除的角色（内存 Mapper 不回填自增主键，显式指定 ID）。 */
    private void deletedRole(Long id, String key) {
        SysRole role = new SysRole();
        role.setRoleId(id);
        role.setRoleName(key);
        role.setRoleKey(key);
        role.setStatus("0");
        role.setDelFlag("2");
        roleMapper.insert(role);
    }

    private void user(Long id) {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setDelFlag("0");
        userMapper.insert(user);
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

    /** 带逻辑删除语义的 sys_menu 内存 Mapper。 */
    private static final class MenuFake extends InMemoryBaseMapper<SysMenu> implements SysMenuMapper {
        @Override
        protected Long idOf(SysMenu entity) {
            return entity.getMenuId();
        }

        @Override
        public int deleteById(Object id) {
            SysMenu menu = selectById(id);
            if (menu == null) {
                return 0;
            }
            menu.setDelFlag("2");
            return 1;
        }

        @Override
        public SysMenu selectById(Object id) {
            return rows.stream()
                    .filter(m -> java.util.Objects.equals(m.getMenuId(), id) && !"2".equals(m.getDelFlag()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<SysMenu> selectList() {
            return rows.stream().filter(m -> !"2".equals(m.getDelFlag())).toList();
        }

        long deletedCountOf(Long id) {
            return rows.stream()
                    .filter(m -> java.util.Objects.equals(m.getMenuId(), id) && "2".equals(m.getDelFlag()))
                    .count();
        }
    }

    /** sys_role 内存 Mapper：selectList 返回全部行（含已删除，与真实 BaseMapper 一致），删除过滤由被测代码负责。 */
    private static final class RoleFake extends InMemoryBaseMapper<SysRole> implements SysRoleMapper {
        @Override
        protected Long idOf(SysRole entity) {
            return entity.getRoleId();
        }

        @Override
        public List<SysRole> selectList() {
            return List.copyOf(rows);
        }

        @Override
        public SysRole selectById(Object id) {
            return rows.stream()
                    .filter(r -> java.util.Objects.equals(r.getRoleId(), id) && !"2".equals(r.getDelFlag()))
                    .findFirst().orElse(null);
        }
    }

    /** sys_role_menu 关系内存 Mapper。 */
    private static final class RoleMenuFake extends InMemoryBaseMapper<SysRoleMenu> implements SysRoleMenuMapper {
        @Override
        protected Long idOf(SysRoleMenu entity) {
            return null;
        }

        List<SysRoleMenu> rowsOfRole(Long roleId) {
            return rows.stream()
                    .filter(r -> java.util.Objects.equals(r.getRoleId(), roleId))
                    .toList();
        }
    }

    /** sys_user_role 关系内存 Mapper。 */
    private static final class UserRoleFake extends InMemoryBaseMapper<SysUserRole> implements SysUserRoleMapper {
        @Override
        protected Long idOf(SysUserRole entity) {
            return null;
        }
    }

    /** 带逻辑删除语义的 sys_user 内存 Mapper。 */
    private static final class UserFake extends InMemoryBaseMapper<SysUser> implements SysUserMapper {
        @Override
        protected Long idOf(SysUser entity) {
            return entity.getUserId();
        }

        @Override
        public SysUser selectById(Object id) {
            return rows.stream()
                    .filter(u -> java.util.Objects.equals(u.getUserId(), id) && !"2".equals(u.getDelFlag()))
                    .findFirst().orElse(null);
        }
    }
}
