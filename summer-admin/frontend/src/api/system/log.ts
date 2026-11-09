import { request } from '@/api/request';
import type { TableData } from '@/api/types';

/** 操作日志实体：与后端 SysOperLog 字段一一对应（敏感字段已由后端脱敏） */
export interface OperLogVo {
  operId: number;
  title?: string;
  businessType?: number;
  method?: string;
  requestMethod?: string;
  operName?: string;
  deptName?: string;
  operUrl?: string;
  operIp?: string;
  operLocation?: string;
  operParam?: string;
  jsonResult?: string;
  status?: number;
  errorMsg?: string;
  operTime?: string;
  costTime?: number;
}

/** 登录日志实体：与后端 SysLoginLog 字段一一对应（敏感字段已由后端脱敏） */
export interface LoginLogVo {
  infoId: number;
  userName?: string;
  ipaddr?: string;
  loginLocation?: string;
  browser?: string;
  os?: string;
  status?: string;
  msg?: string;
  loginTime?: string;
}

/** 操作日志分页查询参数：与后端 /operations/list 查询参数一致 */
export interface OperLogPageParams {
  pageNum: number;
  pageSize: number;
  title?: string;
  operName?: string;
  businessType?: number;
  status?: number;
}

/** 登录日志分页查询参数：与后端 /logins/list 查询参数一致 */
export interface LoginLogPageParams {
  pageNum: number;
  pageSize: number;
  userName?: string;
  ipaddr?: string;
  status?: string;
}

/** 操作日志分页列表 */
export function listOperLogsApi(params: OperLogPageParams): Promise<TableData<OperLogVo>> {
  return request<TableData<OperLogVo>>({ url: '/system/logs/operations/list', method: 'get', params });
}

/** 操作日志详情 */
export function getOperLogApi(operId: number): Promise<OperLogVo> {
  return request<OperLogVo>({ url: `/system/logs/operations/${operId}`, method: 'get' });
}

/** 批量删除操作日志：逗号分隔 ID（如 1,2,3） */
export function deleteOperLogsApi(ids: string): Promise<void> {
  return request<void>({ url: `/system/logs/operations/${ids}`, method: 'delete' });
}

/** 清空操作日志：DELETE 非 GET；清空后后端补记一条清空审计事件 */
export function clearOperLogsApi(): Promise<void> {
  return request<void>({ url: '/system/logs/operations/clean', method: 'delete' });
}

/** 登录日志分页列表 */
export function listLoginLogsApi(params: LoginLogPageParams): Promise<TableData<LoginLogVo>> {
  return request<TableData<LoginLogVo>>({ url: '/system/logs/logins/list', method: 'get', params });
}

/** 登录日志详情 */
export function getLoginLogApi(infoId: number): Promise<LoginLogVo> {
  return request<LoginLogVo>({ url: `/system/logs/logins/${infoId}`, method: 'get' });
}

/** 批量删除登录日志：逗号分隔 ID */
export function deleteLoginLogsApi(ids: string): Promise<void> {
  return request<void>({ url: `/system/logs/logins/${ids}`, method: 'delete' });
}

/** 清空登录日志：DELETE 非 GET；审计事件写入操作日志表 */
export function clearLoginLogsApi(): Promise<void> {
  return request<void>({ url: '/system/logs/logins/clean', method: 'delete' });
}
