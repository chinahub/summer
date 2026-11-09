import { request } from '@/api/request';

/** 部门实体：与后端 SysDept 字段一一对应 */
export interface DeptVo {
  deptId: number;
  parentId: number;
  ancestors?: string;
  deptName: string;
  orderNum?: number;
  leader?: string;
  phone?: string;
  email?: string;
  status?: string;
  createTime?: string;
  /** 树表格子行（前端组装，非后端字段） */
  children?: DeptVo[];
}

/** 部门树节点：antd Tree/TreeSelect 通用结构 */
export interface DeptTreeNode {
  key: number;
  value: number;
  title: string;
  children?: DeptTreeNode[];
}

/** 部门保存载荷：新增/修改共用（修改时携带 deptId 与 ancestors） */
export type DeptPayload = Partial<DeptVo> & { deptName: string; parentId: number };

/**
 * 将扁平部门列表组装为树：parentId=0 为根，按 orderNum 排序；
 * 孤儿节点（父级缺失）挂到根，保证任何脏数据下树可渲染。
 */
export function buildDeptTree(depts: DeptVo[]): DeptTreeNode[] {
  const nodes = new Map<number, DeptTreeNode>();
  for (const dept of depts) {
    nodes.set(dept.deptId, { key: dept.deptId, value: dept.deptId, title: dept.deptName });
  }
  const roots: DeptTreeNode[] = [];
  for (const dept of depts) {
    const node = nodes.get(dept.deptId)!;
    const parent = dept.parentId !== 0 ? nodes.get(dept.parentId) : undefined;
    if (parent) {
      (parent.children ??= []).push(node);
    } else {
      roots.push(node);
    }
  }
  const sortByOrder = (list: DeptTreeNode[]) => {
    list.sort((a, b) => {
      const orderA = depts.find((d) => d.deptId === a.key)?.orderNum ?? 0;
      const orderB = depts.find((d) => d.deptId === b.key)?.orderNum ?? 0;
      return orderA - orderB;
    });
    list.forEach((n) => n.children && sortByOrder(n.children));
  };
  sortByOrder(roots);
  return roots;
}

/** 收集部门自身及全部后代的 ID 集合：用于部门树筛选时包含子部门 */
export function collectDeptAndChildren(depts: DeptVo[], deptId: number): Set<number> {
  const childrenOf = new Map<number, number[]>();
  for (const dept of depts) {
    const siblings = childrenOf.get(dept.parentId);
    if (siblings) {
      siblings.push(dept.deptId);
    } else {
      childrenOf.set(dept.parentId, [dept.deptId]);
    }
  }
  const result = new Set<number>();
  const stack = [deptId];
  while (stack.length > 0) {
    const current = stack.pop()!;
    if (result.has(current)) {
      continue;
    }
    result.add(current);
    for (const child of childrenOf.get(current) ?? []) {
      stack.push(child);
    }
  }
  return result;
}

/** 全部部门列表（部门树由前端基于 parentId 组装） */
export function listDeptApi(): Promise<DeptVo[]> {
  return request<DeptVo[]>({ url: '/system/depts/list', method: 'get' });
}

/** 当前数据范围可见的部门列表（服务端按当前用户解析） */
export function visibleDeptApi(): Promise<DeptVo[]> {
  return request<DeptVo[]>({ url: '/system/depts/visible', method: 'get' });
}

/** 部门详情 */
export function getDeptApi(deptId: number): Promise<DeptVo> {
  return request<DeptVo>({ url: `/system/depts/${deptId}`, method: 'get' });
}

/** 新增部门 */
export function createDeptApi(data: DeptPayload): Promise<number> {
  return request<number>({ url: '/system/depts', method: 'post', data });
}

/** 修改部门（含移动，后端同步维护后代 ancestors） */
export function updateDeptApi(data: DeptPayload): Promise<void> {
  return request<void>({ url: '/system/depts', method: 'put', data });
}

/** 删除部门（有下级部门或归属用户时后端拒绝） */
export function deleteDeptApi(deptId: number): Promise<void> {
  return request<void>({ url: `/system/depts/${deptId}`, method: 'delete' });
}
