package cn.jiebaba.summer.admin.system.user;

import cn.jiebaba.summer.admin.SummerAdminApplication;
import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.system.dept.SysDept;
import cn.jiebaba.summer.admin.system.dept.SysDeptMapper;
import cn.jiebaba.summer.admin.system.post.SysPost;
import cn.jiebaba.summer.admin.system.post.SysPostMapper;
import cn.jiebaba.summer.admin.system.post.SysUserPost;
import cn.jiebaba.summer.admin.system.post.SysUserPostMapper;
import cn.jiebaba.summer.admin.system.role.SysRole;
import cn.jiebaba.summer.admin.system.role.SysRoleDept;
import cn.jiebaba.summer.admin.system.role.SysRoleDeptMapper;
import cn.jiebaba.summer.admin.system.role.SysRoleMapper;
import cn.jiebaba.summer.admin.system.role.SysUserRole;
import cn.jiebaba.summer.admin.system.role.SysUserRoleMapper;
import cn.jiebaba.summer.admin.system.security.DataScopeResolver;
import cn.jiebaba.summer.admin.system.user.dto.ChangePasswordRequest;
import cn.jiebaba.summer.admin.system.user.dto.CreateUserRequest;
import cn.jiebaba.summer.admin.system.user.dto.UpdateProfileRequest;
import cn.jiebaba.summer.admin.system.user.dto.UpdateUserRequest;
import cn.jiebaba.summer.admin.system.user.dto.UserVo;
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import cn.jiebaba.summer.admin.testsupport.MySqlTestDatabase;
import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.data.page.IPage;
import cn.jiebaba.summer.security.crypto.PasswordEncoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户服务测试：内存替身覆盖 CRUD 校验（登录名唯一、BCrypt 编码、关系替换、
 * user_id=1/自身保护、资料白名单、改密验旧）；真实 MySQL 测试库覆盖五种数据
 * 范围列表、按 ID 越权 403、关系替换事务提交/回滚、逻辑删除与关系清理、
 * VO 批量组装与敏感字段缺失，经应用上下文拿到被事务拦截器代理的 UserService
 * Bean 验证真实 @Transactional 行为。
 */
class UserServiceTest {

    private static final long SUPER_ADMIN_ID = 1L;

    private UserFake userMapper;
    private UserRoleFake userRoleMapper;
    private UserPostFake userPostMapper;
    private RoleFake roleMapper;
    private PostFake postMapper;
    private DeptFake deptMapper;
    private TestUserService userService;

    private static SummerApplication app;

    /**
     * 启动完整应用获取真实 Bean：只有容器中的 UserService 才被
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
        userMapper = new UserFake();
        userRoleMapper = new UserRoleFake();
        userPostMapper = new UserPostFake();
        roleMapper = new RoleFake();
        postMapper = new PostFake();
        deptMapper = new DeptFake();
        DataScopeResolver resolver = new DataScopeResolver(
                userMapper, userRoleMapper, roleMapper, roleDeptMapper(), deptMapper);
        userService = new TestUserService(userMapper, userRoleMapper, userPostMapper,
                roleMapper, postMapper, deptMapper, resolver);
        seedOrganization();
    }

    // ---------- 内存替身：CRUD 与校验 ----------

    @Test
    void createRequiresUserNameAndPassword() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUserName("lily");
        request.setDeptId(2L);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> userService.create(request, SUPER_ADMIN_ID)).status());

        CreateUserRequest noName = new CreateUserRequest();
        noName.setPassword("secret66");
        noName.setDeptId(2L);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> userService.create(noName, SUPER_ADMIN_ID)).status());
    }

    @Test
    void createRejectsDuplicateUserName() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUserName("admin");
        request.setNickName("重复");
        request.setPassword("secret66");
        request.setDeptId(2L);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> userService.create(request, SUPER_ADMIN_ID)).status());
    }

    @Test
    void createRejectsUnknownDeptRoleOrPost() {
        CreateUserRequest badDept = validCreate("lily");
        badDept.setDeptId(999L);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> userService.create(badDept, SUPER_ADMIN_ID)).status());

        CreateUserRequest badRole = validCreate("lily");
        badRole.setRoleIds(Set.of(999L));
        assertEquals(404, assertThrows(BusinessException.class,
                () -> userService.create(badRole, SUPER_ADMIN_ID)).status());

        CreateUserRequest badPost = validCreate("lily");
        badPost.setPostIds(Set.of(999L));
        assertEquals(404, assertThrows(BusinessException.class,
                () -> userService.create(badPost, SUPER_ADMIN_ID)).status());
    }

    @Test
    void createEncodesPasswordAndAppliesDefaults() {
        Long userId = userService.create(validCreate("lily"), SUPER_ADMIN_ID);

        SysUser saved = userMapper.selectById(userId);
        assertNotEquals("secret66", saved.getPassword(), "密码绝不允许明文落库");
        assertTrue(saved.getPassword().startsWith("ENC("));
        assertEquals("0", saved.getStatus());
        assertEquals("0", saved.getDelFlag());
        assertEquals("2", saved.getSex());
        assertEquals(Set.of(2L), roleIdsOfUser(userId));
        assertEquals(Set.of(4L), postIdsOfUser(userId));
    }

    @Test
    void createDeniedOutsideDataScope() {
        // 操作者 10 号为"本部门"范围（部门 2），不能建到部门 3
        CreateUserRequest request = validCreate("outsider");
        request.setDeptId(3L);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> userService.create(request, 10L)).status());
        assertTrue(userMapper.selectList().stream()
                .noneMatch(u -> "outsider".equals(u.getUserName())));
    }

    @Test
    void createVisibleInsideDataScope() {
        CreateUserRequest request = validCreate("insider");
        request.setDeptId(2L);
        Long userId = userService.create(request, 10L);
        assertEquals(2L, userMapper.selectById(userId).getDeptId());
    }

    @Test
    void updateReplacesRoleAndPostRelations() {
        Long userId = userService.create(validCreate("lily"), SUPER_ADMIN_ID);

        UpdateUserRequest update = new UpdateUserRequest();
        update.setUserId(userId);
        update.setNickName("莉莉");
        update.setRoleIds(Set.of(3L));
        update.setPostIds(Set.of(2L));
        userService.update(update, SUPER_ADMIN_ID);

        assertEquals("莉莉", userMapper.selectById(userId).getNickName());
        assertEquals(Set.of(3L), roleIdsOfUser(userId));
        assertEquals(Set.of(2L), postIdsOfUser(userId));
    }

    @Test
    void updateKeepsRelationsWhenIdsAbsent() {
        Long userId = userService.create(validCreate("lily"), SUPER_ADMIN_ID);

        UpdateUserRequest update = new UpdateUserRequest();
        update.setUserId(userId);
        update.setEmail("lily@jiebaba.cn");
        userService.update(update, SUPER_ADMIN_ID);

        assertEquals("lily@jiebaba.cn", userMapper.selectById(userId).getEmail());
        assertEquals(Set.of(2L), roleIdsOfUser(userId));
        assertEquals(Set.of(4L), postIdsOfUser(userId));
    }

    @Test
    void updateRejectsDuplicateUserNameExcludingSelf() {
        Long userId = userService.create(validCreate("lily"), SUPER_ADMIN_ID);

        UpdateUserRequest dup = new UpdateUserRequest();
        dup.setUserId(userId);
        dup.setUserName("admin");
        assertEquals(409, assertThrows(BusinessException.class,
                () -> userService.update(dup, SUPER_ADMIN_ID)).status());

        UpdateUserRequest keep = new UpdateUserRequest();
        keep.setUserId(userId);
        keep.setUserName("lily");
        userService.update(keep, SUPER_ADMIN_ID);
    }

    @Test
    void updateDeniedOutsideScope() {
        // 操作者 11 号为"仅本人"范围，不能改别人
        UpdateUserRequest update = new UpdateUserRequest();
        update.setUserId(2L);
        update.setNickName("篡改");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> userService.update(update, 11L)).status());
    }

    @Test
    void updateMovingUserOutsideOwnScopeDenied() {
        Long userId = userService.create(validCreate("mover"), SUPER_ADMIN_ID);

        UpdateUserRequest move = new UpdateUserRequest();
        move.setUserId(userId);
        move.setDeptId(3L);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> userService.update(move, 10L)).status());
        assertEquals(2L, userMapper.selectById(userId).getDeptId());
    }

    @Test
    void getDeniedOutsideScope() {
        assertEquals(403, assertThrows(BusinessException.class,
                () -> userService.get(2L, 11L)).status());
        assertEquals(2L, userService.get(2L, SUPER_ADMIN_ID).getUserId());
    }

    @Test
    void deleteRejectsSuperAdminAndSelf() {
        assertEquals(409, assertThrows(BusinessException.class,
                () -> userService.delete(SUPER_ADMIN_ID, SUPER_ADMIN_ID)).status());
        assertEquals(409, assertThrows(BusinessException.class,
                () -> userService.delete(11L, 11L)).status());
        assertEquals("0", userMapper.selectById(SUPER_ADMIN_ID).getDelFlag());
    }

    @Test
    void deleteLogicalAndClearsRelations() {
        Long userId = userService.create(validCreate("lily"), SUPER_ADMIN_ID);

        userService.delete(userId, SUPER_ADMIN_ID);

        assertNull(userMapper.selectById(userId), "逻辑删除后 selectById 不可见");
        assertEquals("2", userMapper.delFlagOf(userId));
        assertTrue(roleIdsOfUser(userId).isEmpty());
        assertTrue(postIdsOfUser(userId).isEmpty());
    }

    @Test
    void updateStatusRejectsSuperAdminSelfAndInvalid() {
        assertEquals(409, assertThrows(BusinessException.class,
                () -> userService.updateStatus(SUPER_ADMIN_ID, "1", SUPER_ADMIN_ID)).status());
        assertEquals(409, assertThrows(BusinessException.class,
                () -> userService.updateStatus(11L, "1", 11L)).status());
        assertEquals(400, assertThrows(BusinessException.class,
                () -> userService.updateStatus(2L, "9", SUPER_ADMIN_ID)).status());

        userService.updateStatus(2L, "1", SUPER_ADMIN_ID);
        assertEquals("1", userMapper.selectById(2L).getStatus());
    }

    @Test
    void updateStatusDeniedOutsideScope() {
        assertEquals(403, assertThrows(BusinessException.class,
                () -> userService.updateStatus(2L, "1", 11L)).status());
    }

    @Test
    void resetPasswordRejectsSuperAdminAndSelf() {
        assertEquals(409, assertThrows(BusinessException.class,
                () -> userService.resetPassword(SUPER_ADMIN_ID, "newpass66", SUPER_ADMIN_ID)).status());
        assertEquals(409, assertThrows(BusinessException.class,
                () -> userService.resetPassword(11L, "newpass66", 11L)).status());
    }

    @Test
    void resetPasswordEncodesForOtherUser() {
        userService.resetPassword(2L, "newpass66", SUPER_ADMIN_ID);
        assertTrue(userMapper.selectById(2L).getPassword().startsWith("ENC(newpass66)"));
    }

    @Test
    void changePasswordVerifiesOldPassword() {
        ChangePasswordRequest wrong = new ChangePasswordRequest();
        wrong.setOldPassword("bad-old");
        wrong.setNewPassword("newpass66");
        assertEquals(409, assertThrows(BusinessException.class,
                () -> userService.changePassword(2L, wrong)).status());

        ChangePasswordRequest correct = new ChangePasswordRequest();
        correct.setOldPassword("user123");
        correct.setNewPassword("newpass66");
        userService.changePassword(2L, correct);
        assertTrue(userMapper.selectById(2L).getPassword().startsWith("ENC(newpass66)"));
    }

    @Test
    void updateProfileAppliesWhitelistOnly() {
        Long userId = userService.create(validCreate("lily"), SUPER_ADMIN_ID);

        UpdateProfileRequest profile = new UpdateProfileRequest();
        profile.setNickName("莉莉");
        profile.setEmail("lily@jiebaba.cn");
        profile.setPhonenumber("13900000000");
        profile.setSex("1");
        userService.updateProfile(userId, profile);

        SysUser saved = userMapper.selectById(userId);
        assertEquals("莉莉", saved.getNickName());
        assertEquals("lily@jiebaba.cn", saved.getEmail());
        assertEquals("13900000000", saved.getPhonenumber());
        assertEquals("1", saved.getSex());
        assertEquals("0", saved.getStatus(), "资料接口绝不允许触碰状态");
        assertEquals(2L, saved.getDeptId(), "资料接口绝不允许触碰部门");
    }

    @Test
    void userVoNeverExposesPassword() {
        for (Field field : UserVo.class.getDeclaredFields()) {
            assertFalse(field.getName().toLowerCase().contains("password"),
                    "VO 不得包含密码字段: " + field.getName());
        }
        Long userId = userService.create(validCreate("lily"), SUPER_ADMIN_ID);
        UserVo vo = userService.get(userId, SUPER_ADMIN_ID);
        assertEquals("lily", vo.getUserName());
        assertEquals("研发部门", vo.getDeptName());
        assertTrue(vo.getRoleNames().contains("普通角色"));
        assertTrue(vo.getPostNames().contains("市场专员"));
    }

    // ---------- 真实数据库：数据范围、事务与保护 ----------

    @Test
    void pageAppliesAllFiveDataScopesOnRealDatabase() throws Exception {
        UserService bean = app.context().getBean(UserService.class);
        insertScopeFixtures();

        try {
            // ALL（角色 1）：看见全部未删除用户
            assertEquals(7, bean.page(new PageQuery(), 1L, null, null, null).total());
            // SELF（user/角色 2，data_scope=5）：仅自己
            IPage<UserVo> self = bean.page(new PageQuery(), 2L, null, null, null);
            assertEquals(1, self.total());
            assertEquals(2L, self.records().get(0).getUserId());
            // DEPT（用户 3 在部门 2）：部门 2 成员为 1、3、4
            assertEquals(3, bean.page(new PageQuery(), 3L, null, null, null).total());
            // DEPT_AND_CHILDREN（用户 4 在部门 2，含部门 4）：部门 2 的 1、3、4 与部门 4 的 5、7
            assertEquals(5, bean.page(new PageQuery(), 4L, null, null, null).total());
            // CUSTOM（用户 5 在部门 4，自定义可见部门 2）：部门 2 的 1、3、4
            assertEquals(3, bean.page(new PageQuery(), 5L, null, null, null).total());
        } finally {
            deleteScopeFixtures();
        }
    }

    @Test
    void pageDeptIdNarrowsToSubtreeAndNeverWidensDataScope() throws Exception {
        UserService bean = app.context().getBean(UserService.class);
        insertScopeFixtures();

        try {
            // ALL 范围 + 部门 2 子树（含部门 4）：1、3、4、5、7 共 5 人；部门 3：2、6 共 2 人
            assertEquals(5, bean.page(new PageQuery(), 1L, null, null, null, 2L).total());
            assertEquals(2, bean.page(new PageQuery(), 1L, null, null, null, 3L).total());
            // AND 收窄绝不放宽：DEPT 范围用户 3（仅部门 2 的 1、3、4）过滤部门 4 必须为空
            assertEquals(3, bean.page(new PageQuery(), 3L, null, null, null, 2L).total());
            assertEquals(0, bean.page(new PageQuery(), 3L, null, null, null, 4L).total());
            // SELF 范围用户 7（部门 4）：部门 4 子树仅见自己；
            // SELF 范围用户 2（部门 3，在部门 2 子树外）过滤部门 2 必须为空、部门 1 子树含自己
            assertEquals(1, bean.page(new PageQuery(), 7L, null, null, null, 4L).total());
            assertEquals(0, bean.page(new PageQuery(), 2L, null, null, null, 2L).total());
            assertEquals(1, bean.page(new PageQuery(), 2L, null, null, null, 1L).total());
            // 不存在的部门：空结果而非全量
            assertEquals(0, bean.page(new PageQuery(), 1L, null, null, null, 999L).total());
        } finally {
            deleteScopeFixtures();
        }
    }

    @Test
    void pageDeptIdMatchesAncestorsBySegmentNotSubstring() throws Exception {
        UserService bean = app.context().getBean(UserService.class);
        String hash = "$2a$10$ZpE7F1UWG89uVdA8kNtOEOZNE30PLK/DrkHHkyACLrVKy9PC1C1Fa";
        // 部门 11（根下，ancestors '0'）与其子部门 12（ancestors '0,11'）：
        // "0,11" 含子串 ",1"，子串匹配会误把 12 挂到部门 1 的子树
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO sys_dept (dept_id, parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time) VALUES "
                    + "(11, 0, '0', '段匹配甲部', 99, '0', '0', 'admin', NOW()),"
                    + "(12, 11, '0,11', '段匹配乙部', 99, '0', '0', 'admin', NOW())");
            st.execute("INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, password, status, del_flag, create_by, create_time) VALUES "
                    + "(81, 12, 'seg_substring', '段匹配用户', '" + hash + "', '0', '0', 'admin', NOW())");
            try {
                // ALL 范围过滤部门 1：合法后代（部门 2/3 的 admin、user）保留，
                // 部门 11 的后代（部门 12 用户）必须排除
                IPage<UserVo> byOne = bean.page(new PageQuery(), 1L, null, null, null, 1L);
                assertEquals(2, byOne.total());
                assertTrue(byOne.records().stream().anyMatch(u -> "admin".equals(u.getUserName())));
                assertFalse(byOne.records().stream()
                        .anyMatch(u -> "seg_substring".equals(u.getUserName())));

                // 过滤部门 11：其后代部门 12 正常命中
                IPage<UserVo> byEleven = bean.page(new PageQuery(), 1L, null, null, null, 11L);
                assertEquals(1, byEleven.total());
                assertEquals("seg_substring", byEleven.records().get(0).getUserName());
            } finally {
                st.execute("DELETE FROM sys_user WHERE user_id = 81");
                st.execute("DELETE FROM sys_dept WHERE dept_id IN (11, 12)");
            }
        }
    }

    @Test
    void pageAssemblesDeptRolePostNamesWithoutSecretsOnRealDatabase() {
        UserService bean = app.context().getBean(UserService.class);

        IPage<UserVo> page = bean.page(new PageQuery(), 1L, null, null, null);

        assertEquals(2, page.total());
        UserVo admin = page.records().stream()
                .filter(v -> "admin".equals(v.getUserName())).findFirst().orElseThrow();
        assertEquals("研发部门", admin.getDeptName());
        assertTrue(admin.getRoleNames().contains("超级管理员"));
        assertTrue(admin.getPostNames().contains("总经理"));
        assertEquals(List.of(1L), admin.getRoleIds());
        assertEquals(List.of(1L), admin.getPostIds());
    }

    @Test
    void getByIdDeniedOutsideScopeOnRealDatabase() {
        UserService bean = app.context().getBean(UserService.class);

        assertEquals(403, assertThrows(BusinessException.class,
                () -> bean.get(1L, 2L)).status());
        assertEquals("admin", bean.get(1L, 1L).getUserName());
    }

    @Test
    void createCommitsUserAndRelationsOnRealDatabase() throws Exception {
        UserService bean = app.context().getBean(UserService.class);
        CreateUserRequest request = new CreateUserRequest();
        request.setUserName("tx_user_ok");
        request.setNickName("事务用户");
        request.setPassword("secret66");
        request.setDeptId(2L);
        request.setRoleIds(Set.of(2L));
        request.setPostIds(Set.of(4L));

        try {
            Long userId = bean.create(request, 1L);

            try (Connection conn = testConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT password, status, del_flag FROM sys_user WHERE user_id = ?")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    String hash = rs.getString(1);
                    assertTrue(hash.startsWith("$2"), "密码必须为 BCrypt 哈希");
                    assertNotEquals("secret66", hash);
                    assertEquals("0", rs.getString(2));
                    assertEquals("0", rs.getString(3));
                }
            }
            assertEquals(Set.of(2L), userRoleIdsFromDb(userId));
            assertEquals(Set.of(4L), userPostIdsFromDb(userId));
        } finally {
            purgeUser("tx_user_ok");
        }
    }

    @Test
    void createRollsBackWhenRoleMissingOnRealDatabase() throws Exception {
        UserService bean = app.context().getBean(UserService.class);
        CreateUserRequest request = new CreateUserRequest();
        request.setUserName("tx_user_bad");
        request.setNickName("回滚用户");
        request.setPassword("secret66");
        request.setDeptId(2L);
        request.setRoleIds(Set.of(999999L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bean.create(request, 1L));

        assertEquals(404, ex.status());
        assertEquals(0, countUsersByName("tx_user_bad"), "新增失败必须整体回滚，不得残留用户");
    }

    @Test
    void updateRollsBackRelationReplacementOnRealDatabase() throws Exception {
        UserService bean = app.context().getBean(UserService.class);
        Long userId = bean.create(validCreate("tx_user_rel"), 1L);
        try {
            UpdateUserRequest ok = new UpdateUserRequest();
            ok.setUserId(userId);
            ok.setRoleIds(Set.of(2L));
            ok.setPostIds(Set.of(4L));
            bean.update(ok, 1L);
            assertEquals(Set.of(2L), userRoleIdsFromDb(userId));
            assertEquals(Set.of(4L), userPostIdsFromDb(userId));

            UpdateUserRequest bad = new UpdateUserRequest();
            bad.setUserId(userId);
            bad.setRoleIds(Set.of(999999L));
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bean.update(bad, 1L));

            assertEquals(404, ex.status());
            // 旧关系已删除但校验失败：事务回滚后不得出现半套关系
            assertEquals(Set.of(2L), userRoleIdsFromDb(userId));
            assertEquals(Set.of(4L), userPostIdsFromDb(userId));
        } finally {
            purgeUser("tx_user_rel");
        }
    }

    @Test
    void deleteLogicalAndClearsRelationsOnRealDatabase() throws Exception {
        UserService bean = app.context().getBean(UserService.class);
        Long userId = bean.create(validCreate("tx_user_del"), 1L);
        try {
            bean.delete(userId, 1L);

            assertEquals(404, assertThrows(BusinessException.class,
                    () -> bean.get(userId, 1L)).status());
            assertEquals(0, userRoleIdsFromDb(userId).size());
            assertEquals(0, userPostIdsFromDb(userId).size());
            try (Connection conn = testConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT del_flag FROM sys_user WHERE user_id = ?")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("2", rs.getString(1), "删除必须将 del_flag 置为 2");
                }
            }
        } finally {
            purgeUser("tx_user_del");
        }
    }

    @Test
    void statusAndResetGuardsOnRealDatabase() throws Exception {
        UserService bean = app.context().getBean(UserService.class);

        assertEquals(409, assertThrows(BusinessException.class,
                () -> bean.updateStatus(1L, "1", 1L)).status());
        assertEquals(409, assertThrows(BusinessException.class,
                () -> bean.updateStatus(2L, "1", 2L)).status());
        assertEquals(409, assertThrows(BusinessException.class,
                () -> bean.resetPassword(1L, "newpass66", 1L)).status());
        assertEquals(409, assertThrows(BusinessException.class,
                () -> bean.resetPassword(2L, "newpass66", 2L)).status());

        bean.resetPassword(2L, "newpass66", 1L);
        try (Connection conn = testConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT password FROM sys_user WHERE user_id = 2")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getString(1).startsWith("$2"), "重置密码必须 BCrypt 编码");
            }
        } finally {
            try (Connection conn = testConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE sys_user SET password = ? WHERE user_id = 2")) {
                ps.setString(1, "$2a$10$ZpE7F1UWG89uVdA8kNtOEOZNE30PLK/DrkHHkyACLrVKy9PC1C1Fa");
                ps.executeUpdate();
            }
        }
    }

    @Test
    void createDeniedOutsideScopeOnRealDatabase() throws Exception {
        UserService bean = app.context().getBean(UserService.class);
        CreateUserRequest request = validCreate("tx_user_scope");
        request.setDeptId(1L);

        try {
            // user(id=2) 为 SELF 范围，即使接口可调用也绝不能越权建到别的部门
            assertEquals(403, assertThrows(BusinessException.class,
                    () -> bean.create(request, 2L)).status());
            // 部门范围操作者不能建到范围外部门
            insertScopeFixtures();
            assertEquals(403, assertThrows(BusinessException.class,
                    () -> bean.create(request, 3L)).status());
        } finally {
            deleteScopeFixtures();
            purgeUserQuietly("tx_user_scope");
        }
    }

    // ---------- 辅助 ----------

    /**
     * 内存环境组织数据：部门树 1(总公司) → 2(研发)、3(市场)，2 → 4(质量测试)；
     * 角色 1=ALL、2=SELF（用户 admin/user）、3=DEPT、4=DEPT_AND_CHILDREN、
     * 5=CUSTOM；用户 10 绑角色 3（本部门-部门 2），用户 11 绑角色 2（仅本人）。
     */
    private void seedOrganization() {
        dept(1L, 0L, "0", "总公司");
        dept(2L, 1L, "0,1", "研发部门");
        dept(3L, 1L, "0,1", "市场部门");
        dept(4L, 2L, "0,1,2", "质量测试组");
        role(1L, "超级管理员", "admin", "1");
        role(2L, "普通角色", "common", "5");
        role(3L, "部门角色", "dept", "3");
        role(4L, "部门及以下角色", "deptChild", "4");
        role(5L, "自定义角色", "custom", "2");
        post(1L, "ceo", "总经理");
        post(2L, "dev", "研发工程师");
        post(3L, "tester", "测试工程师");
        post(4L, "market", "市场专员");
        user(SUPER_ADMIN_ID, 2L, "admin", "管理员", "1");
        user(2L, 3L, "user", "普通用户", "2");
        user(10L, 2L, "leader", "部门主管", "3");
        user(11L, 2L, "selfonly", "仅本人用户", "2");
    }

    private RoleDeptFake roleDeptMapper() {
        RoleDeptFake fake = new RoleDeptFake();
        // 自定义角色 5 可见部门 2
        SysRoleDept relation = new SysRoleDept();
        relation.setRoleId(5L);
        relation.setDeptId(2L);
        fake.insert(relation);
        return fake;
    }

    private CreateUserRequest validCreate(String userName) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUserName(userName);
        request.setNickName(userName);
        request.setPassword("secret66");
        request.setDeptId(2L);
        request.setRoleIds(Set.of(2L));
        request.setPostIds(Set.of(4L));
        return request;
    }

    private Set<Long> roleIdsOfUser(Long userId) {
        Set<Long> ids = new TreeSet<>();
        for (SysUserRole relation : userRoleMapper.rowsOfUser(userId)) {
            ids.add(relation.getRoleId());
        }
        return ids;
    }

    private Set<Long> postIdsOfUser(Long userId) {
        Set<Long> ids = new TreeSet<>();
        for (SysUserPost relation : userPostMapper.rowsOfUser(userId)) {
            ids.add(relation.getPostId());
        }
        return ids;
    }

    /** 五种数据范围夹具：用户 3(DEPT/部门2)、4(DEPT_AND_CHILDREN/部门2)、5(CUSTOM/部门4/可见部门2)、6(ALL/部门3)、7(SELF/部门4)。 */
    private void insertScopeFixtures() throws SQLException {
        String hash = "$2a$10$ZpE7F1UWG89uVdA8kNtOEOZNE30PLK/DrkHHkyACLrVKy9PC1C1Fa";
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, password, status, del_flag, create_by, create_time) VALUES "
                    + "(3, 2, 'scope_dept', '范围本部门', '" + hash + "', '0', '0', 'admin', NOW()),"
                    + "(4, 2, 'scope_child', '范围及以下', '" + hash + "', '0', '0', 'admin', NOW()),"
                    + "(5, 4, 'scope_custom', '范围自定义', '" + hash + "', '0', '0', 'admin', NOW()),"
                    + "(6, 3, 'scope_all', '范围全部', '" + hash + "', '0', '0', 'admin', NOW()),"
                    + "(7, 4, 'scope_self', '范围仅本人', '" + hash + "', '0', '0', 'admin', NOW())");
            st.execute("INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time) VALUES "
                    + "(3, '范围本部门角色', 'scope_dept', 3, '3', '0', '0', 'admin', NOW()),"
                    + "(4, '范围及以下角色', 'scope_child', 4, '4', '0', '0', 'admin', NOW()),"
                    + "(5, '范围自定义角色', 'scope_custom', 5, '2', '0', '0', 'admin', NOW()),"
                    + "(6, '范围全部角色', 'scope_all', 6, '1', '0', '0', 'admin', NOW()),"
                    + "(7, '范围仅本人角色', 'scope_self', 7, '5', '0', '0', 'admin', NOW())");
            st.execute("INSERT INTO sys_user_role (user_id, role_id) VALUES (3, 3), (4, 4), (5, 5), (6, 6), (7, 7)");
            st.execute("INSERT INTO sys_role_dept (role_id, dept_id) VALUES (5, 2)");
        }
    }

    private void deleteScopeFixtures() throws SQLException {
        try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM sys_user_role WHERE user_id BETWEEN 3 AND 7");
            st.execute("DELETE FROM sys_user_post WHERE user_id BETWEEN 3 AND 7");
            st.execute("DELETE FROM sys_role_dept WHERE role_id BETWEEN 3 AND 7");
            st.execute("DELETE FROM sys_role WHERE role_id BETWEEN 3 AND 7");
            st.execute("DELETE FROM sys_user WHERE user_id BETWEEN 3 AND 7");
        }
    }

    private void purgeUser(String userName) throws SQLException {
        try (Connection conn = testConnection();
             PreparedStatement find = conn.prepareStatement(
                     "SELECT user_id FROM sys_user WHERE user_name = ?");
             PreparedStatement rel = conn.prepareStatement("DELETE FROM sys_user_role WHERE user_id = ?");
             PreparedStatement post = conn.prepareStatement("DELETE FROM sys_user_post WHERE user_id = ?");
             PreparedStatement del = conn.prepareStatement("DELETE FROM sys_user WHERE user_id = ?")) {
            find.setString(1, userName);
            try (ResultSet rs = find.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                long userId = rs.getLong(1);
                rel.setLong(1, userId);
                rel.executeUpdate();
                post.setLong(1, userId);
                post.executeUpdate();
                del.setLong(1, userId);
                del.executeUpdate();
            }
        }
    }

    private void purgeUserQuietly(String userName) {
        try {
            purgeUser(userName);
        } catch (SQLException ignore) {
            // 物理清理失败不影响主断言
        }
    }

    private int countUsersByName(String userName) throws SQLException {
        try (Connection conn = testConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM sys_user WHERE user_name = ?")) {
            ps.setString(1, userName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private Set<Long> userRoleIdsFromDb(Long userId) throws SQLException {
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT role_id FROM sys_user_role WHERE user_id = " + userId)) {
            Set<Long> ids = new HashSet<>();
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
            return ids;
        }
    }

    private Set<Long> userPostIdsFromDb(Long userId) throws SQLException {
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT post_id FROM sys_user_post WHERE user_id = " + userId)) {
            Set<Long> ids = new HashSet<>();
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
            return ids;
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

    private void dept(Long id, Long parentId, String ancestors, String name) {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDeptName(name);
        dept.setStatus("0");
        dept.setDelFlag("0");
        deptMapper.insert(dept);
    }

    private void role(Long id, String name, String key, String dataScope) {
        SysRole role = new SysRole();
        role.setRoleId(id);
        role.setRoleName(name);
        role.setRoleKey(key);
        role.setDataScope(dataScope);
        role.setStatus("0");
        role.setDelFlag("0");
        roleMapper.insert(role);
    }

    private void post(Long id, String code, String name) {
        SysPost post = new SysPost();
        post.setPostId(id);
        post.setPostCode(code);
        post.setPostName(name);
        post.setStatus("0");
        post.setDelFlag("0");
        postMapper.insert(post);
    }

    private void user(Long id, Long deptId, String userName, String nickName, String roleId) {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setDeptId(deptId);
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setPassword("ENC(user123)");
        user.setStatus("0");
        user.setDelFlag("0");
        userMapper.insert(user);
        SysUserRole relation = new SysUserRole();
        relation.setUserId(id);
        relation.setRoleId(Long.valueOf(roleId));
        userRoleMapper.insert(relation);
    }

    /**
     * 内存版 UserService：用关系表内存行替换 SqlExecutor 物理删除，
     * 使关系替换语义（先清后插）与删除清关系可以在无数据库环境下断言。
     * 密码编码用可断言的假实现（ENC 前缀），真实 BCrypt 由真实库用例覆盖。
     */
    private static final class TestUserService extends UserService {
        private final UserRoleFake userRoles;
        private final UserPostFake userPosts;

        TestUserService(UserFake userMapper, UserRoleFake userRoleMapper, UserPostFake userPostMapper,
                        RoleFake roleMapper, PostFake postMapper, DeptFake deptMapper,
                        DataScopeResolver resolver) {
            super(userMapper, userRoleMapper, userPostMapper, roleMapper, postMapper,
                    deptMapper, resolver, null, new PasswordEncoder() {
                @Override
                public String encode(CharSequence rawPassword) {
                    return "ENC(" + rawPassword + ")";
                }

                @Override
                public boolean matches(CharSequence rawPassword, String encodedPassword) {
                    return ("ENC(" + rawPassword + ")").equals(encodedPassword);
                }
            });
            this.userRoles = userRoleMapper;
            this.userPosts = userPostMapper;
        }

        @Override
        protected int removeUserRoles(Long userId) {
            return userRoles.deleteByUserId(userId);
        }

        @Override
        protected int removeUserPosts(Long userId) {
            return userPosts.deleteByUserId(userId);
        }
    }

    /** 带逻辑删除语义的 sys_user 内存 Mapper（insert 自动分配递增 ID，镜像 AUTO 主键）。 */
    private static final class UserFake extends InMemoryBaseMapper<SysUser> implements SysUserMapper {
        @Override
        protected Long idOf(SysUser entity) {
            return entity.getUserId();
        }

        @Override
        public int insert(SysUser entity) {
            if (entity.getUserId() == null) {
                long next = rows.stream().map(u -> u.getUserId() == null ? 0L : u.getUserId())
                        .max(Long::compareTo).orElse(0L) + 1;
                entity.setUserId(next);
            }
            return super.insert(entity);
        }

        @Override
        public int updateById(SysUser entity) {
            for (int i = 0; i < rows.size(); i++) {
                if (Objects.equals(rows.get(i).getUserId(), entity.getUserId())) {
                    rows.set(i, entity);
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int deleteById(Object id) {
            SysUser user = selectById(id);
            if (user == null) {
                return 0;
            }
            user.setDelFlag("2");
            return 1;
        }

        @Override
        public SysUser selectById(Object id) {
            return rows.stream()
                    .filter(u -> Objects.equals(u.getUserId(), id) && !"2".equals(u.getDelFlag()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<SysUser> selectList() {
            return rows.stream().filter(u -> !"2".equals(u.getDelFlag())).toList();
        }

        String delFlagOf(Long id) {
            return rows.stream()
                    .filter(u -> Objects.equals(u.getUserId(), id))
                    .findFirst().map(SysUser::getDelFlag).orElse(null);
        }
    }

    /** sys_user_role 关系内存 Mapper。 */
    private static final class UserRoleFake extends InMemoryBaseMapper<SysUserRole> implements SysUserRoleMapper {
        @Override
        protected Long idOf(SysUserRole entity) {
            return null;
        }

        List<SysUserRole> rowsOfUser(Long userId) {
            List<SysUserRole> result = new ArrayList<>();
            for (SysUserRole row : rows) {
                if (Objects.equals(row.getUserId(), userId)) {
                    result.add(row);
                }
            }
            return result;
        }

        int deleteByUserId(Long userId) {
            return rows.removeIf(r -> Objects.equals(r.getUserId(), userId)) ? 1 : 0;
        }
    }

    /** sys_user_post 关系内存 Mapper。 */
    private static final class UserPostFake extends InMemoryBaseMapper<SysUserPost> implements SysUserPostMapper {
        @Override
        protected Long idOf(SysUserPost entity) {
            return null;
        }

        List<SysUserPost> rowsOfUser(Long userId) {
            List<SysUserPost> result = new ArrayList<>();
            for (SysUserPost row : rows) {
                if (Objects.equals(row.getUserId(), userId)) {
                    result.add(row);
                }
            }
            return result;
        }

        int deleteByUserId(Long userId) {
            return rows.removeIf(r -> Objects.equals(r.getUserId(), userId)) ? 1 : 0;
        }
    }

    /** 带逻辑删除语义的 sys_role 内存 Mapper。 */
    private static final class RoleFake extends InMemoryBaseMapper<SysRole> implements SysRoleMapper {
        @Override
        protected Long idOf(SysRole entity) {
            return entity.getRoleId();
        }

        @Override
        public SysRole selectById(Object id) {
            return rows.stream()
                    .filter(r -> Objects.equals(r.getRoleId(), id) && !"2".equals(r.getDelFlag()))
                    .findFirst().orElse(null);
        }
    }

    /** 带逻辑删除语义的 sys_post 内存 Mapper。 */
    private static final class PostFake extends InMemoryBaseMapper<SysPost> implements SysPostMapper {
        @Override
        protected Long idOf(SysPost entity) {
            return entity.getPostId();
        }

        @Override
        public SysPost selectById(Object id) {
            return rows.stream()
                    .filter(p -> Objects.equals(p.getPostId(), id) && !"2".equals(p.getDelFlag()))
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
                    .filter(d -> Objects.equals(d.getDeptId(), id) && !"2".equals(d.getDelFlag()))
                    .findFirst().orElse(null);
        }
    }

    /** sys_role_dept 关系内存 Mapper（仅用于内存环境构造自定义范围）。 */
    private static final class RoleDeptFake extends InMemoryBaseMapper<SysRoleDept> implements SysRoleDeptMapper {
        @Override
        protected Long idOf(SysRoleDept entity) {
            return null;
        }
    }
}
