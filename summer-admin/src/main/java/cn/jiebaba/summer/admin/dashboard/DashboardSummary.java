package cn.jiebaba.summer.admin.dashboard;

/**
 * 首页汇总响应体：用户/角色/部门/已发布公告四项真实计数。
 * 全部为聚合数字，不含任何监控明细或敏感字段。
 */
public class DashboardSummary {

    /** 未删除用户数 */
    private final long userCount;

    /** 未删除角色数 */
    private final long roleCount;

    /** 未删除部门数 */
    private final long deptCount;

    /** 已发布公告数（草稿与关闭不计入） */
    private final long noticeCount;

    public DashboardSummary(long userCount, long roleCount, long deptCount, long noticeCount) {
        this.userCount = userCount;
        this.roleCount = roleCount;
        this.deptCount = deptCount;
        this.noticeCount = noticeCount;
    }

    public long getUserCount() {
        return userCount;
    }

    public long getRoleCount() {
        return roleCount;
    }

    public long getDeptCount() {
        return deptCount;
    }

    public long getNoticeCount() {
        return noticeCount;
    }
}
