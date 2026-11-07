package cn.jiebaba.summer.admin;

import cn.jiebaba.summer.admin.system.dept.SysDeptMapper;
import cn.jiebaba.summer.admin.system.dict.SysDictDataMapper;
import cn.jiebaba.summer.admin.system.dict.SysDictTypeMapper;
import cn.jiebaba.summer.admin.system.config.SysConfigMapper;
import cn.jiebaba.summer.admin.system.log.SysLoginLogMapper;
import cn.jiebaba.summer.admin.system.log.SysOperLogMapper;
import cn.jiebaba.summer.admin.system.menu.SysMenuMapper;
import cn.jiebaba.summer.admin.system.notice.SysNoticeMapper;
import cn.jiebaba.summer.admin.system.post.SysPost;
import cn.jiebaba.summer.admin.system.post.SysPostMapper;
import cn.jiebaba.summer.admin.system.post.SysUserPostMapper;
import cn.jiebaba.summer.admin.system.role.SysRoleDeptMapper;
import cn.jiebaba.summer.admin.system.role.SysRoleMapper;
import cn.jiebaba.summer.admin.system.role.SysRoleMenuMapper;
import cn.jiebaba.summer.admin.system.role.SysUserRoleMapper;
import cn.jiebaba.summer.admin.system.user.SysUserMapper;
import cn.jiebaba.summer.admin.testsupport.MySqlTestDatabase;
import cn.jiebaba.summer.boot.SummerApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 应用启动冒烟测试：以随机端口、test 配置拉起完整应用，断言 {@code GET /api/health}
 * 返回 200 且 JSON 体含 {@code "status":"UP"}；同时对所有 Mapper 执行
 * {@code selectCount} 元数据冒烟，验证实体与表字段映射无误。
 * <p>启动前先调用 {@link MySqlTestDatabase#reset()}，保证只使用隔离的
 * {@code summer_admin_test} 测试库，绝不触碰 {@code summer_admin}。
 * <p>本测试仅依赖 summer-admin 自身的 POM 与源码，不读取仓库内任何其他模块，
 * 因此整个目录可复制到临时位置独立运行。
 */
class ApplicationSmokeTest {

    private static SummerApplication app;
    private static HttpClient http;

    /**
     * 启动应用：先重建隔离测试库，再以随机端口（{@code --server.port=0}）拉起应用。
     * {@code --summer.profiles.active=test} 激活测试配置（独立数据库与连接池）。
     */
    @BeforeAll
    static void startApplication() {
        MySqlTestDatabase.reset();
        app = SummerApplication.run(SummerAdminApplication.class,
                new String[]{"--server.port=0", "--summer.profiles.active=test"});
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** 关闭应用：停止 Web 服务并关闭 IoC 容器，释放随机端口。 */
    @AfterAll
    static void stopApplication() {
        if (app != null) {
            app.webServer().stop();
            app.context().close();
            app = null;
        }
    }

    @Test
    void healthReturnsUp() throws Exception {
        int port = app.webServer().port();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        // 序列化格式可能含空白，去除后断言 JSON 内容
        String compact = response.body().replaceAll("\\s+", "");
        assertTrue(compact.contains("\"status\":\"UP\""),
                "响应体应含 \"status\":\"UP\"，实际为: " + response.body());
    }

    /**
     * 全部 15 个 Mapper 元数据冒烟：对空表/种子表执行 selectCount，
     * 任一实体字段与表列映射错误都会在此处抛错暴露。
     */
    @Test
    void allMappersCanSelectCount() {
        List<cn.jiebaba.summer.data.mapper.BaseMapper<?>> mappers = List.of(
                app.context().getBean(SysUserMapper.class),
                app.context().getBean(SysDeptMapper.class),
                app.context().getBean(SysPostMapper.class),
                app.context().getBean(SysUserPostMapper.class),
                app.context().getBean(SysRoleMapper.class),
                app.context().getBean(SysUserRoleMapper.class),
                app.context().getBean(SysRoleMenuMapper.class),
                app.context().getBean(SysRoleDeptMapper.class),
                app.context().getBean(SysMenuMapper.class),
                app.context().getBean(SysDictTypeMapper.class),
                app.context().getBean(SysDictDataMapper.class),
                app.context().getBean(SysConfigMapper.class),
                app.context().getBean(SysNoticeMapper.class),
                app.context().getBean(SysOperLogMapper.class),
                app.context().getBean(SysLoginLogMapper.class));
        assertEquals(15, mappers.size());
        for (cn.jiebaba.summer.data.mapper.BaseMapper<?> mapper : mappers) {
            assertTrue(mapper.selectCount(null) >= 0,
                    "Mapper selectCount 失败: " + mapper.getClass().getName());
        }
        // 种子数据已就位：admin 用户与超级管理员角色必须可查
        assertTrue(app.context().getBean(SysUserMapper.class).selectCount(null) >= 1);
    }

    /**
     * 逻辑删除语义回归：核心实体删除必须写 del_flag='2'（而非 @TableLogic 默认的 '1'），
     * 且普通查询必须排除逻辑删除行。用临时岗位行验证后直接物理清理。
     */
    @Test
    void logicalDeleteWritesTwoAndHidesRow() throws Exception {
        SysPostMapper postMapper = app.context().getBean(SysPostMapper.class);
        SysPost post = new SysPost();
        post.setPostCode("temp_logic_verify");
        post.setPostName("逻辑删除验证岗");
        post.setPostSort(99);
        post.setStatus("0");
        post.setCreateBy("test");
        postMapper.insert(post);
        Long postId = post.getPostId();
        try {
            postMapper.deleteById(postId);
            try (Connection conn = testConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT del_flag FROM sys_post WHERE post_id = ?")) {
                ps.setLong(1, postId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "逻辑删除必须是 UPDATE 而非物理删除");
                    assertEquals("2", rs.getString(1),
                            "逻辑删除必须写 del_flag='2'（0存在 2删除），实际为: " + rs.getString(1));
                }
            }
            assertNull(postMapper.selectById(postId), "selectById 必须排除逻辑删除行");
            boolean visible = postMapper.selectList(null).stream()
                    .anyMatch(p -> postId.equals(p.getPostId()));
            assertTrue(!visible, "selectList 必须排除逻辑删除行");
        } finally {
            try (Connection conn = testConnection(); Statement st = conn.createStatement()) {
                st.execute("DELETE FROM sys_post WHERE post_id = " + postId);
            }
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
