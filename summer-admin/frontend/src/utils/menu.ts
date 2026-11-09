import type { RouterVo } from '@/api/types';
import { hasPermission } from '@/utils/permission';

/**
 * 按权限过滤后端下发的菜单树：无权限的菜单/按钮被剔除，
 * 保留节点的已授权子级（按钮被过滤后菜单以空 children 保留，不参与路由渲染）。
 */
export function filterAuthorizedMenus(menus: RouterVo[], permissions: string[]): RouterVo[] {
  const result: RouterVo[] = [];
  for (const menu of menus) {
    if (menu.meta?.perms && !hasPermission(menu.meta.perms, permissions)) {
      continue;
    }
    result.push({ ...menu, children: filterAuthorizedMenus(menu.children ?? [], permissions) });
  }
  return result;
}

/** 外部链接允许的安全协议白名单 */
const SAFE_EXTERNAL_PROTOCOLS = new Set(['http:', 'https:', 'mailto:', 'tel:']);

/** 判断是否为外部链接（非 / 开头的地址） */
export function isExternalPath(path?: string): boolean {
  return !!path && !path.startsWith('/');
}

/** 外部链接协议安全检查：仅放行 http/https/mailto/tel，拒绝 javascript/data/vbscript 等 */
export function isSafeExternalUrl(url: string): boolean {
  try {
    const protocol = new URL(url, window.location.href).protocol;
    return SAFE_EXTERNAL_PROTOCOLS.has(protocol.toLowerCase());
  } catch {
    return false;
  }
}

/** 父子路由路径拼接：补全前导斜杠并去重中间的斜杠 */
export function joinRoutePath(parent: string, child: string): string {
  const base = parent.startsWith('/') ? parent : `/${parent}`;
  if (!child) {
    return base;
  }
  const leaf = child.startsWith('/') ? child : `/${child}`;
  return `${base.replace(/\/+$/, '')}${leaf}`;
}

export interface FlattenedRoute {
  /** 从根节点拼接出的完整路径，如 /system/user */
  fullPath: string;
  route: RouterVo;
}

/** 将后端路由树扁平化为带完整路径的列表（保持先序遍历顺序） */
export function flattenRoutes(routes: RouterVo[], parentPath = ''): FlattenedRoute[] {
  const result: FlattenedRoute[] = [];
  for (const route of routes) {
    const fullPath = parentPath ? joinRoutePath(parentPath, route.path) : route.path;
    result.push({ fullPath, route });
    if (route.children?.length) {
      result.push(...flattenRoutes(route.children, fullPath));
    }
  }
  return result;
}

/** 按完整路径精确匹配当前路由，不做前缀/部分匹配 */
export function findRouteByPath(
  routes: RouterVo[],
  path: string,
): FlattenedRoute | undefined {
  return flattenRoutes(routes).find((item) => item.fullPath === path);
}

/** 是否为目录节点：无组件、仅作为 children 容器（Layout 重定向节点也算目录） */
function isDirectory(route: RouterVo): boolean {
  return !route.component && (!!route.redirect || !!route.children);
}

/**
 * 过滤空目录：剔除无组件且无可见子级的目录节点；
 * 有组件的叶子节点（含个人中心等前端页）始终保留，递归自底向上收敛。
 */
export function filterEmptyDirectories(routes: RouterVo[]): RouterVo[] {
  const result: RouterVo[] = [];
  for (const route of routes) {
    const children = route.children?.length ? filterEmptyDirectories(route.children) : [];
    const visible = children.filter((child) => !child.hidden);
    if (isDirectory(route) && visible.length === 0) {
      continue;
    }
    result.push(children.length ? { ...route, children } : { ...route, children: [] });
  }
  return result;
}
