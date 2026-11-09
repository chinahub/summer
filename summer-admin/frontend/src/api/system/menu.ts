import { request } from '@/api/request';

/** 菜单权限实体：与后端 SysMenu 字段一一对应 */
export interface MenuVo {
  menuId: number;
  menuName: string;
  parentId: number;
  orderNum?: number;
  path?: string;
  component?: string;
  query?: string;
  isFrame?: number;
  isCache?: number;
  /** 类型：M目录 C菜单 F按钮 */
  menuType: string;
  /** 显示状态：0显示 1隐藏 */
  visible?: string;
  status?: string;
  /** 权限标识（按钮级，如 system:user:add） */
  perms?: string;
  icon?: string;
  createTime?: string;
  /** 树表格子行（前端组装，非后端字段） */
  children?: MenuVo[];
}

/** 菜单保存载荷：新增/修改共用（修改时携带 menuId） */
export type MenuPayload = Partial<MenuVo> & { menuName: string; parentId: number; menuType: string };

/** 菜单树节点：antd Tree/TreeSelect 通用结构 */
export interface MenuTreeNode {
  key: number;
  value: number;
  title: string;
  children?: MenuTreeNode[];
}

/** 节点标题：按钮级菜单附加权限标识，便于授权时辨认 */
function menuTitle(menu: MenuVo): string {
  return menu.menuType === 'F' && menu.perms ? `${menu.menuName}（${menu.perms}）` : menu.menuName;
}

/**
 * 将扁平菜单列表组装为树：parentId=0 为根，按 orderNum 排序；
 * 孤儿节点（父级缺失）挂到根，保证任何脏数据下树可渲染。
 */
export function buildMenuTree(menus: MenuVo[]): MenuTreeNode[] {
  const nodes = new Map<number, MenuTreeNode>();
  for (const menu of menus) {
    nodes.set(menu.menuId, { key: menu.menuId, value: menu.menuId, title: menuTitle(menu) });
  }
  const roots: MenuTreeNode[] = [];
  for (const menu of menus) {
    const node = nodes.get(menu.menuId)!;
    const parent = menu.parentId !== 0 ? nodes.get(menu.parentId) : undefined;
    if (parent) {
      (parent.children ??= []).push(node);
    } else {
      roots.push(node);
    }
  }
  const orderOf = new Map(menus.map((m) => [m.menuId, m.orderNum ?? 0]));
  const sortByOrder = (list: MenuTreeNode[]) => {
    list.sort((a, b) => (orderOf.get(a.key) ?? 0) - (orderOf.get(b.key) ?? 0));
    list.forEach((n) => n.children && sortByOrder(n.children));
  };
  sortByOrder(roots);
  return roots;
}

/** 全部菜单列表（菜单树由前端基于 parentId 组装） */
export function listMenuApi(): Promise<MenuVo[]> {
  return request<MenuVo[]>({ url: '/system/menus/list', method: 'get' });
}

/** 菜单详情 */
export function getMenuApi(menuId: number): Promise<MenuVo> {
  return request<MenuVo>({ url: `/system/menus/${menuId}`, method: 'get' });
}

/** 新增菜单 */
export function createMenuApi(data: MenuPayload): Promise<number> {
  return request<number>({ url: '/system/menus', method: 'post', data });
}

/** 修改菜单 */
export function updateMenuApi(data: MenuPayload): Promise<void> {
  return request<void>({ url: '/system/menus', method: 'put', data });
}

/** 删除菜单（有子菜单或已分配角色时后端拒绝） */
export function deleteMenuApi(menuId: number): Promise<void> {
  return request<void>({ url: `/system/menus/${menuId}`, method: 'delete' });
}
