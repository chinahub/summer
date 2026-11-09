import { request } from '@/api/request';
import type { LoginResult, RouterVo } from '@/api/types';

export interface LoginParams {
  username: string;
  password: string;
  code?: string;
  uuid?: string;
}

/** 图形验证码：开关、一次性 UUID 与 Base64 PNG（data URL） */
export interface CaptchaResult {
  captchaEnabled: boolean;
  uuid: string;
  img: string;
}

/** 当前用户资料负载：user 基本信息 + roles 角色 + permissions 权限标识 */
export interface UserinfoResult {
  user: {
    userId: number;
    userName: string;
    nickName?: string;
    deptId?: number;
    email?: string;
    phonenumber?: string;
    sex?: string;
    avatar?: string;
  };
  roles: string[];
  permissions: string[];
}

/** 获取图形验证码；开关关闭时 captchaEnabled 为 false、img 为空串 */
export function captchaApi(): Promise<CaptchaResult> {
  return request<CaptchaResult>({ url: '/auth/captcha', method: 'get' });
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
export function userinfoApi(): Promise<UserinfoResult> {
  return request<UserinfoResult>({ url: '/auth/userinfo', method: 'get' });
}

/** 当前用户动态路由：仅含目录与菜单，按钮不进路由但权限标识已下发 */
export function routersApi(): Promise<RouterVo[]> {
  return request<RouterVo[]>({ url: '/auth/routers', method: 'get' });
}
