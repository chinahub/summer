import { request } from '@/api/request';

/** 首页汇总：用户/角色/部门/公告数量（与后端 DashboardSummary 对应） */
export interface DashboardSummary {
  userCount: number;
  roleCount: number;
  deptCount: number;
  noticeCount: number;
}

/** 首页汇总：真实计数来自 /api/dashboard/summary */
export function dashboardSummaryApi(): Promise<DashboardSummary> {
  return request<DashboardSummary>({ url: '/dashboard/summary', method: 'get' });
}
