package cn.jiebaba.summer.admin.common.log;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.system.log.SysOperLog;
import cn.jiebaba.summer.admin.system.log.SysOperLogMapper;
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.SecurityContext;
import cn.jiebaba.summer.security.core.SecurityContextHolder;
import cn.jiebaba.summer.security.core.UsernamePasswordAuthenticationToken;
import cn.jiebaba.summer.web.http.RawHttpRequest;
import cn.jiebaba.summer.web.http.WebRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作日志切面测试：经真实 IoC 容器创建被代理的 {@link OperLogFixtureService}
 * （自研字节码子类代理路径），覆盖成功记录、业务异常、耗时、操作人、请求 URL/IP、
 * 大小写无关的递归脱敏、日志 Mapper 抛错时的结果/异常不变与无递归记录，
 * 以及未注解方法完全不被拦截。
 */
class OperationLogAspectTest {

    private DefaultApplicationContext context;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        AdminRequestContext.clear();
        if (context != null) {
            context.close();
            context = null;
        }
    }

    @Test
    void successCallRecordsFullOperLogThroughProxy() throws Exception {
        OperFake mapper = new OperFake();
        OperLogFixtureService fixture = startContext(mapper);
        loginAs("admin");
        AdminRequestContext.set(webRequest("POST", "/api/system/users", "127.0.0.1"));

        String result = fixture.createUser("zhangsan", "secret123");

        assertEquals("created:zhangsan", result, "业务返回值不得被切面改变");
        assertEquals(1, mapper.rows().size(), "应恰好记录一条操作日志");
        SysOperLog log = mapper.rows().get(0);
        assertEquals("用户管理", log.getTitle());
        assertEquals(1, log.getBusinessType(), "动作“新增”应映射 businessType=1");
        assertTrue(log.getMethod().contains("createUser"), "method 应记录被调方法签名");
        assertEquals("POST", log.getRequestMethod());
        assertEquals("/api/system/users", log.getOperUrl());
        assertEquals("127.0.0.1", log.getOperIp());
        assertEquals("admin", log.getOperName(), "操作人应取当前 SecurityContext");
        assertEquals(0, log.getStatus());
        assertNotNull(log.getOperTime());
        assertNotNull(log.getCostTime(), "应记录耗时（毫秒）");
        assertTrue(log.getJsonResult().contains("created:zhangsan"), "应记录返回摘要");
    }

    @Test
    void businessExceptionIsRecordedAndRethrownUnchanged() {
        OperFake mapper = new OperFake();
        OperLogFixtureService fixture = startContext(mapper);
        loginAs("admin");

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> fixture.deleteUser("lisi"));
        assertEquals(409, thrown.status(), "原业务异常状态码不得被切面改变");
        assertEquals("禁止删除演示用户", thrown.getMessage());

        assertEquals(1, mapper.rows().size(), "业务异常路径也必须记录操作日志");
        SysOperLog log = mapper.rows().get(0);
        assertEquals(3, log.getBusinessType(), "动作“删除”应映射 businessType=3");
        assertEquals(1, log.getStatus(), "异常路径状态应为 1");
        assertTrue(log.getErrorMsg().contains("禁止删除演示用户"));
        assertEquals("", log.getJsonResult(), "异常路径不应记录返回摘要");
    }

    @Test
    void sensitiveKeysAreMaskedRecursivelyCaseInsensitively() {
        OperFake mapper = new OperFake();
        OperLogFixtureService fixture = startContext(mapper);
        loginAs("admin");

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("oldPassword", "old-secret");
        nested.put("Authorization", "Bearer abc");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("password", "p@ssw0rd");
        body.put("Code", "778899");
        body.put("accessToken", "tk-123");
        body.put("nested", nested);
        body.put("postCode", "dev");
        fixture.updateConfig(body);

        assertEquals(1, mapper.rows().size());
        String param = mapper.rows().get(0).getOperParam();
        assertFalse(param.contains("p@ssw0rd"), "明文密码不得落库");
        assertFalse(param.contains("778899"), "明文验证码不得落库");
        assertFalse(param.contains("tk-123"), "明文令牌不得落库");
        assertFalse(param.contains("old-secret"), "嵌套旧密码不得落库");
        assertFalse(param.contains("Bearer abc"), "Authorization 头不得落库");
        assertTrue(param.contains("******"), "敏感值应被掩码替换");
        assertTrue(param.contains("dev"), "非敏感字段必须原样保留");
    }

    @Test
    void mapperFailureKeepsBusinessResultAndNeverRecursivelyLogs() {
        FailingOperFake mapper = new FailingOperFake();
        OperLogFixtureService fixture = startContext(mapper);
        loginAs("admin");

        String result = fixture.createUser("wangwu", "pwd");

        assertEquals("created:wangwu", result, "日志写库失败绝不允许替换业务返回值");
        assertEquals(1, mapper.attempts, "日志失败后不得递归重试记录");
    }

    @Test
    void mapperFailureKeepsBusinessExceptionUnchanged() {
        FailingOperFake mapper = new FailingOperFake();
        OperLogFixtureService fixture = startContext(mapper);
        loginAs("admin");

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> fixture.deleteUser("zhao6"));
        assertEquals(409, thrown.status(), "日志写库失败绝不允许替换业务异常");
        assertEquals(1, mapper.attempts, "异常路径同样不得递归记录");
    }

    @Test
    void principalAndBinaryArgsAreNotSerialized() throws Exception {
        OperFake mapper = new OperFake();
        OperLogFixtureService fixture = startContext(mapper);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                new cn.jiebaba.summer.security.userdetails.UserDetails() {
                    @Override
                    public List<cn.jiebaba.summer.security.core.GrantedAuthority> getAuthorities() {
                        return List.of();
                    }

                    @Override
                    public String getPassword() {
                        return "hash-should-never-appear";
                    }

                    @Override
                    public String getUsername() {
                        return "admin";
                    }

                    @Override
                    public boolean isEnabled() {
                        return true;
                    }
                }, null, List.of());
        AdminRequestContext.set(webRequest("POST", "/api/system/users/import", "10.0.0.8"));

        fixture.importUsers(List.of(auth, new byte[]{1, 2, 3}));

        assertEquals(1, mapper.rows().size());
        SysOperLog log = mapper.rows().get(0);
        assertEquals("10.0.0.8", log.getOperIp());
        assertFalse(log.getOperParam().contains("hash-should-never-appear"),
                "认证主体（含密码哈希）绝不进入操作参数");
        assertEquals("[[]]", log.getOperParam(), "被清理的嵌套参数应只留下空集合占位");
    }

    @Test
    void unannotatedMethodIsNotIntercepted() {
        OperFake mapper = new OperFake();
        OperLogFixtureService fixture = startContext(mapper);
        loginAs("admin");

        assertEquals("pong", fixture.ping());
        assertTrue(mapper.rows().isEmpty(), "未加 @OperLog 的方法不得产生操作日志");
    }

    @Test
    void missingRequestContextLeavesUrlAndIpEmpty() {
        OperFake mapper = new OperFake();
        OperLogFixtureService fixture = startContext(mapper);
        loginAs("admin");

        fixture.createUser("no-request", "x");

        SysOperLog log = mapper.rows().get(0);
        assertEquals("", log.getOperUrl(), "无请求上下文时 URL 应为空串");
        assertEquals("", log.getOperIp());
        assertEquals("", log.getRequestMethod());
        assertEquals("admin", log.getOperName());
    }

    // ---- 夹具与辅助 -------------------------------------------------------

    /**
     * 启动只扫描 common.log 包的最小容器并注册内存 Mapper：
     * 夹具服务经真实子类代理创建，验证的是框架 AOP 代理路径而非直接调用。
     *
     * @param mapper 内存操作日志 Mapper
     * @return 被代理的夹具服务
     */
    private OperLogFixtureService startContext(OperFake mapper) {
        context = new DefaultApplicationContext(getClass().getClassLoader(),
                new Environment(), Set.of("cn.jiebaba.summer.admin.common.log"));
        context.registerBean("sysOperLogMapper", mapper);
        context.refresh();
        return (OperLogFixtureService) context.getBean("operLogFixtureService");
    }

    /** 以指定用户名登录当前（虚拟）线程的 SecurityContext。 */
    private static void loginAs(String username) {
        SecurityContextHolder.setContext(new SecurityContext(
                new UsernamePasswordAuthenticationToken(username, null, List.of())));
    }

    /** 构造最小合法 HTTP 请求并包装为 WebRequest。 */
    private static WebRequest webRequest(String method, String path, String ip) throws Exception {
        String raw = method + " " + path + " HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\n\r\n";
        RawHttpRequest parsed = RawHttpRequest.parse(
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)), 8192, 65536);
        return new WebRequest(parsed).remoteAddress(ip);
    }

    /** 内存操作日志 Mapper。 */
    private static class OperFake extends InMemoryBaseMapper<SysOperLog> implements SysOperLogMapper {
        @Override
        protected Long idOf(SysOperLog entity) {
            return entity.getOperId();
        }

        List<SysOperLog> rows() {
            return List.copyOf(rows);
        }

        @Override
        public List<SysOperLog> selectList(AbstractWrapper<SysOperLog, ?> wrapper) {
            throw new UnsupportedOperationException("内存 Mapper 不支持 Wrapper 查询");
        }
    }

    /** 写库必失败的 Mapper：用于验证日志失败隔离与无递归。 */
    private static final class FailingOperFake extends OperFake {
        private int attempts;

        @Override
        public int insert(SysOperLog entity) {
            attempts++;
            throw new IllegalStateException("数据库不可用");
        }
    }
}
