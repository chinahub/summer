// access_token 的本地存储键，与后端 Authorization: Bearer 契约对应
const TOKEN_KEY = 'Admin-Token';

/** 读取本地保存的访问令牌，未登录返回 null */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

/** 保存访问令牌 */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

/** 清除访问令牌（登出或 401 时调用） */
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}
