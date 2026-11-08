import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { clearToken, getToken, setToken } from '@/utils/auth';
import { hasPermission } from '@/utils/permission';
import { filterAuthorizedMenus } from '@/utils/menu';
import { request, service } from '@/api/request';

describe('token 存取', () => {
  beforeEach(() => {
    clearToken();
  });

  it('默认无 token', () => {
    expect(getToken()).toBeNull();
  });

  it('setToken 后可读取，clearToken 后清空', () => {
    setToken('abc.def.ghi');
    expect(getToken()).toBe('abc.def.ghi');
    clearToken();
    expect(getToken()).toBeNull();
  });
});

describe('hasPermission 权限匹配', () => {
  it('超级管理员通配符 *:*:* 拥有全部权限', () => {
    expect(hasPermission('system:user:list', ['*:*:*'])).toBe(true);
    expect(hasPermission(['system:user:list', 'system:role:list'], ['*:*:*'])).toBe(true);
  });

  it('精确匹配单个权限', () => {
    expect(hasPermission('system:user:list', ['system:user:list', 'system:user:query'])).toBe(true);
    expect(hasPermission('system:role:list', ['system:user:list'])).toBe(false);
  });

  it('数组权限需全部满足', () => {
    const perms = ['system:user:list', 'system:user:query'];
    expect(hasPermission(['system:user:list', 'system:user:query'], perms)).toBe(true);
    expect(hasPermission(['system:user:list', 'system:role:list'], perms)).toBe(false);
  });

  it('无需权限的标识直接放行', () => {
    expect(hasPermission('', ['system:user:list'])).toBe(true);
    expect(hasPermission([], ['system:user:list'])).toBe(true);
  });
});

describe('filterAuthorizedMenus 菜单过滤', () => {
  const menus = [
    {
      path: '/system',
      meta: { title: '系统管理' },
      children: [
        {
          path: 'user',
          meta: { title: '用户管理', perms: 'system:user:list' },
          children: [
            { path: 'add', meta: { title: '新增', perms: 'system:user:add' } },
          ],
        },
        {
          path: 'role',
          meta: { title: '角色管理', perms: 'system:role:list' },
        },
      ],
    },
  ];

  it('无权限的子菜单被剔除，空目录一并移除', () => {
    const filtered = filterAuthorizedMenus(menus, ['system:user:list']);
    expect(filtered).toHaveLength(1);
    expect(filtered[0].path).toBe('/system');
    expect(filtered[0].children).toHaveLength(1);
    expect(filtered[0].children![0].path).toBe('user');
  });

  it('按钮级权限不进入路由树', () => {
    const filtered = filterAuthorizedMenus(menus, ['system:user:list']);
    expect(filtered[0].children![0].children ?? []).toHaveLength(0);
  });

  it('权限齐全时保留完整菜单', () => {
    const filtered = filterAuthorizedMenus(menus, ['*:*:*']);
    expect(filtered[0].children).toHaveLength(2);
  });
});

describe('request 响应契约', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    clearToken();
    mock = new MockAdapter(service);
  });

  afterEach(() => {
    mock.restore();
  });

  it('业务 code 非 200 时拒绝并携带后端 msg', async () => {
    mock.onGet('/auth/userinfo').reply(200, { code: 403, msg: '无权限', data: null });
    await expect(request({ url: '/auth/userinfo', method: 'get' })).rejects.toThrow('无权限');
  });

  it('业务 code 200 时解包 data', async () => {
    mock.onGet('/auth/userinfo').reply(200, {
      code: 200,
      msg: '操作成功',
      data: { userName: 'admin' },
    });
    const data = await request<{ userName: string }>({ url: '/auth/userinfo', method: 'get' });
    expect(data.userName).toBe('admin');
  });

  it('HTTP 401 时清空 token 并跳转登录页', async () => {
    setToken('expired-token');
    mock.onGet('/auth/userinfo').reply(401, { code: 401, msg: '认证失败' });
    await expect(request({ url: '/auth/userinfo', method: 'get' })).rejects.toBeTruthy();
    expect(getToken()).toBeNull();
    expect(window.location.hash).toBe('#/login');
  });

  it('请求时携带 Authorization: Bearer', async () => {
    setToken('valid-token');
    mock.onGet('/auth/userinfo').reply((config) => {
      expect(config.headers?.Authorization).toBe('Bearer valid-token');
      return [200, { code: 200, msg: '操作成功', data: { userName: 'admin' } }];
    });
    await request({ url: '/auth/userinfo', method: 'get' });
  });
});
