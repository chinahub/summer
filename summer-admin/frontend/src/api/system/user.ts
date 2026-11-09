import { request } from '@/api/request';
import type { TableData } from '@/api/types';

/** 用户视图对象：与后端 UserVo 字段一一对应 */
export interface UserVo {
  userId: number;
  deptId?: number;
  deptName?: string;
  userName: string;
  nickName?: string;
  email?: string;
  phonenumber?: string;
  sex?: string;
  avatar?: string;
  status?: string;
  loginIp?: string;
  loginDate?: string;
  createTime?: string;
  roleIds?: number[];
  roleNames?: string[];
  postIds?: number[];
  postNames?: string[];
}

/** 用户分页查询参数：与后端 /list 查询参数一致（deptId 仅前端树筛选） */
export interface UserPageParams {
  pageNum: number;
  pageSize: number;
  userName?: string;
  phonenumber?: string;
  status?: string;
}

/** 新增/修改用户表单值：roleIds/postIds 为空数组时转换为不携带（后端 null=保持） */
export interface UserFormValues {
  userId?: number;
  userName: string;
  nickName: string;
  password?: string;
  deptId: number;
  email?: string;
  phonenumber?: string;
  sex?: string;
  status?: string;
  roleIds?: number[];
  postIds?: number[];
}

/** 新增用户请求体：字段名与后端 CreateUserRequest 完全一致 */
export interface CreateUserPayload {
  userName: string;
  nickName: string;
  password: string;
  deptId: number;
  email?: string;
  phonenumber?: string;
  sex?: string;
  status?: string;
  roleIds?: number[];
  postIds?: number[];
}

/** 修改用户请求体：字段名与后端 UpdateUserRequest 完全一致 */
export interface UpdateUserPayload {
  userId: number;
  userName?: string;
  nickName?: string;
  deptId?: number;
  email?: string;
  phonenumber?: string;
  sex?: string;
  status?: string;
  roleIds?: number[];
  postIds?: number[];
}

/** 空关系数组归一化为 undefined：不传字段即后端"保持/不分配"语义 */
function normalizeIds(ids?: number[]): number[] | undefined {
  return ids && ids.length > 0 ? ids : undefined;
}

/** 将用户表单值转换为后端 CreateUserRequest 载荷 */
export function buildCreateUserPayload(values: UserFormValues): CreateUserPayload {
  const payload: CreateUserPayload = {
    userName: values.userName,
    nickName: values.nickName,
    password: values.password ?? '',
    deptId: values.deptId,
    email: values.email,
    phonenumber: values.phonenumber,
    sex: values.sex,
    status: values.status,
    roleIds: normalizeIds(values.roleIds),
    postIds: normalizeIds(values.postIds),
  };
  return payload;
}

/** 将用户表单值转换为后端 UpdateUserRequest 载荷（不含密码） */
export function buildUpdateUserPayload(
  values: Partial<UserFormValues> & { userId: number },
): UpdateUserPayload {
  return {
    userId: values.userId ?? 0,
    userName: values.userName,
    nickName: values.nickName,
    deptId: values.deptId,
    email: values.email,
    phonenumber: values.phonenumber,
    sex: values.sex,
    status: values.status,
    roleIds: normalizeIds(values.roleIds),
    postIds: normalizeIds(values.postIds),
  };
}

/** 用户分页列表（数据范围由后端控制） */
export function listUserApi(params: UserPageParams): Promise<TableData<UserVo>> {
  return request<TableData<UserVo>>({ url: '/system/users/list', method: 'get', params });
}

/** 用户详情（越权时后端返回 403） */
export function getUserApi(userId: number): Promise<UserVo> {
  return request<UserVo>({ url: `/system/users/${userId}`, method: 'get' });
}

/** 新增用户（含角色/岗位关系） */
export function createUserApi(data: CreateUserPayload): Promise<number> {
  return request<number>({ url: '/system/users', method: 'post', data });
}

/** 修改用户（roleIds/postIds 为 null 保持原关系） */
export function updateUserApi(data: UpdateUserPayload): Promise<void> {
  return request<void>({ url: '/system/users', method: 'put', data });
}

/** 删除用户（user_id=1 与当前用户自身后端拒绝） */
export function deleteUserApi(userId: number): Promise<void> {
  return request<void>({ url: `/system/users/${userId}`, method: 'delete' });
}

/** 修改用户状态：status 仅允许 0（正常）/1（停用） */
export function updateUserStatusApi(userId: number, status: string): Promise<void> {
  return request<void>({ url: `/system/users/${userId}/status`, method: 'put', data: { status } });
}

/** 管理端重置密码 */
export function resetUserPasswordApi(userId: number, password: string): Promise<void> {
  return request<void>({ url: `/system/users/${userId}/password`, method: 'put', data: { password } });
}
