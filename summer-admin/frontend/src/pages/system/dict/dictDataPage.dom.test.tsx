import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { TestBrowser, history } from '@umijs/max';
import { service } from '@/api/request';
import { usePermissionStore } from '@/stores/permissionStore';
import { useTagsViewStore } from '@/stores/tagsViewStore';
import { useUserStore } from '@/stores/userStore';

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

/** 两个字典类型各自的可辨识行数据 */
const ROWS_BY_TYPE: Record<string, Array<Record<string, unknown>>> = {
  sys_normal_disable: [
    { dictCode: 1, dictType: 'sys_normal_disable', dictLabel: '正常项', dictValue: '0', dictSort: 1 },
  ],
  sys_browser_test: [
    { dictCode: 9, dictType: 'sys_browser_test', dictLabel: '浏览器项', dictValue: '9', dictSort: 1 },
  ],
};

/**
 * 字典数据页 query 切换回归：复现浏览器发现——
 * 从 ?dictType=sys_normal_disable 导航到 ?dictType=sys_browser_test，
 * URL 已变但 ProTable 搜索表单/请求仍停留在旧类型，展示过期行。
 * 根因：KeepAlive 按 pathname 缓存页签，search 变化不重挂载，
 * ProTable 的 initialValue 只在挂载时生效一次。
 */
describe('DictDataPage query 切换回归', () => {
  let mock: MockAdapter;
  let container: HTMLDivElement;
  let root: Root;
  let requestedTypes: string[];

  beforeEach(async () => {
    mock = new MockAdapter(service);
    requestedTypes = [];
    mock.onGet('/dashboard/summary').reply(200, {
      code: 200,
      msg: '操作成功',
      data: { userCount: 2, roleCount: 2, deptCount: 4, noticeCount: 1 },
    });
    mock.onGet('/system/profile').reply(200, {
      code: 200,
      msg: '操作成功',
      data: { userId: 1, userName: 'admin', nickName: '管理员' },
    });
    // 按请求参数返回对应类型的行，并记录每次请求的 dictType
    mock.onGet('/system/dict/data/list').reply((config) => {
      const dictType = String((config.params as Record<string, unknown>)?.dictType ?? '');
      requestedTypes.push(dictType);
      return [200, { code: 200, msg: '查询成功', rows: ROWS_BY_TYPE[dictType] ?? [], total: 1 }];
    });
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
    usePermissionStore.setState({ routes: [] });
    useTagsViewStore.getState().delAll();

    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => {
      root.render(
        <TestBrowser
          location={{ pathname: '/system/dict/data/index', search: '?dictType=sys_normal_disable' }}
        />,
      );
    });
    await waitForText('正常项');
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

  it('query 切换后列表请求与渲染行均切换到新 dictType', async () => {
    expect(requestedTypes[0]).toBe('sys_normal_disable');
    expect(document.body.textContent).toContain('正常项');

    // 同页签内切换 query（KeepAlive 不重挂载页面组件）
    await act(async () => {
      history.push('/system/dict/data/index?dictType=sys_browser_test');
      await new Promise((resolve) => { setTimeout(resolve, 30); });
    });

    // 第二次请求必须携带新 dictType，且渲染新行、旧行消失
    await waitForText('浏览器项');
    expect(requestedTypes.length).toBeGreaterThanOrEqual(2);
    expect(requestedTypes[requestedTypes.length - 1]).toBe('sys_browser_test');
    await waitForTextGone('正常项');
  });

  it('query 切换后新增弹窗默认字典类型为当前 query 值', async () => {
    await act(async () => {
      history.push('/system/dict/data/index?dictType=sys_browser_test');
      await new Promise((resolve) => { setTimeout(resolve, 30); });
    });
    await waitForText('浏览器项');

    await clickText('新增字典数据');
    await waitForText('字典键值');
    // 弹窗内字典类型输入框必须回填当前 query 类型（首个表单项）
    const modal = document.querySelector('.ant-modal');
    const typeInput = modal?.querySelector<HTMLInputElement>('input');
    expect(typeInput?.value).toBe('sys_browser_test');
  });
});
