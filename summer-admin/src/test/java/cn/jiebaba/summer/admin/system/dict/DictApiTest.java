package cn.jiebaba.summer.admin.system.dict;

import cn.jiebaba.summer.admin.SummerAdminApplication;
import cn.jiebaba.summer.admin.testsupport.MySqlTestDatabase;
import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.core.json.Json;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字典管理真实 HTTP 测试：以随机端口、test 配置拉起完整应用，覆盖匿名 401、
 * 无 system:dict:* 权限 403、已认证用户可读启用字典（按类型，排序稳定）、
 * 类型/数据 CRUD、类型与值唯一冲突 409、有数据的类型拒绝删除 404/409 契约。
 */
class DictApiTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String USER = "user";
    private static final String USER_PASSWORD = "user123";

    private static SummerApplication app;
    private static HttpClient http;
    private static int port;

    /** 启动应用：先重建隔离测试库，再以随机端口、test 配置拉起。 */
    @BeforeAll
    static void startApplication() {
        MySqlTestDatabase.reset();
        app = SummerApplication.run(SummerAdminApplication.class,
                new String[]{"--server.port=0", "--summer.profiles.active=test"});
        port = app.webServer().port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** 关闭应用：停止 Web 服务并关闭 IoC 容器。 */
    @AfterAll
    static void stopApplication() {
        if (app != null) {
            app.webServer().stop();
            app.context().close();
            app = null;
        }
    }

    @Test
    void anonymousRequestsGet401() throws Exception {
        for (String path : new String[]{
                "/api/system/dict/types/list",
                "/api/system/dict/types/1",
                "/api/system/dict/data/list",
                "/api/system/dict/data/type/sys_normal_disable"}) {
            HttpResponse<String> response = get(path, null);
            assertEquals(401, response.statusCode(), path + " 匿名访问应 401");
            assertRShape(response, 401);
        }
    }

    @Test
    void userWithoutDictPermissionGets403OnMaintenance() throws Exception {
        String token = loginToken(USER, USER_PASSWORD);
        HttpResponse<String> list = get("/api/system/dict/types/list", token);
        assertEquals(403, list.statusCode());
        assertRShape(list, 403);

        HttpResponse<String> create = postJson("/api/system/dict/types",
                "{\"dictName\":\"无权限\",\"dictType\":\"test_no_perm\"}", token);
        assertEquals(403, create.statusCode());
        assertRShape(create, 403);

        HttpResponse<String> update = putJson("/api/system/dict/types",
                "{\"dictId\":1,\"dictName\":\"无权限\",\"dictType\":\"sys_normal_disable\"}", token);
        assertEquals(403, update.statusCode());
        assertRShape(update, 403);

        HttpResponse<String> delete = delete("/api/system/dict/types/1", token);
        assertEquals(403, delete.statusCode());
        assertRShape(delete, 403);
    }

    @Test
    void authenticatedUserCanReadEnabledDictDataWithoutPermission() throws Exception {
        // 普通用户无任何 system:dict:* 权限，但按类型读启用字典仅要求已认证
        String token = loginToken(USER, USER_PASSWORD);
        HttpResponse<String> response = get("/api/system/dict/data/type/sys_normal_disable", token);
        assertEquals(200, response.statusCode());
        assertEquals(200, code(json(response)));

        List<?> rows = (List<?>) json(response).get("data");
        assertNotNull(rows, "启用字典必须返回 data 列表: " + response.body());
        assertEquals(2, rows.size(), "sys_normal_disable 种子含 正常/停用 两项: " + response.body());
        Map<?, ?> first = (Map<?, ?>) rows.get(0);
        assertEquals("正常", first.get("dictLabel"));
        assertEquals("0", first.get("dictValue"));
        Map<?, ?> second = (Map<?, ?>) rows.get(1);
        assertEquals("停用", second.get("dictLabel"));
        // 返回 snake_case 字段，且不含删除标志等内部字段
        assertTrue(first.containsKey("dictCode"), "应含 dictCode: " + response.body());
        assertFalse(first.containsKey("delFlag"), "不得含 delFlag: " + response.body());
    }

    @Test
    void adminListsTypesAndDataWithFilters() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);

        HttpResponse<String> types = get("/api/system/dict/types/list?pageNum=1&pageSize=10", token);
        assertEquals(200, types.statusCode());
        Map<?, ?> table = (Map<?, ?>) json(types).get("data");
        assertNotNull(table, "类型列表必须返回 TableData: " + types.body());
        assertTrue(((Number) table.get("total")).longValue() >= 5, "种子至少 5 个类型: " + types.body());

        HttpResponse<String> filtered = get(
                "/api/system/dict/types/list?pageNum=1&pageSize=10&dictType=sys_normal", token);
        Map<?, ?> filteredTable = (Map<?, ?>) json(filtered).get("data");
        assertEquals(1, ((Number) filteredTable.get("total")).longValue());

        HttpResponse<String> data = get(
                "/api/system/dict/data/list?pageNum=1&pageSize=10&dictType=sys_normal_disable", token);
        assertEquals(200, data.statusCode());
        Map<?, ?> dataTable = (Map<?, ?>) json(data).get("data");
        assertEquals(2, ((Number) dataTable.get("total")).longValue());
    }

    @Test
    void adminCrudFlowWithUniquenessAndDeleteGuards() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        String dictType = "test_api_color";

        // 新增类型
        HttpResponse<String> created = postJson("/api/system/dict/types",
                "{\"dictName\":\"接口测试颜色\",\"dictType\":\"" + dictType + "\"}", token);
        assertEquals(200, created.statusCode());
        long typeId = ((Number) json(created).get("data")).longValue();

        // 类型唯一：重复 dictType 冲突 409
        HttpResponse<String> duplicate = postJson("/api/system/dict/types",
                "{\"dictName\":\"重复\",\"dictType\":\"" + dictType + "\"}", token);
        assertEquals(409, duplicate.statusCode());
        assertRShape(duplicate, 409);

        // 详情
        HttpResponse<String> found = get("/api/system/dict/types/" + typeId, token);
        assertEquals(200, found.statusCode());
        assertEquals("接口测试颜色", ((Map<?, ?>) json(found).get("data")).get("dictName"));

        // 修改类型
        HttpResponse<String> updated = putJson("/api/system/dict/types",
                "{\"dictId\":" + typeId + ",\"dictName\":\"接口测试颜色改\",\"dictType\":\"" + dictType + "\"}", token);
        assertEquals(200, updated.statusCode());

        // 校验失败：空请求体 400
        HttpResponse<String> invalid = postJson("/api/system/dict/types", "{}", token);
        assertEquals(400, invalid.statusCode());
        assertRShape(invalid, 400);

        // 新增数据 + 同类型同值唯一
        HttpResponse<String> dataCreated = postJson("/api/system/dict/data",
                "{\"dictType\":\"" + dictType + "\",\"dictLabel\":\"红\",\"dictValue\":\"1\",\"dictSort\":2}", token);
        assertEquals(200, dataCreated.statusCode());
        long dictCode = ((Number) json(dataCreated).get("data")).longValue();
        HttpResponse<String> dupValue = postJson("/api/system/dict/data",
                "{\"dictType\":\"" + dictType + "\",\"dictLabel\":\"深红\",\"dictValue\":\"1\",\"dictSort\":3}", token);
        assertEquals(409, dupValue.statusCode());
        assertRShape(dupValue, 409);
        // 同值不同类型合法（sys_user_sex 种子已占用键值 0/1/2，故用 9）
        HttpResponse<String> otherType = postJson("/api/system/dict/data",
                "{\"dictType\":\"sys_user_sex\",\"dictLabel\":\"红\",\"dictValue\":\"9\",\"dictSort\":9}", token);
        assertEquals(200, otherType.statusCode());

        // 有数据的类型拒绝删除
        HttpResponse<String> guarded = delete("/api/system/dict/types/" + typeId, token);
        assertEquals(409, guarded.statusCode());
        assertRShape(guarded, 409);

        // 数据详情、修改、删除；类型随之可删
        HttpResponse<String> dataFound = get("/api/system/dict/data/" + dictCode, token);
        assertEquals(200, dataFound.statusCode());
        HttpResponse<String> dataUpdated = putJson("/api/system/dict/data",
                "{\"dictCode\":" + dictCode + ",\"dictType\":\"" + dictType
                        + "\",\"dictLabel\":\"深红\",\"dictValue\":\"1\",\"dictSort\":1}", token);
        assertEquals(200, dataUpdated.statusCode());

        HttpResponse<String> dataDeleted = delete("/api/system/dict/data/" + dictCode, token);
        assertEquals(200, dataDeleted.statusCode());
        HttpResponse<String> dataMissing = get("/api/system/dict/data/" + dictCode, token);
        assertEquals(404, dataMissing.statusCode());
        assertRShape(dataMissing, 404);

        HttpResponse<String> typeDeleted = delete("/api/system/dict/types/" + typeId, token);
        assertEquals(200, typeDeleted.statusCode());
        HttpResponse<String> typeMissing = get("/api/system/dict/types/" + typeId, token);
        assertEquals(404, typeMissing.statusCode());
        assertRShape(typeMissing, 404);
    }

    @Test
    void enabledEndpointExcludesDisabledAndSorts() throws Exception {
        String token = loginToken(ADMIN, ADMIN_PASSWORD);
        String dictType = "test_api_order";
        postJson("/api/system/dict/types",
                "{\"dictName\":\"排序测试\",\"dictType\":\"" + dictType + "\"}", token);
        postJson("/api/system/dict/data",
                "{\"dictType\":\"" + dictType + "\",\"dictLabel\":\"第二\",\"dictValue\":\"2\",\"dictSort\":2}", token);
        postJson("/api/system/dict/data",
                "{\"dictType\":\"" + dictType + "\",\"dictLabel\":\"第一\",\"dictValue\":\"1\",\"dictSort\":1}", token);
        postJson("/api/system/dict/data",
                "{\"dictType\":\"" + dictType + "\",\"dictLabel\":\"停用项\",\"dictValue\":\"0\",\"dictSort\":0,\"status\":\"1\"}", token);

        HttpResponse<String> response = get("/api/system/dict/data/type/" + dictType, token);
        assertEquals(200, response.statusCode());
        List<?> rows = (List<?>) json(response).get("data");
        assertEquals(2, rows.size(), "停用项不得返回: " + response.body());
        assertEquals("第一", ((Map<?, ?>) rows.get(0)).get("dictLabel"));
        assertEquals("第二", ((Map<?, ?>) rows.get(1)).get("dictLabel"));

        HttpResponse<String> unknown = get("/api/system/dict/data/type/test_api_unknown", token);
        assertEquals(200, unknown.statusCode());
        assertTrue(((List<?>) json(unknown).get("data")).isEmpty());
    }

    // ---- 辅助方法 -------------------------------------------------------

    /** 登录并返回 access_token；失败时带响应体断言。 */
    private static String loginToken(String username, String password) throws Exception {
        HttpResponse<String> response = postJson("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", null);
        assertEquals(200, response.statusCode(), "登录失败: " + response.body());
        Map<?, ?> data = (Map<?, ?>) json(response).get("data");
        return (String) data.get("access_token");
    }

    /** 断言响应体为 R 形错误（{code,msg}，不含框架 timestamp/path 字段）。 */
    private static void assertRShape(HttpResponse<String> response, int expectedCode) {
        Map<String, Object> body = json(response);
        assertEquals(expectedCode, code(body));
        assertNotNull(body.get("msg"), "R 形错误必须含 msg: " + response.body());
        assertFalse(body.containsKey("timestamp"), "R 格式不得含框架 timestamp 字段: " + response.body());
        assertFalse(body.containsKey("path"), "R 格式不得含框架 path 字段: " + response.body());
    }

    private static HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postJson(String path, String json, String token) throws Exception {
        return sendJson(path, json, token, "POST");
    }

    private static HttpResponse<String> putJson(String path, String json, String token) throws Exception {
        return sendJson(path, json, token, "PUT");
    }

    private static HttpResponse<String> sendJson(String path, String json, String token, String method) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .DELETE();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(HttpResponse<String> response) {
        return (Map<String, Object>) Json.parse(response.body());
    }

    private static int code(Map<String, Object> body) {
        return ((Number) body.get("code")).intValue();
    }
}
