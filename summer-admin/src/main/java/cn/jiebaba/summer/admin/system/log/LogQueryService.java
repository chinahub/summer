package cn.jiebaba.summer.admin.system.log;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.common.log.OperLog;
import cn.jiebaba.summer.admin.common.log.OperLogRecorder;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.page.IPage;

import java.util.List;

/**
 * 日志查询服务：操作日志（sys_oper_log）与登录日志（sys_login_log）的
 * 条件分页、详情、批量删除与清空。两张表均为物理删除表；
 * 清空在删除完成后由 {@link OperLogRecorder} 以 REQUIRES_NEW 独立事务
 * 补记一条"清空"审计事件（写入操作日志表），绝不随外层事务回滚。
 */
@Service
public class LogQueryService {

    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final OperLogRecorder operLogRecorder;

    public LogQueryService(SysOperLogMapper operLogMapper, SysLoginLogMapper loginLogMapper,
                           OperLogRecorder operLogRecorder) {
        this.operLogMapper = operLogMapper;
        this.loginLogMapper = loginLogMapper;
        this.operLogRecorder = operLogRecorder;
    }

    /**
     * 操作日志条件分页：模块/操作人模糊过滤，业务类型/状态精确过滤，
     * 按 oper_id 倒序（最新在前）。
     *
     * @param query        分页参数
     * @param title        模块名模糊词（可空）
     * @param operName     操作人模糊词（可空）
     * @param businessType 业务类型精确值（可空）
     * @param status       状态精确值（可空，0正常 1异常）
     * @return 操作日志分页结果
     */
    public IPage<SysOperLog> pageOperLogs(PageQuery query, String title, String operName,
                                          Integer businessType, Integer status) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        if (hasText(title)) {
            wrapper.like(SysOperLog::getTitle, title);
        }
        if (hasText(operName)) {
            wrapper.like(SysOperLog::getOperName, operName);
        }
        if (businessType != null) {
            wrapper.eq(SysOperLog::getBusinessType, businessType);
        }
        if (status != null) {
            wrapper.eq(SysOperLog::getStatus, status);
        }
        wrapper.orderByDesc(SysOperLog::getOperId);
        return operLogMapper.selectPage(query.toPage(), wrapper);
    }

    /**
     * 操作日志详情：不存在统一 404。
     *
     * @param operId 操作日志主键
     * @return 日志实体
     * @throws BusinessException ID 为空 400，不存在 404
     */
    public SysOperLog getOperLog(Long operId) {
        if (operId == null) {
            throw new BusinessException(400, "日志ID不能为空");
        }
        SysOperLog log = operLogMapper.selectById(operId);
        if (log == null) {
            throw new BusinessException(404, "操作日志不存在");
        }
        return log;
    }

    /**
     * 批量删除操作日志：ID 列表必填；不存在的 ID 静默忽略；
     * 返回实际删除条数。
     *
     * @param ids 待删除主键列表
     * @return 实际删除条数
     * @throws BusinessException ID 列表为空 400
     */
    @OperLog(module = "操作日志", action = "删除")
    public int deleteOperLogs(List<Long> ids) {
        List<Long> normalized = normalizeIds(ids);
        int removed = 0;
        for (Long id : normalized) {
            removed += operLogMapper.deleteById(id);
        }
        return removed;
    }

    /**
     * 清空操作日志：物理删除全部行，随后以独立事务补记一条"清空"审计事件。
     * 审计写入失败不影响已完成的清空结果（异常由全局兜底）。
     *
     * @return 被清空的行数
     */
    public int clearOperLogs() {
        List<SysOperLog> all = operLogMapper.selectList();
        for (SysOperLog log : all) {
            operLogMapper.deleteById(log.getOperId());
        }
        operLogRecorder.recordClearAudit("操作日志");
        return all.size();
    }

    /**
     * 登录日志条件分页：账号/IP 模糊过滤，状态精确过滤，按 info_id 倒序。
     *
     * @param query    分页参数
     * @param userName 登录账号模糊词（可空）
     * @param ipaddr   登录 IP 模糊词（可空）
     * @param status   状态精确值（可空，0成功 1失败）
     * @return 登录日志分页结果
     */
    public IPage<SysLoginLog> pageLoginLogs(PageQuery query, String userName,
                                            String ipaddr, String status) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        if (hasText(userName)) {
            wrapper.like(SysLoginLog::getUserName, userName);
        }
        if (hasText(ipaddr)) {
            wrapper.like(SysLoginLog::getIpaddr, ipaddr);
        }
        if (hasText(status)) {
            wrapper.eq(SysLoginLog::getStatus, status);
        }
        wrapper.orderByDesc(SysLoginLog::getInfoId);
        return loginLogMapper.selectPage(query.toPage(), wrapper);
    }

    /**
     * 登录日志详情：不存在统一 404。
     *
     * @param infoId 登录日志主键
     * @return 日志实体
     * @throws BusinessException ID 为空 400，不存在 404
     */
    public SysLoginLog getLoginLog(Long infoId) {
        if (infoId == null) {
            throw new BusinessException(400, "日志ID不能为空");
        }
        SysLoginLog log = loginLogMapper.selectById(infoId);
        if (log == null) {
            throw new BusinessException(404, "登录日志不存在");
        }
        return log;
    }

    /**
     * 批量删除登录日志：语义同 {@link #deleteOperLogs(List)}。
     *
     * @param ids 待删除主键列表
     * @return 实际删除条数
     * @throws BusinessException ID 列表为空 400
     */
    @OperLog(module = "登录日志", action = "删除")
    public int deleteLoginLogs(List<Long> ids) {
        List<Long> normalized = normalizeIds(ids);
        int removed = 0;
        for (Long id : normalized) {
            removed += loginLogMapper.deleteById(id);
        }
        return removed;
    }

    /**
     * 清空登录日志：物理删除全部行，审计事件（标题"登录日志"）写入操作日志表，
     * 独立事务保证不随外层事务回滚。
     *
     * @return 被清空的行数
     */
    public int clearLoginLogs() {
        List<SysLoginLog> all = loginLogMapper.selectList();
        for (SysLoginLog log : all) {
            loginLogMapper.deleteById(log.getInfoId());
        }
        operLogRecorder.recordClearAudit("登录日志");
        return all.size();
    }

    /** 校验并拷贝 ID 列表：null/空一律 400。 */
    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "日志ID不能为空");
        }
        return List.copyOf(ids);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
