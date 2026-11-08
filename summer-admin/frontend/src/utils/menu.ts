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
