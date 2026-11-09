import { request } from '@/api/request';
import type { TableData } from '@/api/types';

/** 公告状态：0已发布 1关闭 2草稿，与后端 NoticeService 常量一致 */
export const NOTICE_STATUS = {
  PUBLISHED: '0',
  CLOSED: '1',
  DRAFT: '2',
} as const;

/** 公告实体：与后端 SysNotice 字段一一对应 */
export interface NoticeVo {
  noticeId?: number;
  noticeTitle: string;
  noticeType?: string;
  noticeContent?: string;
  status?: string;
  createTime?: string;
}

/** 公告分页查询参数：管理端 /list 查询参数，可见全部状态（含草稿） */
export interface NoticePageParams {
  pageNum: number;
  pageSize: number;
  noticeTitle?: string;
  noticeType?: string;
  status?: string;
}

/** 公告保存载荷：新增/修改共用（修改时携带 noticeId） */
export type NoticePayload = Partial<NoticeVo> & { noticeTitle: string };

/** 管理端公告分页列表：可见草稿 */
export function listNoticeApi(params: NoticePageParams): Promise<TableData<NoticeVo>> {
  return request<TableData<NoticeVo>>({ url: '/system/notices/list', method: 'get', params });
}

/** 已发布公告分页列表：仅要求已认证，草稿与关闭公告不进入结果 */
export function listPublishedNoticeApi(params: {
  pageNum: number;
  pageSize: number;
}): Promise<TableData<NoticeVo>> {
  return request<TableData<NoticeVo>>({ url: '/system/notices/published/list', method: 'get', params });
}

/** 新增公告 */
export function createNoticeApi(data: NoticePayload): Promise<number> {
  return request<number>({ url: '/system/notices', method: 'post', data });
}

/** 修改公告（含发布状态切换） */
export function updateNoticeApi(data: NoticePayload): Promise<void> {
  return request<void>({ url: '/system/notices', method: 'put', data });
}

/** 删除公告（物理删除） */
export function deleteNoticeApi(noticeId: number): Promise<void> {
  return request<void>({ url: `/system/notices/${noticeId}`, method: 'delete' });
}
