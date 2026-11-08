import { request } from '@/api/request';
import type { LoginResult } from '@/api/types';

export interface LoginParams {
  username: string;
  password: string;
  code?: string;
  uuid?: string;
}

/** 登录：后端在 data 中返回 access_token/token_type/expires_in */
export function loginApi(params: LoginParams): Promise<LoginResult> {
  return request<LoginResult>({ url: '/auth/login', method: 'post', data: params });
}

/** 登出：无状态令牌服务端不撤销，仅记录日志，前端负责清除令牌 */
export function logoutApi(): Promise<void> {
  return request<void>({ url: '/auth/logout', method: 'post' });
}

/** 当前用户资料：基本信息、角色与权限集合 */
export function userinfoApi(): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: '/auth/userinfo', method: 'get' });
}
