import type { RouterVo } from '@/api/types';
import { FRONTEND_STATIC_PAGES } from '@/pages/pageRegistry';
import { filterEmptyDirectories, findRouteByPath, joinRoutePath } from '@/utils/menu';

/** 路由解析结果：命中的白名单组件键、错误页或目录重定向目标 */
export type ResolvedView =
  | { kind: 'page'; componentKey: string; title?: string }
  | { kind: 'forbidden' }
  | { kind: 'redirect'; to: string };

/**
 * 解析 hash 路径到白名单页面：
 * - 首页固定渲染 index；前端内置页（个人中心等）直接命中
 * - 后端动态路由树精确匹配 → 白名单组件键，未知组件由 resolvePage 回退 404
 * - 目录节点直达 → 重定向到第一个可见子菜单
 * - 树中不存在 → 403（后端只下发已授权菜单，缺失即无权限）
 */
export function resolveCurrentView(pathname: string, routes: RouterVo[]): ResolvedView {
  if (pathname === '/') {
    return { kind: 'page', componentKey: 'index', title: '首页' };
  }
  const staticHit = FRONTEND_STATIC_PAGES[pathname];
  if (staticHit) {
    return { kind: 'page', componentKey: staticHit.componentKey, title: staticHit.title };
  }
  const hit = findRouteByPath(routes, pathname);
  if (!hit) {
    return { kind: 'forbidden' };
  }
  if (!hit.route.component) {
    const firstChild = filterEmptyDirectories([hit.route])[0]?.children?.find((c) => !c.hidden);
    if (firstChild) {
      return { kind: 'redirect', to: joinRoutePath(hit.fullPath, firstChild.path) };
    }
    return { kind: 'forbidden' };
  }
  return { kind: 'page', componentKey: hit.route.component, title: hit.route.meta?.title };
}
