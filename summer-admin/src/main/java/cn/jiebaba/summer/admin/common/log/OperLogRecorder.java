package cn.jiebaba.summer.admin.common.log;

import cn.jiebaba.summer.admin.system.log.SysOperLog;
import cn.jiebaba.summer.admin.system.log.SysOperLogMapper;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.transaction.Propagation;
import cn.jiebaba.summer.data.transaction.Transactional;
import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.SecurityContextHolder;

import java.time.LocalDateTime;

/**
 * 操作日志写入器：所有 sys_oper_log 落库的唯一入口（切面与清空审计共用）。
 * 写入一律走 {@code REQUIRES_NEW} 独立事务——挂起并独立于外层业务事务，
 * 业务回滚不影响审计落账；本类无任何 {@link OperLog} 注解，绝不会被
 * 操作日志切面递归拦截。
 */
@Service
public class OperLogRecorder {

    private final SysOperLogMapper operLogMapper;

    public OperLogRecorder(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    /**
     * 以独立事务写入一条操作日志：调用方（切面）已做完全失败隔离，
     * 这里只负责落库，任何异常原样上抛。
     *
     * @param log 待写入的操作日志实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SysOperLog log) {
        operLogMapper.insert(log);
    }

    /**
     * 清空审计：清空成功后调用，以独立事务补记一条 businessType=7 的日志，
     * 绝不随外层（可能回滚的）事务一起回滚。
     *
     * @param module 被清空的日志模块（操作日志/登录日志）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordClearAudit(String module) {
        SysOperLog log = new SysOperLog();
        log.setTitle(module);
        log.setBusinessType(7);
        log.setMethod("");
        log.setRequestMethod(AdminRequestContext.method());
        log.setOperName(currentUsername());
        log.setDeptName("");
        log.setOperUrl(SensitiveDataSanitizer.cap(AdminRequestContext.path(), 255));
        log.setOperIp(SensitiveDataSanitizer.cap(AdminRequestContext.remoteAddress(), 128));
        log.setOperLocation("");
        log.setOperParam("");
        log.setJsonResult("");
        log.setStatus(0);
        log.setErrorMsg("");
        log.setOperTime(LocalDateTime.now());
        log.setCostTime(0L);
        record(log);
    }

    /** 当前登录名；无认证上下文（匿名/系统调用）返回空串。 */
    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getAuthentication();
        return authentication == null ? "" : authentication.getName();
    }
}
