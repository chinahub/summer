import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { TestBrowser, history } from '@umijs/max';
import { service } from '@/api/request';
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
    ],
  },
];

// 部门树选择器在 jsdom 中替换为直接赋值的桩（本测试聚焦列表刷新，不测树交互）
vi.mock('@/components/system/DeptTreeSelect', () => ({
  default: ({ value, onChange }: { value?: number; onChange?: (v: number) => void }) => (
    <button type="button" data-testid="dept-pick" onClick={() => onChange?.(2)}>
      部门{value ?? ''}
    </button>
  ),
}));

(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true;

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

/** 轮询等待文本消失 */
async function waitForTextGone(text: string, timeoutMs = 4000): Promise<void> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (!document.body.textContent?.includes(text)) {
      return;
    }
    await act(async () => {
      await new Promise((resolve) => { setTimeout(resolve, 25); });
    });
  }
  throw new Error(`等待超时，文本仍存在: ${text}`);
}

/** 点击包含指定文本的最深层节点 */
async function clickText(text: string): Promise<void> {
  const nodes = Array.from(document.querySelectorAll<HTMLElement>('button, a, span, td, li'));
  const hits = nodes.filter((node) => node.textContent?.trim() === text);
  if (hits.length === 0) {
    throw new Error(`未找到可点击文本: ${text}`);
  }
  await act(async () => {
    hits[hits.length - 1].dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await new Promise((resolve) => { setTimeout(resolve, 30); });
  });
}

/** 在弹窗内按 placeholder 填写受控输入（原生 setter + input 事件触发 React onChange） */
async function fillModalInput(placeholder: string, value: string): Promise<void> {
  const modal = document.querySelector('.ant-modal');
  const input = modal?.querySelector<HTMLInputElement>(`input[placeholder="${placeholder}"]`);
  if (!input) {
    throw new Error(`弹窗内未找到输入框: ${placeholder}`);
  }
  await act(async () => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')!.set!;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await new Promise((resolve) => { setTimeout(resolve, 30); });
  });
}

interface Row {
  userId: number;
  userName: string;
  nickName: string;
  deptId: number;
  deptName: string;
  status: string;
}

/**
 * 用户管理页 ProTable 刷新回归：复现浏览器发现——新增返回成功后列表不更新。
 * ProTable 数据自管理（request 回调），TanStack 失效无法刷新它；
 * 本测试经真实 Modal 提交与真实 axios 层，断言新行出现/旧行消失且列表被重新拉取。
 */
describe('UserPage ProTable 列表刷新回归', () => {
  let mock: MockAdapter;
  let container: HTMLDivElement;
  let root: Root;
  let rows: Row[];
  let listCalls: number;

  function seedMocks(): void {
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
        deptName: '总公司',
        roleNames: ['超级管理员'],
        postNames: ['总经理'],
        createTime: '2026-11-01 10:00:00',
      },
    });
    mock.onGet('/system/depts/visible').reply(200, {
      code: 200,
      msg: '操作成功',
      data: [
        { deptId: 1, parentId: 0, deptName: '总公司', orderNum: 0, status: '0' },
        { deptId: 2, parentId: 1, ancestors: '0,1', deptName: '研发部门', orderNum: 1, status: '0' },
      ],
    });
    mock.onGet('/system/roles/list').reply(200, { code: 200, msg: '查询成功', rows: [], total: 0 });
    mock.onGet('/system/posts/list').reply(200, { code: 200, msg: '查询成功', rows: [], total: 0 });
    // 有状态列表接口：每次请求返回当前行快照，只有 ProTable reload 才能看到变化
    mock.onGet('/system/users/list').reply(() => {
      listCalls += 1;
      return [200, { code: 200, msg: '查询成功', rows: [...rows], total: rows.length }];
    });
    mock.onPost('/system/users').reply((config) => {
      const body = JSON.parse(config.data as string) as { userName: string; nickName: string; deptId: number };
      rows.push({
        userId: rows.length + 1,
        userName: body.userName,
        nickName: body.nickName,
        deptId: body.deptId,
        deptName: '研发部门',
        status: '0',
      });
      return [200, { code: 200, msg: '操作成功', data: rows.length }];
    });
    mock.onDelete(/\/system\/users\/\d+/).reply((config) => {
      const id = Number((config.url ?? '').split('/').pop());
      const index = rows.findIndex((row) => row.userId === id);
      if (index >= 0) {
        rows.splice(index, 1);
      }
      return [200, { code: 200, msg: '操作成功' }];
    });
  }

  async function gotoUserPage(): Promise<void> {
    await clickText('系统管理');
    await waitForText('用户管理');
    await clickText('用户管理');
    // 等首屏行数据渲染（ProTable request 为异步）
    await waitForText('普通用户');
  }

  beforeEach(async () => {
    mock = new MockAdapter(service);
    rows = [
      { userId: 1, userName: 'admin', nickName: '管理员', deptId: 2, deptName: '研发部门', status: '0' },
      { userId: 2, userName: 'user', nickName: '普通用户', deptId: 2, deptName: '研发部门', status: '0' },
    ];
    listCalls = 0;
    seedMocks();
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
    mock.restore();
    useUserStore.getState().reset();
    usePermissionStore.getState().reset();
    useTagsViewStore.getState().delAll();
    history.push('/');
  });

  it('新增成功后列表立即出现新用户（首屏无重复请求）', async () => {
    await gotoUserPage();
    expect(document.body.textContent).toContain('普通用户');
    // 首屏只请求一次列表，避免重复请求回归
    expect(listCalls).toBe(1);

    await clickText('新增用户');
    await waitForText('用户昵称');
    await fillModalInput('请输入用户昵称', '浏览器用户');
    await fillModalInput('请输入登录名称', 'browser_user');
    await fillModalInput('请输入用户密码', 'secret66');
    await clickText('部门');
    await clickText('确 定');

    // 无 actionRef.reload 时：提交成功但 ProTable 不重新拉取，新行永不出现（回归即超时失败）
    await waitForText('浏览器用户');
    expect(document.body.textContent).toContain('browser_user');
    expect(listCalls).toBeGreaterThanOrEqual(2);
  });

  it('删除成功后列表立即移除该用户', async () => {
    await gotoUserPage();
    await waitForText('普通用户');

    const row = Array.from(document.querySelectorAll('tr')).find((tr) =>
      tr.textContent?.includes('普通用户'),
    );
    const deleteButton = Array.from(row?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.trim() === '删除',
    );
    expect(deleteButton, '目标行必须存在删除按钮').toBeTruthy();
    await act(async () => {
      deleteButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await new Promise((resolve) => { setTimeout(resolve, 30); });
    });

    await clickText('确 定');

    await waitForTextGone('普通用户');
    expect(listCalls).toBeGreaterThanOrEqual(2);
  });
});
