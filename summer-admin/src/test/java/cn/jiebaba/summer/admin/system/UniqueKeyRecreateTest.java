package cn.jiebaba.summer.admin.system;

import cn.jiebaba.summer.admin.SummerAdminApplication;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.system.config.ConfigService;
import cn.jiebaba.summer.admin.system.config.SysConfig;
import cn.jiebaba.summer.admin.system.dict.DictService;
import cn.jiebaba.summer.admin.system.dict.SysDictType;
import cn.jiebaba.summer.admin.system.post.PostService;
import cn.jiebaba.summer.admin.system.post.SysPost;
import cn.jiebaba.summer.admin.system.role.RoleService;
import cn.jiebaba.summer.admin.system.role.SysRole;
import cn.jiebaba.summer.admin.system.security.DataScopeResolver;
import cn.jiebaba.summer.admin.system.user.UserService;
import cn.jiebaba.summer.admin.system.user.dto.CreateUserRequest;
import cn.jiebaba.summer.admin.testsupport.MySqlTestDatabase;
import cn.jiebaba.summer.boot.SummerApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 逻辑删除唯一键真实回归：以完整应用（真实连接池 + 代理 Service + 真实 MySQL
 * 生成列唯一索引）对隔离测试库执行，覆盖最终审查裁定——自然键唯一索引由
 * "生成列（仅 del_flag='0' 时等于自然键，删除后为 NULL）+ 唯一索引"承载。
 *
 * <p>每个受影响表（sys_user.user_name、sys_post.post_code/post_name、
 * sys_role.role_key/role_name、sys_dict_type.dict_type、sys_config.config_key）
 * 执行两轮连续的 创建→删除→同名重建→删除 周期，断言：
 * <ul>
 *   <li>同名重建不再触发数据库唯一冲突（旧裸唯一键在此场景 500）；</li>
 *   <li>活跃重复键一律是 409 业务冲突（BusinessException），绝不 500；</li>
 *   <li>两轮结束后同一自然键存在两条已删除记录（多条已删除行可共存，
 *       复合键 (key,del_flag) 无法支持第二轮删除周期）。</li>
 * </ul>
 * 凭据只从环境变量读取；只操作 {@code summer_admin_test}。
 */
class UniqueKeyRecreateTest {

    private static SummerApplication app;
    private static UserService userService;
    private static PostService postService;
    private static RoleService roleService;
    private static DictService dictService;
    private static ConfigService configService;

    /** 启动应用：真实代理的 Service 与真实生成列唯一索引交互。 */
    @BeforeAll
    static void startApplication() {
        MySqlTestDatabase.reset();
        app = SummerApplication.run(SummerAdminApplication.class,
                new String[]{"--server.port=0", "--summer.profiles.active=test"});
        userService = app.context().getBean(UserService.class);
        postService = app.context().getBean(PostService.class);
        roleService = app.context().getBean(RoleService.class);
        dictService = app.context().getBean(DictService.class);
        configService = app.context().getBean(ConfigService.class);
    }

    @AfterAll
    static void stopApplication() {
        if (app != null) {
            app.webServer().stop();
            app.context().close();
            app = null;
        }
    }

    @Test
    void sysUserNameSurvivesTwoRecreateCycles() throws Exception {
        String userName = "recycle_user";
        for (int cycle = 1; cycle <= 2; cycle++) {
            Long id = createUser(userName);
            assertActiveUserNameRejected(userName);
            userService.delete(id, DataScopeResolver.SUPER_ADMIN_ID);
        }
        assertEquals(2, countDeleted("sys_user", "user_name = '" + userName + "'"),
                "两轮删除后同一登录名应共存两条已删除用户记录");
    }

    @Test
    void sysPostCodeAndNameSurviveTwoRecreateCycles() throws Exception {
        String postCode = "recycle_post";
        String postName = "循环岗位";
        for (int cycle = 1; cycle <= 2; cycle++) {
            Long id = createPost(postCode, postName);
            assertActivePostRejected(postCode, postName);
            postService.delete(id);
        }
        assertEquals(2, countDeleted("sys_post", "post_code = '" + postCode + "'"),
                "两轮删除后同一岗位编码应共存两条已删除岗位记录");
        assertEquals(2, countDeleted("sys_post", "post_name = '" + postName + "'"),
                "两轮删除后同一岗位名称应共存两条已删除岗位记录");
    }

    @Test
    void sysRoleKeyAndNameSurviveTwoRecreateCycles() throws Exception {
        String roleKey = "recycle_role";
        String roleName = "循环角色";
        for (int cycle = 1; cycle <= 2; cycle++) {
            Long id = createRole(roleKey, roleName);
            assertActiveRoleRejected(roleKey, roleName);
            roleService.delete(id);
        }
        assertEquals(2, countDeleted("sys_role", "role_key = '" + roleKey + "'"),
                "两轮删除后同一权限字符应共存两条已删除角色记录");
        assertEquals(2, countDeleted("sys_role", "role_name = '" + roleName + "'"),
                "两轮删除后同一角色名称应共存两条已删除角色记录");
    }

    @Test
    void sysDictTypeSurvivesTwoRecreateCycles() throws Exception {
        String dictType = "recycle_dict_type";
        for (int cycle = 1; cycle <= 2; cycle++) {
            Long id = createDictType(dictType);
            assertActiveDictTypeRejected(dictType);
            dictService.deleteType(id);
        }
        assertEquals(2, countDeleted("sys_dict_type", "dict_type = '" + dictType + "'"),
                "两轮删除后同一字典类型应共存两条已删除类型记录");
    }

    @Test
    void sysConfigKeySurvivesTwoRecreateCycles() throws Exception {
        String configKey = "recycle.config.key";
        for (int cycle = 1; cycle <= 2; cycle++) {
            Long id = createConfig(configKey);
            assertActiveConfigKeyRejected(configKey);
            configService.delete(id);
        }
        assertEquals(2, countDeleted("sys_config", "config_key = '" + configKey + "'"),
                "两轮删除后同一参数键名应共存两条已删除参数记录");
    }

    // ---- 各表创建辅助 -----------------------------------------------------

    /** 以超级管理员身份创建测试用户（归属种子部门 2，无角色/岗位关系）。 */
    private static Long createUser(String userName) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUserName(userName);
        request.setNickName("循环用户");
        request.setPassword("recycle123");
        request.setDeptId(2L);
        return userService.create(request, DataScopeResolver.SUPER_ADMIN_ID);
    }

    private static Long createPost(String postCode, String postName) {
        SysPost post = new SysPost();
        post.setPostCode(postCode);
        post.setPostName(postName);
        return postService.create(post);
    }

    private static Long createRole(String roleKey, String roleName) {
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        role.setRoleName(roleName);
        return roleService.create(role);
    }

    private static Long createDictType(String dictType) {
        SysDictType type = new SysDictType();
        type.setDictName("循环字典");
        type.setDictType(dictType);
        return dictService.createType(type);
    }

    private static Long createConfig(String configKey) {
        SysConfig config = new SysConfig();
        config.setConfigName("循环参数");
        config.setConfigKey(configKey);
        config.setConfigValue("recycle-value");
        return configService.create(config);
    }

    // ---- 活跃重复键断言：一律 409，绝不 500 --------------------------------

    private static void assertActiveUserNameRejected(String userName) {
        BusinessException ex = assertThrows(BusinessException.class, () -> createUser(userName),
                "活跃登录名重复必须抛业务冲突而不是数据库唯一冲突");
        assertEquals(409, ex.status(), "活跃登录名重复应为 409，绝不 500");
    }

    private static void assertActivePostRejected(String postCode, String postName) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> createPost(postCode, "另一个岗位名"),
                "活跃岗位编码重复必须抛业务冲突而不是数据库唯一冲突");
        assertEquals(409, ex.status(), "活跃岗位编码重复应为 409，绝不 500");
        ex = assertThrows(BusinessException.class, () -> createPost("another_code", postName),
                "活跃岗位名称重复必须抛业务冲突而不是数据库唯一冲突");
        assertEquals(409, ex.status(), "活跃岗位名称重复应为 409，绝不 500");
    }

    private static void assertActiveRoleRejected(String roleKey, String roleName) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> createRole(roleKey, "另一个角色名"),
                "活跃权限字符重复必须抛业务冲突而不是数据库唯一冲突");
        assertEquals(409, ex.status(), "活跃权限字符重复应为 409，绝不 500");
        ex = assertThrows(BusinessException.class, () -> createRole("another_key", roleName),
                "活跃角色名称重复必须抛业务冲突而不是数据库唯一冲突");
        assertEquals(409, ex.status(), "活跃角色名称重复应为 409，绝不 500");
    }

    private static void assertActiveDictTypeRejected(String dictType) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> createDictType(dictType),
                "活跃字典类型重复必须抛业务冲突而不是数据库唯一冲突");
        assertEquals(409, ex.status(), "活跃字典类型重复应为 409，绝不 500");
    }

    private static void assertActiveConfigKeyRejected(String configKey) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> createConfig(configKey),
                "活跃参数键名重复必须抛业务冲突而不是数据库唯一冲突");
        assertEquals(409, ex.status(), "活跃参数键名重复应为 409，绝不 500");
    }

    // ---- JDBC 断言辅助 ----------------------------------------------------

    /** 统计指定表满足条件的已逻辑删除行数（证明多条已删除记录共存）。 */
    private static long countDeleted(String table, String where) throws Exception {
        try (Connection conn = testConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM " + table + " WHERE del_flag = '2' AND " + where)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Connection testConnection() throws Exception {
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
}
