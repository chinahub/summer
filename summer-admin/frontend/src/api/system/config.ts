import { request } from '@/api/request';
import type { TableData } from '@/api/types';

/** 参数实体：与后端 SysConfig 字段一一对应 */
export interface ConfigVo {
  configId?: number;
  configName: string;
  configKey: string;
  configValue?: string;
  configType?: string;
  status?: string;
  createTime?: string;
}

/** 参数分页查询参数：与后端 /list 查询参数一致 */
export interface ConfigPageParams {
  pageNum: number;
  pageSize: number;
  configName?: string;
  configKey?: string;
  configType?: string;
  status?: string;
}

/** 参数保存载荷：新增/修改共用（修改时携带 configId） */
export type ConfigPayload = Partial<ConfigVo> & { configName: string; configKey: string };

/** 参数分页列表 */
export function listConfigApi(params: ConfigPageParams): Promise<TableData<ConfigVo>> {
  return request<TableData<ConfigVo>>({ url: '/system/configs/list', method: 'get', params });
}

/** 新增参数（键名唯一） */
export function createConfigApi(data: ConfigPayload): Promise<number> {
  return request<number>({ url: '/system/configs', method: 'post', data });
}

/** 修改参数 */
export function updateConfigApi(data: ConfigPayload): Promise<void> {
  return request<void>({ url: '/system/configs', method: 'put', data });
}

/** 删除参数（逻辑删除；内置初始化标记由后端兜底保护） */
export function deleteConfigApi(configId: number): Promise<void> {
  return request<void>({ url: `/system/configs/${configId}`, method: 'delete' });
}
