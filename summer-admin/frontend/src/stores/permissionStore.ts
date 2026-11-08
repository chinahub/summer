import { create } from 'zustand';
import type { RouterVo } from '@/api/types';

interface PermissionState {
  routes: RouterVo[];
  setRoutes: (routes: RouterVo[]) => void;
  reset: () => void;
}

/** 权限路由状态：后端 /auth/routers 下发的动态路由树 */
export const usePermissionStore = create<PermissionState>((set) => ({
  routes: [],
  setRoutes: (routes) => set({ routes }),
  reset: () => set({ routes: [] }),
}));
