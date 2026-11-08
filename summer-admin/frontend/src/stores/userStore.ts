import { create } from 'zustand';
import { clearToken, getToken, setToken } from '@/utils/auth';

export interface UserInfo {
  userId?: number;
  userName?: string;
  nickName?: string;
  avatar?: string;
  roles: string[];
  permissions: string[];
}

interface UserState {
  token: string | null;
  userInfo: UserInfo | null;
  setToken: (token: string) => void;
  setUserInfo: (userInfo: UserInfo) => void;
  reset: () => void;
}

/** 登录用户状态：令牌与 userinfo 解析出的角色/权限 */
export const useUserStore = create<UserState>((set) => ({
  token: getToken(),
  userInfo: null,
  setToken: (token) => {
    setToken(token);
    set({ token });
  },
  setUserInfo: (userInfo) => set({ userInfo }),
  reset: () => {
    clearToken();
    set({ token: null, userInfo: null });
  },
}));
