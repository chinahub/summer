import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios';
import { usePermissionStore } from '@/stores/permissionStore';
import { useUserStore } from '@/stores/userStore';
import { getToken } from '@/utils/auth';

/** 全局 axios 实例：统一 /api 前缀、Bearer 令牌与业务码处理 */
export const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

/** 重置登录态：令牌、用户资料与动态路由一并清空（登出或 401 时调用） */
function resetAuthState(): void {
  useUserStore.getState().reset();
  usePermissionStore.getState().reset();
}

// 请求拦截：携带访问令牌
service.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截：HTTP 401 清空登录态回登录页；业务 code 非 200 拒绝
service.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) {
        // 分页响应（含 rows）整体返回，普通响应解包 data
        return 'rows' in body ? body : body.data;
      }
      return Promise.reject(new Error(body.msg ?? '请求失败'));
    }
    return body;
  },
  (error) => {
    if (error.response?.status === 401) {
      resetAuthState();
      // 精确匹配登录路由，避免在登录页上反复跳转
      if (window.location.hash !== '#/login') {
        window.location.hash = '#/login';
      }
    }
    // 其余 HTTP 错误统一归一化为携带后端 msg 的 Error
    const msg = error.response?.data?.msg;
    return Promise.reject(msg ? new Error(msg) : error);
  },
);

/**
 * 发起业务请求：成功返回解包后的 data（分页响应返回整个 TableData），
 * 失败返回携带后端 msg 的 Error。
 */
export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return service.request(config) as unknown as Promise<T>;
}
