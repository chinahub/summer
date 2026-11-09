import { request } from '@/api/request';
import type { TableData } from '@/api/types';

/** 字典类型实体：与后端 SysDictType 字段一一对应 */
export interface DictTypeVo {
  dictId: number;
  dictName: string;
  dictType: string;
  status?: string;
  createTime?: string;
}

/** 字典数据实体：与后端 SysDictData 字段一一对应（管理端） */
export interface DictDataVo {
  dictCode?: number;
  dictType: string;
  dictLabel: string;
  dictValue: string;
  dictSort?: number;
  cssClass?: string;
  listClass?: string;
  isDefault?: string;
  status?: string;
  createTime?: string;
}

/** 字典类型分页查询参数：与后端 /types/list 查询参数一致 */
export interface DictTypePageParams {
  pageNum: number;
  pageSize: number;
  dictName?: string;
  dictType?: string;
  status?: string;
}

/** 字典数据分页查询参数：与后端 /data/list 查询参数一致 */
export interface DictDataPageParams {
  pageNum: number;
  pageSize: number;
  dictType?: string;
  dictLabel?: string;
  status?: string;
}

/** 字典类型保存载荷：新增/修改共用（修改时携带 dictId） */
export type DictTypePayload = Partial<DictTypeVo> & { dictName: string; dictType: string };

/** 字典数据保存载荷：新增/修改共用（修改时携带 dictCode） */
export type DictDataPayload = Partial<DictDataVo> & {
  dictType: string;
  dictLabel: string;
  dictValue: string;
};

/** 字典类型分页列表 */
export function listDictTypeApi(params: DictTypePageParams): Promise<TableData<DictTypeVo>> {
  return request<TableData<DictTypeVo>>({ url: '/system/dict/types/list', method: 'get', params });
}

/** 新增字典类型 */
export function createDictTypeApi(data: DictTypePayload): Promise<number> {
  return request<number>({ url: '/system/dict/types', method: 'post', data });
}

/** 修改字典类型 */
export function updateDictTypeApi(data: DictTypePayload): Promise<void> {
  return request<void>({ url: '/system/dict/types', method: 'put', data });
}

/** 删除字典类型（有字典数据时后端拒绝） */
export function deleteDictTypeApi(dictId: number): Promise<void> {
  return request<void>({ url: `/system/dict/types/${dictId}`, method: 'delete' });
}

/** 字典数据分页列表 */
export function listDictDataApi(params: DictDataPageParams): Promise<TableData<DictDataVo>> {
  return request<TableData<DictDataVo>>({ url: '/system/dict/data/list', method: 'get', params });
}

/** 按类型读取启用字典数据：仅要求已认证，供 useDict 缓存（顺序由后端保证） */
export function listDictDataByTypeApi(dictType: string): Promise<DictDataVo[]> {
  return request<DictDataVo[]>({ url: `/system/dict/data/type/${encodeURIComponent(dictType)}`, method: 'get' });
}

/** 新增字典数据 */
export function createDictDataApi(data: DictDataPayload): Promise<number> {
  return request<number>({ url: '/system/dict/data', method: 'post', data });
}

/** 修改字典数据 */
export function updateDictDataApi(data: DictDataPayload): Promise<void> {
  return request<void>({ url: '/system/dict/data', method: 'put', data });
}

/** 删除字典数据（逻辑删除） */
export function deleteDictDataApi(dictCode: number): Promise<void> {
  return request<void>({ url: `/system/dict/data/${dictCode}`, method: 'delete' });
}
