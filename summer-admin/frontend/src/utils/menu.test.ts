import { describe, expect, it } from 'vitest';
import type { RouterVo } from '@/api/types';
import {
  filterEmptyDirectories,
  findRouteByPath,
  flattenRoutes,
  isSafeExternalUrl,
  joinRoutePath,
} from '@/utils/menu';

describe('joinRoutePath 父子路径拼接', () => {
  it('根路径与子路径拼接为完整路径', () => {
    expect(joinRoutePath('/system', 'user')).toBe('/system/user');
    expect(joinRoutePath('/system', '/user')).toBe('/system/user');
    expect(joinRoutePath('system', 'user')).toBe('/system/user');
  });

  it('子路径为空时返回父路径', () => {
    expect(joinRoutePath('/system', '')).toBe('/system');
  });
});

describe('flattenRoutes 后端路由树扁平化', () => {
  const tree: RouterVo[] = [
    {
      name: 'System',
      path: '/system',
      component: 'Layout',
      redirect: 'noRedirect',
      children: [
        { name: 'User', path: 'user', component: 'system/user/index', meta: { title: '用户管理' } },
        { name: 'Role', path: 'role', component: 'system/role/index', meta: { title: '角色管理' } },
      ],
    },
    { name: 'Doc', path: '/doc', component: 'Layout', children: [] },
  ];

  it('生成完整路径并保留原始节点', () => {
    const flat = flattenRoutes(tree);
    expect(flat.map((item) => item.fullPath)).toEqual([
      '/system',
      '/system/user',
      '/system/role',
      '/doc',
    ]);
    expect(flat.find((item) => item.fullPath === '/system/user')?.route.component).toBe(
      'system/user/index',
    );
  });

  it('多级嵌套逐层拼接完整路径', () => {
    const nested: RouterVo[] = [
      {
        path: '/monitor',
        component: 'Layout',
        children: [
          {
            path: 'log',
            children: [{ path: 'operlog', component: 'monitor/operlog/index' }],
          },
        ],
      },
    ];
    const flat = flattenRoutes(nested);
    expect(flat.map((item) => item.fullPath)).toContain('/monitor/log/operlog');
  });
});

describe('isSafeExternalUrl 外部链接协议白名单', () => {
  it('允许 http/https/mailto/tel 协议', () => {
    expect(isSafeExternalUrl('https://gitee.com/JavaLionLi/plus-ui')).toBe(true);
    expect(isSafeExternalUrl('http://example.com')).toBe(true);
    expect(isSafeExternalUrl('mailto:a@b.cn')).toBe(true);
    expect(isSafeExternalUrl('tel:10086')).toBe(true);
  });

  it('拒绝不安全的协议', () => {
    expect(isSafeExternalUrl('javascript:alert(1)')).toBe(false);
    expect(isSafeExternalUrl('JavaScript:alert(1)')).toBe(false);
    expect(isSafeExternalUrl('data:text/html,<script>alert(1)</script>')).toBe(false);
    expect(isSafeExternalUrl('vbscript:msgbox(1)')).toBe(false);
    expect(isSafeExternalUrl('file:///etc/passwd')).toBe(false);
  });
});

describe('findRouteByPath 当前路由精确匹配', () => {
  const tree: RouterVo[] = [
    {
      path: '/system',
      component: 'Layout',
      children: [{ path: 'user', component: 'system/user/index', meta: { title: '用户管理' } }],
    },
    { path: '/profile', component: 'system/user/profile/index' },
  ];

  it('按完整路径精确命中当前路由', () => {
    const hit = findRouteByPath(tree, '/system/user');
    expect(hit?.route.component).toBe('system/user/index');
    expect(hit?.route.meta?.title).toBe('用户管理');
  });

  it('不存在前缀或部分匹配', () => {
    expect(findRouteByPath(tree, '/system/usr')).toBeUndefined();
    expect(findRouteByPath(tree, '/system/user/extra')).toBeUndefined();
    expect(findRouteByPath(tree, '/nope')).toBeUndefined();
  });
});

describe('filterEmptyDirectories 过滤空目录', () => {
  it('剔除无组件且无可见子级的目录', () => {
    const tree: RouterVo[] = [
      {
        path: '/system',
        children: [{ path: 'user', component: 'system/user/index' }],
      },
      { path: '/empty', redirect: 'noRedirect', children: [] },
      { path: '/full', component: 'system/user/profile/index' },
    ];
    const filtered = filterEmptyDirectories(tree);
    expect(filtered.map((item) => item.path)).toEqual(['/system', '/full']);
    expect(filtered[0].children).toHaveLength(1);
  });

  it('嵌套空目录被一并剔除', () => {
    const tree: RouterVo[] = [
      {
        path: '/a',
        children: [{ path: 'b', children: [] }],
      },
      { path: '/c', component: 'index' },
    ];
    const filtered = filterEmptyDirectories(tree);
    expect(filtered.map((item) => item.path)).toEqual(['/c']);
  });
});
