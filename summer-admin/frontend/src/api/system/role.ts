import { request } from '@/api/request';
import type { TableData } from '@/api/types';

/** 角色实体：与后端 SysRole 字段一一对应（含 BaseEntity 审计字段） */
export interface RoleVo {
  roleId: number;
  roleName: string;
  roleKey: string;
  roleSort?: number;
  /** 数据范围存储编码（1全部 2自定义 3本部门 4本部门及以下 5仅本人） */
  dataScope?: string;
  status?: string;
  createTime?: string;
}

/** 数据范围枚举名：与后端 DataScopeType 枚举名完全一致 */
export const DATA_SCOPE_ENUMS = ['ALL', 'CUSTOM', 'DEPT', 'DEPT_AND_CHILDREN', 'SELF'] as const;
export type DataScopeTypeName = (typeof DATA_SCOPE_ENUMS)[number];

/** 数据范围展示选项：value 为后端枚举名，label 为中文说明 */
export const DATA_SCOPE_OPTIONS: { label: string; value: DataScopeTypeName }[] = [
  { label: '全部数据权限', value: 'ALL' },
  { label: '自定义数据权限', value: 'CUSTOM' },
  { label: '本部门数据权限', value: 'DEPT' },
  { label: '本部门及以下数据权限', value: 'DEPT_AND_CHILDREN' },
  { label: '仅本人数据权限', value: 'SELF' },
];

/** sys_role.data_scope 存储编码 → 枚举名；未知/空编码返回 null（拒绝默认语义） */
export function dataScopeEnumFromCode(code?: string | null): DataScopeTypeName | null {
  const mapping: Record<string, DataScopeTypeName> = {
    '1': 'ALL',
    '2': 'CUSTOM',
    '3': 'DEPT',
    '4': 'DEPT_AND_CHILDREN',
    '5': 'SELF',
  };
  if (!code) {
    return null;
  }
  return mapping[code.trim()] ?? null;
}

/** 数据范围枚举名 → sys_role.data_scope 存储编码 */
export function dataScopeCodeFromEnum(name: DataScopeTypeName): string {
  const mapping: Record<DataScopeTypeName, string> = {
    ALL: '1',
    CUSTOM: '2',
    DEPT: '3',
    DEPT_AND_CHILDREN: '4',
    SELF: '5',
  };
  return mapping[name];
}

/** 菜单授权请求体：与后端 RoleController.MenuAssignBody 一致 */
export interface MenuAssignBody {
  menuIds: number[];
}

/** 数据范围授权请求体：与后端 RoleController.DataScopeBody 一致 */
export interface DataScopeBody {
  scopeType: DataScopeTypeName;
  deptIds: number[];
}

/** 将勾选的菜单 ID 列表转换为后端菜单授权请求体 */
export function buildMenuAssignBody(menuIds: number[]): MenuAssignBody {
  return { menuIds: [...menuIds] };
}

/**
 * 将数据范围表单值转换为后端授权请求体；CUSTOM 必须携带非空部门集合，
 * 否则抛错由表单层阻止提交（对应后端"自定义需显式部门"的约束）。
 */
export function buildDataScopeBody(
  scopeType: DataScopeTypeName,
  deptIds?: number[],
): DataScopeBody {
  if (scopeType === 'CUSTOM' && (!deptIds || deptIds.length === 0)) {
    throw new Error('请选择自定义数据权限的部门');
  }
  return { scopeType, deptIds: deptIds ?? [] };
}

/** 角色分页查询参数：与后端 /list 查询参数一致 */
export interface RolePageParams {
  pageNum: number;
  pageSize: number;
  roleName?: string;
  roleKey?: string;
  status?: string;
}

/** 角色保存载荷：新增/修改共用（修改时携带 roleId） */
export type RolePayload = Partial<RoleVo> & { roleName: string; roleKey: string };

/** 角色分页列表 */
export function listRoleApi(params: RolePageParams): Promise<TableData<RoleVo>> {
  return request<TableData<RoleVo>>({ url: '/system/roles/list', method: 'get', params });
}

/** 角色详情 */
export function getRoleApi(roleId: number): Promise<RoleVo> {
  return request<RoleVo>({ url: `/system/roles/${roleId}`, method: 'get' });
}

/** 新增角色（数据范围默认 ALL） */
export function createRoleApi(data: RolePayload): Promise<number> {
  return request<number>({ url: '/system/roles', method: 'post', data });
}

/** 修改角色 */
export function updateRoleApi(data: RolePayload): Promise<void> {
  return request<void>({ url: '/system/roles', method: 'put', data });
}

/** 删除角色（内置超级管理员或已分配用户时后端拒绝） */
export function deleteRoleApi(roleId: number): Promise<void> {
  return request<void>({ url: `/system/roles/${roleId}`, method: 'delete' });
}

/** 角色自定义数据范围已选中的部门 ID 列表（编辑弹窗回填） */
export function getRoleDeptIdsApi(roleId: number): Promise<number[]> {
  return request<number[]>({ url: `/system/roles/${roleId}/depts`, method: 'get' });
}

/** 角色当前已授权的菜单 ID 列表（编辑弹窗回填） */
export function getRoleMenuIdsApi(roleId: number): Promise<number[]> {
  return request<number[]>({ url: `/system/roles/${roleId}/menus`, method: 'get' });
}

/** 替换角色菜单授权（整体一个事务） */
export function assignRoleMenusApi(roleId: number, menuIds: number[]): Promise<void> {
  return request<void>({
    url: `/system/roles/${roleId}/menus`,
    method: 'put',
    data: buildMenuAssignBody(menuIds),
  });
}

/** 保存角色数据范围（CUSTOM 显式保存部门集合） */
export function assignRoleDataScopeApi(roleId: number, body: DataScopeBody): Promise<void> {
  return request<void>({ url: `/system/roles/${roleId}/data-scope`, method: 'put', data: body });
}
