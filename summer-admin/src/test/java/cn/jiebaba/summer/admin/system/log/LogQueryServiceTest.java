package cn.jiebaba.summer.admin.system.log;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.common.log.OperLogRecorder;
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import cn.jiebaba.summer.admin.testsupport.WrapperSupport;
import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.page.IPage;
import cn.jiebaba.summer.data.transaction.Propagation;
import cn.jiebaba.summer.data.transaction.Transactional;
import cn.jiebaba.summer.security.core.SecurityContext;
import cn.jiebaba.summer.security.core.SecurityContextHolder;
import cn.jiebaba.summer.security.core.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志查询服务纯内存单测：两类日志（操作/登录）的条件分页、详情（含 404）、
 * 批量删除（空入参 400）与清空（清空空表也留审计事件）；审计事件以
 * REQUIRES_NEW 独立事务写入，绝不随外层事务回滚，且清空审计落在操作日志表。
 */
class LogQueryServiceTest {

    private OperFake operMapper;
    private LoginFake loginMapper;
    private LogQueryService service;

    @BeforeEach
    void setUp() {
        operMapper = new OperFake();
        loginMapper = new LoginFake();
        service = new LogQueryService(operMapper, loginMapper, new OperLogRecorder(operMapper));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void operPageFiltersByTitleOperNameBusinessTypeAndStatus() {
        seedOperLogs();

        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        IPage<SysOperLog> byTitle = service.pageOperLogs(query, "用户", null, null, null);
        assertEquals(2, byTitle.total());
        assertEquals(2L, byTitle.records().get(0).getOperId(), "应按 oper_id 倒序");

        IPage<SysOperLog> byName = service.pageOperLogs(query, null, "admin", null, null);
        assertEquals(2, byName.total());

        IPage<SysOperLog> byType = service.pageOperLogs(query, null, null, 3, null);
        assertEquals(1, byType.total());
        assertEquals(2L, byType.records().get(0).getOperId());

        IPage<SysOperLog> byStatus = service.pageOperLogs(query, null, null, null, 1);
        assertEquals(1, byStatus.total());
        assertEquals(3L, byStatus.records().get(0).getOperId());

        IPage<SysOperLog> combined = service.pageOperLogs(query, "用户", "admin", 1, 0);
        assertEquals(1, combined.total());
        assertEquals(1L, combined.records().get(0).getOperId());
    }

    @Test
    void operPageSlicesByPageNumAndPageSize() {
        seedOperLogs();
        PageQuery query = new PageQuery();
        query.setPageNum(2);
        query.setPageSize(2);

        IPage<SysOperLog> page = service.pageOperLogs(query, null, null, null, null);

        assertEquals(3, page.total());
        assertEquals(1, page.records().size(), "第二页应只剩 oper_id 最早的一条");
        assertEquals(1L, page.records().get(0).getOperId());
    }

    @Test
    void operDetailReturnsRowOr404() {
        seedOperLogs();
        assertEquals("用户管理", service.getOperLog(1L).getTitle());
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.getOperLog(null)).status());
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.getOperLog(999L)).status());
    }

    @Test
    void operBatchDeleteValidatesAndCounts() {
        seedOperLogs();
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.deleteOperLogs(null)).status());
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.deleteOperLogs(List.of())).status());

        int removed = service.deleteOperLogs(List.of(1L, 2L, 999L));
        assertEquals(2, removed, "只统计实际删除的行，不存在的 ID 静默忽略");
        assertEquals(1, operMapper.rows().size());

        service.deleteOperLogs(List.of(3L));
        assertTrue(operMapper.rows().isEmpty());
    }

    @Test
    void clearOperLogsEmptiesTableAndLeavesAuditEvent() {
        seedOperLogs();
        loginAs("admin");

        int cleared = service.clearOperLogs();

        assertEquals(3, cleared, "清空应返回被清掉的行数");
        assertEquals(1, operMapper.rows().size(), "清空后只应剩下审计事件本身");
        SysOperLog audit = operMapper.rows().get(0);
        assertEquals("操作日志", audit.getTitle());
        assertEquals(7, audit.getBusinessType(), "清空审计 businessType 应为 7（清空）");
        assertEquals(0, audit.getStatus());
        assertEquals("admin", audit.getOperName());
        assertNotNull(audit.getOperTime());
    }

    @Test
    void clearEmptyOperLogTableStillLeavesAuditEvent() {
        int cleared = service.clearOperLogs();

        assertEquals(0, cleared);
        assertEquals(1, operMapper.rows().size(), "即便空表，清空动作本身也必须留下审计事件");
    }

    @Test
    void loginPageFiltersByUserNameIpAndStatus() {
        seedLoginLogs();
        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        IPage<SysLoginLog> byName = service.pageLoginLogs(query, "admin", null, null);
        assertEquals(2, byName.total());
        assertEquals(2L, byName.records().get(0).getInfoId(), "应按 info_id 倒序");

        IPage<SysLoginLog> byIp = service.pageLoginLogs(query, null, "192.168", null);
        assertEquals(2, byIp.total());

        IPage<SysLoginLog> byStatus = service.pageLoginLogs(query, null, null, "1");
        assertEquals(1, byStatus.total());
        assertEquals(2L, byStatus.records().get(0).getInfoId());

        IPage<SysLoginLog> combined = service.pageLoginLogs(query, "user", "10.0.0.8", "0");
        assertEquals(1, combined.total());
        assertEquals(3L, combined.records().get(0).getInfoId());
    }

    @Test
    void loginDetailReturnsRowOr404() {
        seedLoginLogs();
        assertEquals("admin", service.getLoginLog(1L).getUserName());
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.getLoginLog(999L)).status());
    }

    @Test
    void loginBatchDeleteValidatesAndCounts() {
        seedLoginLogs();
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.deleteLoginLogs(List.of())).status());

        int removed = service.deleteLoginLogs(List.of(1L, 3L));
        assertEquals(2, removed);
        assertEquals(1, loginMapper.rows().size());
    }

    @Test
    void clearLoginLogsEmptiesTableAndLeavesAuditInOperTable() {
        seedLoginLogs();
        loginAs("admin");

        int cleared = service.clearLoginLogs();

        assertEquals(3, cleared);
        assertTrue(loginMapper.rows().isEmpty(), "登录日志表应被清空");
        assertEquals(1, operMapper.rows().size(), "审计事件应写入操作日志表");
        SysOperLog audit = operMapper.rows().get(0);
        assertEquals("登录日志", audit.getTitle());
        assertEquals(7, audit.getBusinessType());
        assertEquals("admin", audit.getOperName());
    }

    @Test
    void clearAuditRunsInIndependentNewTransaction() throws NoSuchMethodException {
        Transactional record = OperLogRecorder.class
                .getMethod("record", SysOperLog.class).getAnnotation(Transactional.class);
        Transactional clearAudit = OperLogRecorder.class
                .getMethod("recordClearAudit", String.class).getAnnotation(Transactional.class);

        assertNotNull(record, "操作日志写入应声明独立事务");
        assertNotNull(clearAudit, "清空审计应声明独立事务");
        assertEquals(Propagation.REQUIRES_NEW, record.propagation(),
                "操作日志不得随业务事务回滚");
        assertEquals(Propagation.REQUIRES_NEW, clearAudit.propagation(),
                "清空审计不得随外层事务回滚");
    }

    @Test
    void clearMethodsDeclareRequiredTransaction() throws NoSuchMethodException {
        Transactional operClear = LogQueryService.class
                .getMethod("clearOperLogs").getAnnotation(Transactional.class);
        Transactional loginClear = LogQueryService.class
                .getMethod("clearLoginLogs").getAnnotation(Transactional.class);

        assertNotNull(operClear, "clearOperLogs 应声明事务，删除中途失败必须整体回滚");
        assertNotNull(loginClear, "clearLoginLogs 应声明事务");
        assertEquals(Propagation.REQUIRED, operClear.propagation());
        assertEquals(Propagation.REQUIRED, loginClear.propagation());
    }

    @Test
    void auditFailureDoesNotAffectClearResult() {
        OperFake failingAuditStore = new OperFake() {
            @Override
            public int insert(SysOperLog entity) {
                throw new IllegalStateException("审计库不可用");
            }
        };
        LogQueryService isolated = new LogQueryService(operMapper, loginMapper,
                new OperLogRecorder(failingAuditStore));
        seedOperLogs();
        seedLoginLogs();

        assertEquals(3, isolated.clearOperLogs(), "审计写库失败绝不允许替换清空的成功返回");
        assertTrue(operMapper.rows().isEmpty(), "审计失败不影响已完成的清空结果");

        assertEquals(3, isolated.clearLoginLogs());
        assertTrue(loginMapper.rows().isEmpty());
    }

    // ---- 夹具与辅助 -------------------------------------------------------

    private void seedOperLogs() {
        operMapper.insert(oper(1L, "用户管理", 1, "admin", 0));
        operMapper.insert(oper(2L, "用户管理", 3, "admin", 0));
        operMapper.insert(oper(3L, "岗位管理", 1, "operator", 1));
    }

    private static SysOperLog oper(Long id, String title, Integer businessType,
                                   String operName, Integer status) {
        SysOperLog log = new SysOperLog();
        log.setOperId(id);
        log.setTitle(title);
        log.setBusinessType(businessType);
        log.setOperName(operName);
        log.setStatus(status);
        log.setOperTime(LocalDateTime.now());
        return log;
    }

    private void seedLoginLogs() {
        loginMapper.insert(login(1L, "admin", "192.168.1.1", "0"));
        loginMapper.insert(login(2L, "admin", "192.168.1.2", "1"));
        loginMapper.insert(login(3L, "user", "10.0.0.8", "0"));
    }

    private static SysLoginLog login(Long id, String userName, String ip, String status) {
        SysLoginLog log = new SysLoginLog();
        log.setInfoId(id);
        log.setUserName(userName);
        log.setIpaddr(ip);
        log.setStatus(status);
        log.setLoginTime(LocalDateTime.now());
        return log;
    }

    private static void loginAs(String username) {
        SecurityContextHolder.setContext(new SecurityContext(
                new UsernamePasswordAuthenticationToken(username, null, List.of())));
    }

    /** 内存操作日志 Mapper，selectPage 由 WrapperSupport 求值。 */
    private static class OperFake extends InMemoryBaseMapper<SysOperLog>
            implements SysOperLogMapper {
        @Override
        protected Long idOf(SysOperLog entity) {
            return entity.getOperId();
        }

        List<SysOperLog> rows() {
            return List.copyOf(rows);
        }

        @Override
        public List<SysOperLog> selectList(AbstractWrapper<SysOperLog, ?> wrapper) {
            return WrapperSupport.select(rows, wrapper, OPER_ACCESSOR);
        }

        @Override
        public IPage<SysOperLog> selectPage(IPage<SysOperLog> page,
                                            AbstractWrapper<SysOperLog, ?> wrapper) {
            List<SysOperLog> all = WrapperSupport.select(rows, wrapper, OPER_ACCESSOR);
            page.setTotal(all.size());
            int from = (int) Math.min(page.offset(), all.size());
            int to = (int) Math.min(from + page.size(), all.size());
            page.setRecords(all.subList(from, to));
            return page;
        }
    }

    /** 内存登录日志 Mapper，selectPage 由 WrapperSupport 求值。 */
    private static final class LoginFake extends InMemoryBaseMapper<SysLoginLog>
            implements SysLoginLogMapper {
        @Override
        protected Long idOf(SysLoginLog entity) {
            return entity.getInfoId();
        }

        List<SysLoginLog> rows() {
            return List.copyOf(rows);
        }

        @Override
        public List<SysLoginLog> selectList(AbstractWrapper<SysLoginLog, ?> wrapper) {
            return WrapperSupport.select(rows, wrapper, LOGIN_ACCESSOR);
        }

        @Override
        public IPage<SysLoginLog> selectPage(IPage<SysLoginLog> page,
                                             AbstractWrapper<SysLoginLog, ?> wrapper) {
            List<SysLoginLog> all = WrapperSupport.select(rows, wrapper, LOGIN_ACCESSOR);
            page.setTotal(all.size());
            int from = (int) Math.min(page.offset(), all.size());
            int to = (int) Math.min(from + page.size(), all.size());
            page.setRecords(all.subList(from, to));
            return page;
        }
    }

    /** 操作日志列访问器：同时兼容下划线列名与驼峰属性名。 */
    private static final Function<String, Function<SysOperLog, Object>> OPER_ACCESSOR = col -> switch (col) {
        case "oper_id", "operId" -> SysOperLog::getOperId;
        case "title" -> SysOperLog::getTitle;
        case "business_type", "businessType" -> SysOperLog::getBusinessType;
        case "oper_name", "operName" -> SysOperLog::getOperName;
        case "status" -> SysOperLog::getStatus;
        case "oper_time", "operTime" -> SysOperLog::getOperTime;
        default -> throw new IllegalArgumentException("未知列: " + col);
    };

    /** 登录日志列访问器：同时兼容下划线列名与驼峰属性名。 */
    private static final Function<String, Function<SysLoginLog, Object>> LOGIN_ACCESSOR = col -> switch (col) {
        case "info_id", "infoId" -> SysLoginLog::getInfoId;
        case "user_name", "userName" -> SysLoginLog::getUserName;
        case "ipaddr" -> SysLoginLog::getIpaddr;
        case "status" -> SysLoginLog::getStatus;
        case "login_time", "loginTime" -> SysLoginLog::getLoginTime;
        default -> throw new IllegalArgumentException("未知列: " + col);
    };
}
