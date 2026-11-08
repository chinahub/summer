package cn.jiebaba.summer.admin.system.log;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.admin.common.api.TableData;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.security.annotation.PreAuthorize;
import cn.jiebaba.summer.web.annotation.DeleteMapping;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志管理接口：操作日志与登录日志的分页列表、详情、批量删除、清空。
 * 全部端点声明 {@code monitor:log:*} 权限标识由框架强制执行；
 * 清空在删除完成后留下一条独立事务的"清空"审计事件（见 {@link LogQueryService}）。
 */
@RestController
@RequestMapping("/api/system/logs")
public class LogController {

    private final LogQueryService logQueryService;

    public LogController(LogQueryService logQueryService) {
        this.logQueryService = logQueryService;
    }

    /** 操作日志分页列表：模块/操作人模糊与业务类型/状态过滤。 */
    @PreAuthorize(authorities = {"monitor:log:list"})
    @GetMapping("/operations/list")
    public R<TableData<SysOperLog>> operList(PageQuery pageQuery,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "operName", required = false) String operName,
            @RequestParam(value = "businessType", required = false) Integer businessType,
            @RequestParam(value = "status", required = false) Integer status) {
        return R.ok(TableData.of(
                logQueryService.pageOperLogs(pageQuery, title, operName, businessType, status)));
    }

    /** 操作日志详情。 */
    @PreAuthorize(authorities = {"monitor:log:query"})
    @GetMapping("/operations/{operId}")
    public R<SysOperLog> operDetail(@PathVariable("operId") Long operId) {
        return R.ok(logQueryService.getOperLog(operId));
    }

    /** 批量删除操作日志：逗号分隔 ID（如 1,2,3）。 */
    @PreAuthorize(authorities = {"monitor:log:remove"})
    @DeleteMapping("/operations/{ids}")
    public R<Void> operDelete(@PathVariable("ids") String ids) {
        logQueryService.deleteOperLogs(parseIds(ids));
        return R.ok();
    }

    /** 清空操作日志（清空完成后留下一条审计事件）。 */
    @PreAuthorize(authorities = {"monitor:log:remove"})
    @DeleteMapping("/operations/clean")
    public R<Void> operClean() {
        logQueryService.clearOperLogs();
        return R.ok();
    }

    /** 登录日志分页列表：账号/IP 模糊与状态过滤。 */
    @PreAuthorize(authorities = {"monitor:log:list"})
    @GetMapping("/logins/list")
    public R<TableData<SysLoginLog>> loginList(PageQuery pageQuery,
            @RequestParam(value = "userName", required = false) String userName,
            @RequestParam(value = "ipaddr", required = false) String ipaddr,
            @RequestParam(value = "status", required = false) String status) {
        return R.ok(TableData.of(
                logQueryService.pageLoginLogs(pageQuery, userName, ipaddr, status)));
    }

    /** 登录日志详情。 */
    @PreAuthorize(authorities = {"monitor:log:query"})
    @GetMapping("/logins/{infoId}")
    public R<SysLoginLog> loginDetail(@PathVariable("infoId") Long infoId) {
        return R.ok(logQueryService.getLoginLog(infoId));
    }

    /** 批量删除登录日志：逗号分隔 ID。 */
    @PreAuthorize(authorities = {"monitor:log:remove"})
    @DeleteMapping("/logins/{ids}")
    public R<Void> loginDelete(@PathVariable("ids") String ids) {
        logQueryService.deleteLoginLogs(parseIds(ids));
        return R.ok();
    }

    /** 清空登录日志（审计事件写入操作日志表）。 */
    @PreAuthorize(authorities = {"monitor:log:remove"})
    @DeleteMapping("/logins/clean")
    public R<Void> loginClean() {
        logQueryService.clearLoginLogs();
        return R.ok();
    }

    /**
     * 解析逗号分隔的 ID 路径变量：空白段跳过；任一段非数字抛 400；
     * 整体为空抛 400。
     *
     * @param ids 逗号分隔的主键串（如 "1,2,3"）
     * @return 主键列表
     * @throws BusinessException 格式非法或为空 400
     */
    private static List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            throw new BusinessException(400, "日志ID不能为空");
        }
        List<Long> parsed = new ArrayList<>();
        for (String segment : ids.split(",")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                parsed.add(Long.parseLong(trimmed));
            } catch (NumberFormatException notNumeric) {
                throw new BusinessException(400, "日志ID格式非法: " + trimmed);
            }
        }
        if (parsed.isEmpty()) {
            throw new BusinessException(400, "日志ID不能为空");
        }
        return parsed;
    }
}
