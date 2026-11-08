package cn.jiebaba.summer.admin.dashboard;

import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RestController;

/**
 * 首页汇总接口：{@code GET /api/dashboard/summary} 只要求已认证
 * （安全链对全部 /api 强制 JWT，此处不追加业务权限），
 * 返回用户/角色/部门/已发布公告四项真实计数。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 首页四项计数汇总：已认证即可访问。 */
    @GetMapping("/summary")
    public R<DashboardSummary> summary() {
        return R.ok(dashboardService.summary());
    }
}
