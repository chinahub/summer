package cn.jiebaba.summer.admin.e2e;

import cn.jiebaba.summer.admin.testsupport.MySqlTestDatabase;
import cn.jiebaba.summer.core.json.Json;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 核心管理功能端到端验收：对真实打包的 {@code summer-admin-1.0.0-boot.jar} 进程
 * 执行生产形态的核心场景（仅操作隔离测试库 {@code summer_admin_test}，独立端口 18090）。
 *
 * <p>场景（顺序执行，共享同一测试库）：
 * <ol>
 *   <li>生产静态页：{@code /} 返回 hash 前端页面，hash 资源可访问，未知 API 不回退页面；</li>
 *   <li>认证：admin/user 登录、userinfo/routers、无密码字段泄露；</li>
 *   <li>核心 CRUD：部门/岗位/参数/字典/公告 的增改查删；</li>
 *   <li>菜单/按钮/API 权限：普通用户无任何接口权限，探测角色按按钮权限拒绝；</li>
 *   <li>五种数据范围（全部/自定义/本部门/本部门及以下/仅本人）列表过滤与按 ID 越权 403；</li>
 *   <li>角色授权变更后旧令牌不生效、重新登录权限刷新；</li>
 *   <li>操作/登录日志记录且响应与日志无明文密码；</li>
 *   <li>生产默认开启验证码：签发验证码、错误验证码登录 400、登录日志留痕。</li>
 * </ol>
 *
 * <p>进程纪律：所有 {@code java -jar} 子进程在 finally 中无条件关闭
 * （destroy → 等待 → destroyForcibly）；凭据只经进程环境变量传递；
 * 多标签与主题等纯浏览器交互由主会话补充验收，本测试不覆盖。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoreAdminE2eIT {

    /** E2E 独立端口，避开开发 8080、ProductionJarIT 的 18089 与其他任务端口。 */
    private static final int PORT = 18090;
    private static final String BASE = "http://127.0.0.1:" + PORT;

    /** 主场景（验证码经环境变量关闭，仅测试用途）进程日志。 */
    private static final Path E2E_LOG = Path.of("target", "it-core-admin-e2e.log");
    /** 验证码专项（生产默认开启）进程日志。 */
    private static final Path CAPTCHA_LOG = Path.of("target", "it-core-admin-captcha.log");

    /** 测试专用 JWT 密钥：仅用于隔离测试库，不得用于任何真实环境。 */
    private static final String TEST_JWT_SECRET =
            "it-e2e-test-only-jwt-secret-0123456789abcdef";
    /** 探测账号统一测试口令：仅写入隔离测试库。 */
    private static final String PROBE_PASSWORD = "Probe123456";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private static Process app;
    private static Path appLog;

    private static String adminToken;
    private static String userToken;

    // 数据范围场景产出的主键，供后续断言与清理
    private static long deptId;
    private static long postId;
    private static long configId;
    private static long dictTypeId;
    private static long dictCode;
    private static long noticeId;
    private static final List<Long> roleIds = new ArrayList<>();
    private static final List<Long> probeUserIds = new ArrayList<>();
    private static long refreshRoleId;
    private static long refreshUserId;

    /**
     * 重建隔离测试库并拉起真实 JAR 进程（验证码经环境变量关闭，保证后续
     * 登录链路可自动化），轮询健康检查直至就绪。
     */
    @BeforeAll
    static void startApplication() throws Exception {
        MySqlTestDatabase.reset();
        startApp(false, E2E_LOG);
        awaitHealth();
    }

    /** 兜底关闭：无论任何测试状态，结束后不允许遗留进程。 */
    @AfterAll
    static void ensureStopped() throws Exception {
        stopApp();
    }

    @Test
    @Order(1)
    void productionStaticPagesServedBySameJar() throws Exception {
        HttpResponse<String> index = get("/", null);
        assertEquals(200, index.statusCode());
        assertTrue(index.body().contains("id=\"root\""), "/ 必须返回前端页面");
        String umi = extractFirst(index.body(), "src=\"([^\"]*umi[^\"]*\\.js)\"");
        assertNotNull(umi, "index.html 应引用 umi 入口脚本: " + abbreviate(index.body()));
        assertTrue(umi.matches(".*umi\\.[0-9a-f]{8,}\\.js.*"),
                "生产入口脚本应带内容 hash: " + umi);
        String assetPath = umi.startsWith("/") ? umi : "/" + umi;
        assertEquals(200, get(assetPath, null).statusCode(), "hash 入口脚本应可访问");

        HttpResponse<String> unknownApi = get("/api/definitely-not-found", null);
        // 未认证请求先经 JWT 过滤器（401），认证后无可匹配路由（404）；两者都不得回退为前端页面
        assertTrue(unknownApi.statusCode() == 401 || unknownApi.statusCode() == 404,
                "未知 API 应返回 401/404: " + unknownApi.statusCode());
        assertFalse(unknownApi.body().contains("id=\"root\""),
                "未知 API 不得回退为前端页面: " + abbreviate(unknownApi.body()));

        HttpResponse<String> health = get("/api/health", null);
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"UP\""), "health 应返回 UP: " + health.body());
    }

    @Test
    @Order(2)
    void adminAndUserLoginAndProfile() throws Exception {
        HttpResponse<String> adminLogin = post("/api/auth/login",
                "{\"username\":\"admin\",\"password\":\"admin123\"}", null);
        assertEquals(200, adminLogin.statusCode(), "admin 登录失败: " + adminLogin.body());
        adminToken = tokenOf(adminLogin);
        assertNotNull(adminToken);

        HttpResponse<String> adminInfo = get("/api/auth/userinfo", adminToken);
        assertEquals(200, adminInfo.statusCode());
        Map<String, Object> user = dataMap(adminInfo);
        Map<?, ?> profile = (Map<?, ?>) user.get("user");
        assertEquals("admin", profile.get("userName"));
        assertTrue(((List<?>) user.get("roles")).contains("admin"));
        assertTrue(((List<?>) user.get("permissions")).contains("system:user:list"));
        assertNoPasswordLeak("admin userinfo", adminInfo.body());

        HttpResponse<String> routers = get("/api/auth/routers", adminToken);
        assertEquals(200, routers.statusCode());
        assertTrue(routers.body().contains("系统管理"), "routers 应含菜单树");

        HttpResponse<String> userLogin = post("/api/auth/login",
                "{\"username\":\"user\",\"password\":\"user123\"}", null);
        assertEquals(200, userLogin.statusCode(), "user 登录失败: " + userLogin.body());
        userToken = tokenOf(userLogin);

        HttpResponse<String> userInfo = get("/api/auth/userinfo", userToken);
        Map<String, Object> commonUser = dataMap(userInfo);
        assertTrue(((List<?>) commonUser.get("roles")).contains("common"));
        assertTrue(((List<?>) commonUser.get("permissions")).isEmpty(),
                "普通角色未授权菜单，权限应为空: " + userInfo.body());
        assertNoPasswordLeak("user userinfo", userInfo.body());

        HttpResponse<String> denied = get("/api/system/users/list", userToken);
        assertEquals(403, denied.statusCode(),
                "普通用户访问用户列表应 403: " + abbreviate(denied.body()));
    }

    @Test
    @Order(3)
    void coreCrudAsAdmin() throws Exception {
        // 部门：增 → 改 → 查（祖级由服务端计算）→ 删
        HttpResponse<String> dept = post("/api/system/depts",
                "{\"deptName\":\"E2E临时部门\",\"parentId\":1,\"orderNum\":99,\"status\":\"0\"}",
                adminToken);
        assertEquals(200, dept.statusCode(), "新增部门失败: " + dept.body());
        deptId = idOf(dept);
        assertEquals(200, put("/api/system/depts",
                "{\"deptId\":" + deptId + ",\"deptName\":\"E2E临时部门改\",\"parentId\":1,"
                        + "\"orderNum\":98,\"status\":\"0\"}", adminToken).statusCode());
        HttpResponse<String> deptGet = get("/api/system/depts/" + deptId, adminToken);
        assertEquals(200, deptGet.statusCode());
        assertTrue(deptGet.body().contains("E2E临时部门改"));
        assertTrue(deptGet.body().contains("0,1"), "祖级路径应含 0,1: " + deptGet.body());

        // 岗位：增 → 改 → 删
        HttpResponse<String> post = post("/api/system/posts",
                "{\"postName\":\"E2E岗位\",\"postCode\":\"e2e_post\",\"postSort\":99,\"status\":\"0\"}",
                adminToken);
        assertEquals(200, post.statusCode(), "新增岗位失败: " + post.body());
        postId = idOf(post);
        assertEquals(200, put("/api/system/posts",
                "{\"postId\":" + postId + ",\"postName\":\"E2E岗位改\",\"postCode\":\"e2e_post\","
                        + "\"postSort\":98,\"status\":\"0\"}", adminToken).statusCode());
        HttpResponse<String> postGet = get("/api/system/posts/" + postId, adminToken);
        assertEquals(200, postGet.statusCode());
        assertTrue(postGet.body().contains("E2E岗位改"));

        // 参数：增 → 改 → 查 → 删
        HttpResponse<String> config = post("/api/system/configs",
                "{\"configName\":\"E2E参数\",\"configKey\":\"sys.e2e.flag\","
                        + "\"configValue\":\"on\",\"configType\":\"N\",\"status\":\"0\"}",
                adminToken);
        assertEquals(200, config.statusCode(), "新增参数失败: " + config.body());
        configId = idOf(config);
        assertEquals(200, put("/api/system/configs",
                "{\"configId\":" + configId + ",\"configName\":\"E2E参数改\","
                        + "\"configKey\":\"sys.e2e.flag\",\"configValue\":\"off\","
                        + "\"configType\":\"N\",\"status\":\"0\"}", adminToken).statusCode());
        HttpResponse<String> configGet = get("/api/system/configs/" + configId, adminToken);
        assertEquals(200, configGet.statusCode());
        assertTrue(configGet.body().contains("\"configValue\":\"off\""));

        // 字典：类型增 → 数据增/改 → 数据删 → 类型删
        HttpResponse<String> dictType = post("/api/system/dict/types",
                "{\"dictName\":\"E2E字典\",\"dictType\":\"e2e_test_status\",\"status\":\"0\"}",
                adminToken);
        assertEquals(200, dictType.statusCode(), "新增字典类型失败: " + dictType.body());
        dictTypeId = idOf(dictType);
        HttpResponse<String> dictData = post("/api/system/dict/data",
                "{\"dictType\":\"e2e_test_status\",\"dictLabel\":\"启用\",\"dictValue\":\"0\","
                        + "\"dictSort\":1,\"status\":\"0\"}", adminToken);
        assertEquals(200, dictData.statusCode(), "新增字典数据失败: " + dictData.body());
        dictCode = idOf(dictData);
        assertEquals(200, put("/api/system/dict/data",
                "{\"dictCode\":" + dictCode + ",\"dictType\":\"e2e_test_status\","
                        + "\"dictLabel\":\"启用改\",\"dictValue\":\"0\",\"dictSort\":1,"
                        + "\"status\":\"0\"}", adminToken).statusCode());

        // 公告：增（发布态）→ 改 → published 列表可见
        HttpResponse<String> notice = post("/api/system/notices",
                "{\"noticeTitle\":\"E2E公告\",\"noticeType\":\"1\","
                        + "\"noticeContent\":\"端到端验收公告\",\"status\":\"0\"}", adminToken);
        assertEquals(200, notice.statusCode(), "新增公告失败: " + notice.body());
        noticeId = idOf(notice);
        assertEquals(200, put("/api/system/notices",
                "{\"noticeId\":" + noticeId + ",\"noticeTitle\":\"E2E公告改\",\"noticeType\":\"1\","
                        + "\"noticeContent\":\"端到端验收公告改\",\"status\":\"0\"}",
                adminToken).statusCode());
        HttpResponse<String> published = get("/api/system/notices/published/list", userToken);
        assertEquals(200, published.statusCode());
        assertTrue(published.body().contains("E2E公告改"),
                "已发布公告应对登录用户可见: " + published.body());

        // 删除闭环：按关系顺序删除（字典数据先于字典类型，类型有数据时拒绝删除），
        // 每个删除都断言 HTTP 200 且删除后查询返回 404（字典数据另断言列表为空）
        assertEquals(200, delete("/api/system/depts/" + deptId, adminToken).statusCode(),
                "删除 E2E 部门失败");
        assertEquals(404, get("/api/system/depts/" + deptId, adminToken).statusCode(),
                "部门删除后查询应 404");

        assertEquals(200, delete("/api/system/posts/" + postId, adminToken).statusCode(),
                "删除 E2E 岗位失败");
        assertEquals(404, get("/api/system/posts/" + postId, adminToken).statusCode(),
                "岗位删除后查询应 404");

        assertEquals(200, delete("/api/system/configs/" + configId, adminToken).statusCode(),
                "删除 E2E 参数失败");
        assertEquals(404, get("/api/system/configs/" + configId, adminToken).statusCode(),
                "参数删除后查询应 404");

        assertEquals(200, delete("/api/system/dict/data/" + dictCode, adminToken).statusCode(),
                "删除 E2E 字典数据失败");
        assertEquals(404, get("/api/system/dict/data/" + dictCode, adminToken).statusCode(),
                "字典数据删除后查询应 404");
        HttpResponse<String> dataList = get(
                "/api/system/dict/data/list?dictType=e2e_test_status", adminToken);
        assertEquals(200, dataList.statusCode());
        assertEquals(0L, Long.parseLong(String.valueOf(dataMap(dataList).get("total"))),
                "字典数据删除后按类型查询应为空: " + dataList.body());

        assertEquals(200, delete("/api/system/dict/types/" + dictTypeId, adminToken).statusCode(),
                "删除 E2E 字典类型失败");
        assertEquals(404, get("/api/system/dict/types/" + dictTypeId, adminToken).statusCode(),
                "字典类型删除后查询应 404");

        assertEquals(200, delete("/api/system/notices/" + noticeId, adminToken).statusCode(),
                "删除 E2E 公告失败");
        assertEquals(404, get("/api/system/notices/" + noticeId, adminToken).statusCode(),
                "公告删除后查询应 404");
    }

    @Test
    @Order(4)
    void menuButtonAndApiPermissions() throws Exception {
        // 普通用户：无任何按钮/API 权限
        assertEquals(403, post("/api/system/depts",
                "{\"deptName\":\"越权部门\",\"parentId\":1,\"orderNum\":1,\"status\":\"0\"}",
                userToken).statusCode(), "普通用户新增部门应 403");

        // 探测角色（ALL 数据范围 + 用户列表/查询按钮）：可查询但无新增按钮
        long roleId = createRole("E2E权限角色", "e2e_perm", "ALL", List.of());
        assignMenus(roleId, List.of(100L, 1001L));
        long probeId = createUser("e2e_perm_user", 3L, List.of(roleId));
        String probeToken = login("e2e_perm_user");
        assertEquals(200, get("/api/system/users/list", probeToken).statusCode());
        HttpResponse<String> noButton = post("/api/system/users",
                "{\"userName\":\"e2e_nobtn\",\"nickName\":\"无按钮\",\"password\":\""
                        + PROBE_PASSWORD + "\",\"deptId\":3,\"status\":\"0\"}", probeToken);
        assertEquals(403, noButton.statusCode(),
                "无 system:user:add 按钮权限应 403: " + abbreviate(noButton.body()));
        roleIds.add(roleId);
        probeUserIds.add(probeId);
    }

    @Test
    @Order(5)
    void allFiveDataScopesAndDirectIdDenial() throws Exception {
        // 五种数据范围角色：均只授予用户列表 + 用户查询按钮
        long all = createRole("E2E全部数据", "e2e_scope_all", "ALL", List.of());
        long custom = createRole("E2E自定义数据", "e2e_scope_custom", "CUSTOM", List.of(3L));
        long dept = createRole("E2E本部门数据", "e2e_scope_dept", "DEPT", List.of());
        long deptChild = createRole("E2E部门及以下", "e2e_scope_dept_child", "DEPT_AND_CHILDREN",
                List.of());
        long self = createRole("E2E仅本人数据", "e2e_scope_self", "SELF", List.of());
        for (long roleId : List.of(all, custom, dept, deptChild, self)) {
            assignMenus(roleId, List.of(100L, 1001L));
        }
        roleIds.addAll(List.of(all, custom, dept, deptChild, self));

        // 探测用户：e2e_all/u3、e2e_custom/u3、e2e_dept/u2、e2e_child/u2、e2e_self/u4(质量测试组)
        long uAll = createUser("e2e_all", 3L, List.of(all));
        long uCustom = createUser("e2e_custom", 3L, List.of(custom));
        long uDept = createUser("e2e_dept", 2L, List.of(dept));
        long uChild = createUser("e2e_child", 2L, List.of(deptChild));
        long uSelf = createUser("e2e_self", 4L, List.of(self));
        probeUserIds.addAll(List.of(uAll, uCustom, uDept, uChild, uSelf));

        // 全部：可见所有用户（admin/user + 已有探测账号）
        Set<String> allVisible = visibleUserNames(login("e2e_all"));
        assertTrue(allVisible.contains("admin") && allVisible.contains("user"),
                "全部数据范围应可见所有用户: " + allVisible);
        assertTrue(allVisible.contains("e2e_self"), "全部范围应含 e2e_self: " + allVisible);

        // 自定义（仅市场部门 dept3）：只见 dept3 用户（含此前创建的权限探测账号）
        Set<String> customVisible = visibleUserNames(login("e2e_custom"));
        assertEquals(Set.of("user", "e2e_all", "e2e_custom", "e2e_perm_user"), customVisible,
                "自定义范围应严格限制在授权部门: " + customVisible);

        // 本部门（研发 dept2）：不含子部门质量测试组(dept4)
        Set<String> deptVisible = visibleUserNames(login("e2e_dept"));
        assertEquals(Set.of("admin", "e2e_dept", "e2e_child"), deptVisible,
                "本部门范围不应含子部门用户: " + deptVisible);

        // 本部门及以下（研发 dept2 + 质量测试组 dept4）
        Set<String> childVisible = visibleUserNames(login("e2e_child"));
        assertEquals(Set.of("admin", "e2e_dept", "e2e_child", "e2e_self"), childVisible,
                "本部门及以下应含子部门用户: " + childVisible);

        // 仅本人：列表只有自己
        String selfToken = login("e2e_self");
        assertEquals(Set.of("e2e_self"), visibleUserNames(selfToken),
                "仅本人范围列表应只有自己");

        // 按 ID 越权：仅本人用户直接读取范围外用户详情必须 403，读自己 200
        HttpResponse<String> denied = get("/api/system/users/1", selfToken);
        assertEquals(403, denied.statusCode(),
                "按 ID 越权访问其他用户应 403: " + abbreviate(denied.body()));
        assertEquals(200, get("/api/system/users/" + uSelf, selfToken).statusCode());
    }

    @Test
    @Order(6)
    void roleChangeRequiresReloginToTakeEffect() throws Exception {
        // 新角色初始无菜单，用户登录后无任何权限
        refreshRoleId = createRole("E2E刷新角色", "e2e_refresh", "ALL", List.of());
        assignMenus(refreshRoleId, List.of());
        refreshUserId = createUser("e2e_refresh", 3L, List.of(refreshRoleId));
        roleIds.add(refreshRoleId);
        probeUserIds.add(refreshUserId);

        String oldToken = login("e2e_refresh");
        assertEquals(403, get("/api/system/users/list", oldToken).statusCode(),
                "未授权角色访问用户列表应 403");

        // admin 变更角色授权：旧令牌（无状态）仍 403，重新登录后生效
        assignMenus(refreshRoleId, List.of(100L, 1001L));
        assertEquals(403, get("/api/system/users/list", oldToken).statusCode(),
                "旧令牌不得因服务端授权变更而自动生效");
        String newToken = login("e2e_refresh");
        assertEquals(200, get("/api/system/users/list", newToken).statusCode(),
                "重新登录后新令牌应获得刷新后的权限");
    }

    @Test
    @Order(7)
    void operationAndLoginLogsWithoutSecrets() throws Exception {
        // 操作日志：覆盖本用例之前的全部写操作，且不得含明文密码/口令
        HttpResponse<String> operations = get(
                "/api/system/logs/operations/list?pageNum=1&pageSize=50", adminToken);
        assertEquals(200, operations.statusCode());
        assertTrue(operations.body().contains("E2E公告改")
                        || operations.body().contains("E2E公告"),
                "操作日志应记录公告维护轨迹: " + abbreviate(operations.body()));
        // 操作日志的 operParam 保留参数键名（值为脱敏掩码），只断言敏感值不泄露
        assertNoSecretValues("操作日志", operations.body());

        // 登录日志：admin/user 成功留痕
        HttpResponse<String> logins = get(
                "/api/system/logs/logins/list?pageNum=1&pageSize=50", adminToken);
        assertEquals(200, logins.statusCode());
        assertTrue(logins.body().contains("admin") && logins.body().contains("user"),
                "登录日志应含 admin/user 成功记录: " + abbreviate(logins.body()));
        assertNoPasswordLeak("登录日志", logins.body());

        // 用户列表响应同样不得泄露密码散列
        HttpResponse<String> users = get("/api/system/users/list?pageNum=1&pageSize=50",
                adminToken);
        assertEquals(200, users.statusCode());
        assertNoPasswordLeak("用户列表", users.body());
    }

    @Test
    @Order(8)
    void captchaEnabledByDefaultRejectsWrongCode() throws Exception {
        // 主场景结束：先停进程并完成其日志敏感串检查，再以生产默认（验证码开启）重启
        stopApp();
        assertLogFileHasNoSecrets(E2E_LOG);

        startApp(true, CAPTCHA_LOG);
        try {
            awaitHealth();
            HttpResponse<String> captcha = get("/api/auth/captcha", null);
            assertEquals(200, captcha.statusCode());
            Map<String, Object> data = dataMap(captcha);
            assertEquals(Boolean.TRUE, data.get("captchaEnabled"), "生产默认应开启验证码");
            String uuid = (String) data.get("uuid");
            assertNotNull(uuid, "验证码应返回 uuid: " + captcha.body());
            String img = (String) data.get("img");
            assertNotNull(img);
            assertTrue(img.startsWith("data:image/png;base64,"), "验证码应为 PNG base64");

            // 错误验证码：登录 400，响应不含密码
            HttpResponse<String> wrong = post("/api/auth/login",
                    "{\"username\":\"admin\",\"password\":\"admin123\",\"uuid\":\""
                            + uuid + "\",\"code\":\"ZZZZ\"}", null);
            assertEquals(400, wrong.statusCode(),
                    "错误验证码应返回 400: " + abbreviate(wrong.body()));
            assertTrue(wrong.body().contains("验证码"),
                    "错误信息应提示验证码: " + wrong.body());
            assertNoPasswordLeak("验证码错误响应", wrong.body());

            // 登录日志留痕且不含密码
            List<String> msgs = loginLogMessages("admin", "1");
            assertTrue(msgs.stream().anyMatch(m -> m.contains("验证码")),
                    "验证码失败应写登录日志: " + msgs);
        } finally {
            stopApp();
            assertLogFileHasNoSecrets(CAPTCHA_LOG);
        }
    }

    // ------------------------------------------------------------------
    // 以下为 HTTP / 进程 / 数据库辅助方法
    // ------------------------------------------------------------------

    /**
     * 拉起真实 JAR 进程：固定独立端口与测试库，JWT 密钥为测试专用值；
     * {@code captchaEnabled=false} 时经环境变量关闭验证码（仅测试自动化用途），
     * true 时完全不设置该变量，走 application.yml 生产默认（开启）。
     */
    private static void startApp(boolean captchaEnabled, Path log) throws IOException {
        Path jar = Path.of(System.getProperty("it.jar",
                Path.of(System.getProperty("user.dir"), "target",
                        "summer-admin-1.0.0-boot.jar").toString()));
        assertTrue(Files.isRegularFile(jar), "可执行 JAR 不存在: " + jar);
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString(),
                "-jar", jar.toAbsolutePath().toString());
        builder.environment().put("SERVER_PORT", String.valueOf(PORT));
        builder.environment().put("SUMMER_DATASOURCE_URL", MySqlTestDatabase.testJdbcUrl());
        builder.environment().put("SUMMER_SECURITY_JWT_SECRET", TEST_JWT_SECRET);
        requireEnv(builder, "SUMMER_DATASOURCE_USERNAME");
        requireEnv(builder, "SUMMER_DATASOURCE_PASSWORD");
        if (!captchaEnabled) {
            builder.environment().put("SUMMER_ADMIN_CAPTCHA_ENABLED", "false");
        }
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        app = builder.start();
        appLog = log;
    }

    /** 无条件关闭当前进程：destroy 后等待 10 秒，未退出则强制终止并断言已退出。 */
    private static void stopApp() throws Exception {
        Process p = app;
        app = null;
        if (p == null) {
            return;
        }
        p.destroy();
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            assertTrue(p.waitFor(10, TimeUnit.SECONDS), "进程强制终止后仍未退出");
        }
        assertFalse(p.isAlive(), "测试进程必须已结束");
    }

    /** 轮询健康检查直至就绪（最多 60 秒）；进程提前退出时附带日志尾部。 */
    private static void awaitHealth() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        IOException last = null;
        while (System.nanoTime() < deadline) {
            if (!app.isAlive()) {
                fail("测试进程提前退出（exit=" + app.exitValue() + "），日志尾部：" + tailLog());
            }
            try {
                HttpResponse<String> response = get("/api/health", null);
                if (response.statusCode() == 200 && response.body().contains("\"UP\"")) {
                    return;
                }
            } catch (IOException e) {
                last = e;
            }
            Thread.sleep(500);
        }
        fail("健康检查超时未就绪: " + (last == null ? "" : last.getMessage())
                + "，日志尾部：" + tailLog());
    }

    /** 断言进程日志文件不含任何敏感串（密码、测试口令、JWT 密钥、数据库口令）。 */
    private static void assertLogFileHasNoSecrets(Path log) throws IOException {
        if (!Files.isRegularFile(log)) {
            return;
        }
        String content = Files.readString(log, StandardCharsets.UTF_8);
        List<String> forbidden = new ArrayList<>(List.of(
                "admin123", "user123", PROBE_PASSWORD, TEST_JWT_SECRET));
        String dbPassword = System.getenv("SUMMER_DATASOURCE_PASSWORD");
        if (dbPassword != null && !dbPassword.isBlank()) {
            forbidden.add(dbPassword);
        }
        for (String secret : forbidden) {
            assertFalse(content.contains(secret), "进程日志泄露敏感串（索引 "
                    + content.indexOf(secret) + "），文件: " + log);
        }
    }

    private static void requireEnv(ProcessBuilder builder, String name) {
        String value = System.getenv(name);
        assertNotNull(value, "环境变量缺失: " + name);
        builder.environment().put(name, value);
    }

    /** 创建角色并设置数据范围（scopeType 为 DataScopeType 枚举名，CUSTOM 附带部门）。 */
    private static long createRole(String name, String key, String scopeType,
                                   List<Long> deptIds) throws Exception {
        HttpResponse<String> created = post("/api/system/roles",
                "{\"roleName\":\"" + name + "\",\"roleKey\":\"" + key
                        + "\",\"roleSort\":99,\"status\":\"0\"}", adminToken);
        assertEquals(200, created.statusCode(), "新增角色失败: " + created.body());
        long roleId = idOf(created);
        StringBuilder body = new StringBuilder("{\"scopeType\":\"" + scopeType + "\"");
        if (!deptIds.isEmpty()) {
            body.append(",\"deptIds\":[");
            for (int i = 0; i < deptIds.size(); i++) {
                body.append(i == 0 ? "" : ",").append(deptIds.get(i));
            }
            body.append("]");
        }
        body.append("}");
        HttpResponse<String> scope = put("/api/system/roles/" + roleId + "/data-scope",
                body.toString(), adminToken);
        assertEquals(200, scope.statusCode(), "设置数据范围失败: " + scope.body());
        return roleId;
    }

    /** 为角色分配菜单（含按钮），授权立即写入关系表。 */
    private static void assignMenus(long roleId, List<Long> menuIds) throws Exception {
        StringBuilder body = new StringBuilder("{\"menuIds\":[");
        for (int i = 0; i < menuIds.size(); i++) {
            body.append(i == 0 ? "" : ",").append(menuIds.get(i));
        }
        body.append("]}");
        HttpResponse<String> response = put("/api/system/roles/" + roleId + "/menus",
                body.toString(), adminToken);
        assertEquals(200, response.statusCode(), "分配菜单失败: " + response.body());
    }

    /** 创建探测用户：固定测试口令，仅存在于隔离测试库。 */
    private static long createUser(String userName, long deptId, List<Long> roleIds)
            throws Exception {
        StringBuilder body = new StringBuilder("{\"userName\":\"" + userName
                + "\",\"nickName\":\"E2E探测\",\"password\":\"" + PROBE_PASSWORD
                + "\",\"deptId\":" + deptId + ",\"email\":\"e2e@jiebaba.cn\","
                + "\"phonenumber\":\"13900000000\",\"sex\":\"2\",\"status\":\"0\","
                + "\"roleIds\":[");
        for (int i = 0; i < roleIds.size(); i++) {
            body.append(i == 0 ? "" : ",").append(roleIds.get(i));
        }
        body.append("]}");
        HttpResponse<String> created = post("/api/system/users", body.toString(), adminToken);
        assertEquals(200, created.statusCode(), "新增用户失败: " + created.body());
        return idOf(created);
    }

    /** 登录并返回令牌（验证码已关闭的场景）。 */
    private static String login(String username) throws Exception {
        HttpResponse<String> response = post("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PROBE_PASSWORD + "\"}",
                null);
        assertEquals(200, response.statusCode(), username + " 登录失败: " + response.body());
        return tokenOf(response);
    }

    /** 读取用户列表第一页全部用户名（pageSize=50 足够覆盖 E2E 产生的数据量）。 */
    private static Set<String> visibleUserNames(String token) throws Exception {
        HttpResponse<String> response = get("/api/system/users/list?pageNum=1&pageSize=50",
                token);
        assertEquals(200, response.statusCode(), "数据范围用户列表应可访问: " + response.body());
        Map<String, Object> data = dataMap(response);
        List<?> rows = (List<?>) data.get("rows");
        Set<String> names = new LinkedHashSet<>();
        for (Object row : rows) {
            names.add((String) ((Map<?, ?>) row).get("userName"));
        }
        return names;
    }

    /** 直连测试库读取登录日志消息（仅测试库，凭据只从环境变量读取）。 */
    private static List<String> loginLogMessages(String userName, String status)
            throws Exception {
        List<String> msgs = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(MySqlTestDatabase.testJdbcUrl(),
                System.getenv("SUMMER_DATASOURCE_USERNAME"),
                System.getenv("SUMMER_DATASOURCE_PASSWORD"));
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT msg FROM sys_login_log WHERE user_name = ? AND status = ?")) {
            ps.setString(1, userName);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    msgs.add(rs.getString(1));
                }
            }
        }
        return msgs;
    }

    /** 断言响应体不含密码明文与 BCrypt 散列。 */
    private static void assertNoPasswordLeak(String where, String body) {
        assertFalse(body.contains("password"), where + " 不得包含 password 字段: "
                + abbreviate(body));
        assertNoSecretValues(where, body);
    }

    /** 断言文本不含任何敏感值（明文口令、种子口令、密码散列、测试密钥）。 */
    private static void assertNoSecretValues(String where, String body) {
        assertFalse(body.contains("$2a$"), where + " 不得包含密码散列: " + abbreviate(body));
        assertFalse(body.contains(PROBE_PASSWORD), where + " 不得包含明文口令");
        assertFalse(body.contains("admin123") || body.contains("user123"),
                where + " 不得包含种子口令");
        assertFalse(body.contains(TEST_JWT_SECRET), where + " 不得包含 JWT 密钥");
    }

    // ------------------------------------------------------------------
    // HTTP 与 JSON 基础封装
    // ------------------------------------------------------------------

    private static HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, null, token);
    }

    private static HttpResponse<String> post(String path, String body, String token)
            throws Exception {
        return send("POST", path, body, token);
    }

    private static HttpResponse<String> put(String path, String body, String token)
            throws Exception {
        return send("PUT", path, body, token);
    }

    /** 发送 DELETE 请求（无请求体）：自动附加 Bearer 令牌。 */
    private static HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(5))
                .method("DELETE", HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 发送 JSON 请求：自动附加 Bearer 令牌与 content-type，5 秒超时。 */
    private static HttpResponse<String> send(String method, String path, String body,
                                             String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(5));
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(
                    body == null ? "{}" : body));
            builder.header("Content-Type", "application/json");
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 解析响应 JSON 顶层对象。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(HttpResponse<String> response) {
        return (Map<String, Object>) Json.parse(response.body());
    }

    /** 取 R.data 对象。 */
    private static Map<String, Object> dataMap(HttpResponse<String> response) {
        return (Map<String, Object>) json(response).get("data");
    }

    /** 取 R.data 中的长整型主键（容忍数字或字符串形态）。 */
    private static long idOf(HttpResponse<String> response) {
        Object data = json(response).get("data");
        assertNotNull(data, "响应应含主键 data: " + response.body());
        return Long.parseLong(String.valueOf(data));
    }

    /** 取登录令牌（OAuth 形态字段 access_token）。 */
    private static String tokenOf(HttpResponse<String> response) {
        Object token = dataMap(response).get("access_token");
        assertNotNull(token, "登录响应应含 access_token: " + response.body());
        return (String) token;
    }

    /** 用正则提取 HTML 中第一个匹配组，未命中返回 null。 */
    private static String extractFirst(String content, String regex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex)
                .matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 截断长文本，避免断言消息刷屏。 */
    private static String abbreviate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    /** 读取当前进程日志尾部（最多 20 行）用于失败诊断。 */
    private static String tailLog() throws IOException {
        Path log = appLog;
        if (log == null || !Files.isRegularFile(log)) {
            return "<无日志文件>";
        }
        List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        return String.join("\n", lines.subList(Math.max(0, lines.size() - 20), lines.size()));
    }
}
