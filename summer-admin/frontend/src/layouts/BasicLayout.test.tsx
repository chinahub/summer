import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { TestBrowser, history } from '@umijs/max';
import { service } from '@/api/request';
import { useAppStore } from '@/stores/appStore';
import { usePermissionStore } from '@/stores/permissionStore';
import { useTagsViewStore } from '@/stores/tagsViewStore';
import { useUserStore } from '@/stores/userStore';
import type { RouterVo } from '@/api/types';

// 与后端 /auth/routers 真实下发一致的菜单树（根 Layout 路径带前导斜杠，子级相对路径）
const BACKEND_ROUTES: RouterVo[] = [
  {
    name: 'System',
    path: '/system',
    component: 'Layout',
    redirect: 'noRedirect',
    meta: { title: '系统管理' },
    children: [
      {
        name: 'User',
        path: 'user',
        component: 'system/user/index',
        meta: { title: '用户管理', perms: 'system:user:list' },
      },
      {
        name: 'Log',
        path: 'log',
        meta: { title: '日志管理' },
        children: [
          {
            name: 'Operlog',
            path: 'operlog',
            component: 'monitor/operlog/index',
            meta: { title: '操作日志', perms: 'monitor:log:list' },
          },
        ],
      },
    ],
  },
];

(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true;

/** 在 DOM 中查找包含指定文本的最深层节点（事件向上冒泡，点击叶子才能触发内层处理器） */
function findByText(text: string): HTMLElement {
  const nodes = Array.from(document.querySelectorAll<HTMLElement>('li, a, span, div'));
  const hits = nodes.filter((node) => node.textContent?.trim() === text);
  if (hits.length === 0) {
    throw new Error(`未找到文本节点: ${text}`);
  }
  return hits[hits.length - 1];
}

/** 轮询等待文本出现（异步路由与请求需要多个事件循环 tick） */
async function waitForText(text: string, timeoutMs = 4000): Promise<void> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (document.body.textContent?.includes(text)) {
      return;
    }
    await act(async () => {
      await new Promise((resolve) => { setTimeout(resolve, 25); });
    });
  }
  throw new Error(`等待超时，文本未出现: ${text}；当前内容: ${document.body.textContent?.slice(0, 200)}`);
}

/** 点击包含指定文本的节点 */
async function clickText(text: string): Promise<void> {
  const node = findByText(text);
  await act(async () => {
    node.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await new Promise((resolve) => { setTimeout(resolve, 30); });
  });
}

describe('BasicLayout 菜单导航回归', () => {
  let mock: MockAdapter;
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(async () => {
    mock = new MockAdapter(service);
    mock.onGet('/dashboard/summary').reply(200, {
      code: 200,
      msg: '操作成功',
      data: { userCount: 2, roleCount: 2, deptCount: 4, noticeCount: 1 },
    });
    mock.onGet('/system/profile').reply(200, {
      code: 200,
      msg: '操作成功',
      data: {
        userId: 1,
        userName: 'admin',
        nickName: '管理员',
        deptName: '研发部门',
        roleNames: ['超级管理员'],
        postNames: ['总经理'],
        createTime: '2026-11-01 10:00:00',
      },
    });
    // 用户管理页（Task 14 注册的真实页面）所需的列表接口
    mock.onGet('/system/depts/visible').reply(200, { code: 200, msg: '操作成功', data: [] });
    mock.onGet('/system/roles/list').reply(200, { code: 200, msg: '查询成功', rows: [], total: 0 });
    mock.onGet('/system/posts/list').reply(200, { code: 200, msg: '查询成功', rows: [], total: 0 });
    mock.onGet('/system/users/list').reply(200, { code: 200, msg: '查询成功', rows: [], total: 0 });
    // 操作日志页（Task 15 注册的真实页面）所需的列表接口
    mock.onGet('/system/logs/operations/list').reply(200, { code: 200, msg: '查询成功', rows: [], total: 0 });
    // 登录态与动态路由直接入 store（本测试聚焦布局导航，不测登录流）
    useUserStore.setState({
      token: 'test-token',
      userInfo: {
        userId: 1,
        userName: 'admin',
        nickName: '管理员',
        roles: ['admin'],
        permissions: ['*:*:*'],
      },
    });
    usePermissionStore.setState({ routes: BACKEND_ROUTES });
    useTagsViewStore.getState().delAll();

    document.body.innerHTML = '<div id="root"></div>';
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => {
      root.render(<TestBrowser location={{ pathname: '/' }} />);
    });
    await waitForText('用户数量');
  });

  afterEach(async () => {
    await act(async () => {
      root.unmount();
    });
    container.remove();
    document.getElementById('root')?.remove();
    mock.restore();
    useUserStore.getState().reset();
    usePermissionStore.getState().reset();
    useTagsViewStore.getState().delAll();
  });

  it('首页展示真实汇总计数', () => {
    expect(document.body.textContent).toContain('用户数量');
    expect(document.body.textContent).toContain('系统管理');
  });

  it('点击用户管理：路由跳转到 /system/user 且内容切换到目标视图', async () => {
    // 展开系统管理目录，使叶子菜单进入 DOM
    await clickText('系统管理');
    await waitForText('用户管理');
    const item = findByText('用户管理');
    // 菜单项不得渲染成外链 <a>（相对路径被误当外部链接会导致整页跳转）
    expect(item.closest('a')).toBeNull();
    await act(async () => {
      history.push('/');
      await new Promise((resolve) => { setTimeout(resolve, 30); });
    });
    await clickText('用户管理');
    // 路径必须是完整 hash 路由 /system/user
    expect(history.location.pathname).toBe('/system/user');
    // 内容切换到 Task 14 注册的真实用户管理页（ProTable 表头与新增按钮）
    await waitForText('用户列表');
    expect(document.body.textContent).toContain('新增用户');
    // 页签栏同步出现目标页签
    expect(document.body.textContent).toContain('首页');
  });

  it('点击嵌套菜单操作日志：跳转到 /system/log/operlog', async () => {
    // 展开两级目录：系统管理 → 日志管理
    await clickText('系统管理');
    await waitForText('日志管理');
    await clickText('日志管理');
    await waitForText('操作日志');
    await act(async () => {
      history.push('/');
      await new Promise((resolve) => { setTimeout(resolve, 30); });
    });
    await clickText('操作日志');
    expect(history.location.pathname).toBe('/system/log/operlog');
    // Task 15 注册的真实操作日志页（ProTable 表头）
    await waitForText('操作日志列表');
  });

  it('用户菜单进入个人中心：跳转到 /system/user/profile/index 且渲染资料', async () => {
    // 用户下拉为点击触发（trigger=['click']）
    await clickText('管理员');
    await clickText('个人中心');
    expect(history.location.pathname).toBe('/system/user/profile/index');
    await waitForText('个人信息');
    expect(document.body.textContent).toContain('基本资料');
  });

  describe('混合布局（mix）回归', () => {
    beforeEach(() => {
      useAppStore.setState({ layout: 'mix' });
    });

    afterEach(() => {
      useAppStore.setState({ layout: 'side' });
    });

    it('顶部渲染一级目录菜单且首页可达', async () => {
      const topMenu = document.querySelector('ul.ant-menu-horizontal');
      expect(topMenu).not.toBeNull();
      expect(topMenu?.textContent).toContain('首页');
      expect(topMenu?.textContent).toContain('系统管理');
      // 顶部一级菜单点击首页回到首页
      await act(async () => {
        history.push('/system/user');
        await new Promise((resolve) => { setTimeout(resolve, 30); });
      });
      await clickText('首页');
      expect(history.location.pathname).toBe('/');
    });

    it('侧栏子菜单键只拼一次父路径，绝不出现 /system/system/*', async () => {
      // 激活 system 目录，侧栏渲染其子菜单
      await act(async () => {
        history.push('/system/user');
        await new Promise((resolve) => { setTimeout(resolve, 30); });
      });
      const sideMenu = document.querySelector('ul.ant-menu-root.ant-menu-inline');
      expect(sideMenu).not.toBeNull();
      expect(sideMenu?.textContent).toContain('用户管理');
      const keys = Array.from(sideMenu?.querySelectorAll('li[data-menu-id]') ?? []).map(
        (li) => li.getAttribute('data-menu-id') ?? '',
      );
      // 子菜单键为 /system/user（父路径恰好拼接一次）
      expect(keys.some((key) => key.endsWith('/system/user'))).toBe(true);
      // 回归：旧实现把根节点再次传入导致 /system/system/user
      expect(keys.some((key) => key.includes('/system/system/'))).toBe(false);
    });

    it('侧栏点击子菜单按预期路径导航且内容切换', async () => {
      // 激活 system 目录，侧栏渲染子菜单
      await act(async () => {
        history.push('/system/user');
        await new Promise((resolve) => { setTimeout(resolve, 30); });
      });
      // 展开日志管理子目录后点击操作日志，验证 mix 侧栏导航路径正确
      await clickText('日志管理');
      await waitForText('操作日志');
      await clickText('操作日志');
      expect(history.location.pathname).toBe('/system/log/operlog');
      // 内容切换到 Task 15 注册的真实操作日志页
      await waitForText('操作日志列表');
    });
  });
});
