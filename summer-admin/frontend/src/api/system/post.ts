import { request } from '@/api/request';
import type { TableData } from '@/api/types';

/** 岗位实体：与后端 SysPost 字段一一对应 */
export interface PostVo {
  postId: number;
  postCode: string;
  postName: string;
  postSort?: number;
  status?: string;
  createTime?: string;
}

/** 岗位分页查询参数：与后端 /list 查询参数一致 */
export interface PostPageParams {
  pageNum: number;
  pageSize: number;
  postName?: string;
  postCode?: string;
  status?: string;
}

/** 岗位保存载荷：新增/修改共用（修改时携带 postId） */
export type PostPayload = Partial<PostVo> & { postName: string; postCode: string };

/** 岗位分页列表 */
export function listPostApi(params: PostPageParams): Promise<TableData<PostVo>> {
  return request<TableData<PostVo>>({ url: '/system/posts/list', method: 'get', params });
}

/** 岗位详情 */
export function getPostApi(postId: number): Promise<PostVo> {
  return request<PostVo>({ url: `/system/posts/${postId}`, method: 'get' });
}

/** 新增岗位 */
export function createPostApi(data: PostPayload): Promise<number> {
  return request<number>({ url: '/system/posts', method: 'post', data });
}

/** 修改岗位 */
export function updatePostApi(data: PostPayload): Promise<void> {
  return request<void>({ url: '/system/posts', method: 'put', data });
}

/** 删除岗位（已分配给用户时后端拒绝） */
export function deletePostApi(postId: number): Promise<void> {
  return request<void>({ url: `/system/posts/${postId}`, method: 'delete' });
}
