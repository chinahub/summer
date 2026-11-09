import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { App as AntdApp } from 'antd';
import { service } from '@/api/request';
import { usePermissionStore } from '@/stores/permissionStore';
import { useUserStore } from '@/stores/userStore';
import LoginPage from '@/pages/login';

(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true;

/** 轮询等待断言成立（异步请求需要多个事件循环 tick） */
async function waitFor(assertion: () => void, timeoutMs = 4000): Promise<void> {
  const start = Date.now();
  let lastError: unknown;
  while (Date.now() - start < timeoutMs) {
    try {
      assertion();
      return;
    } catch (error) {
      lastError = error;
    }
    await act(async () => {
      await new Promise((resolve) => { setTimeout(resolve, 25); });
    });
  }
  throw lastError;
}

/** 按 placeholder 填写受控输入（原生 setter + input 事件触发 React onChange） */
async function fillInput(placeholder: string, value: string): Promise<void> {
  const input = document.querySelector<HTMLInputElement>(`input[placeholder="${placeholder}"]`);
  if (!input) {
    throw new Error(`未找到输入框: ${placeholder}`);
  }
  await act(async () => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')!.set!;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await new Promise((resolve) => { setTimeout(resolve, 30); });
  });
}

/**
 * 登录部分失败状态清理回归：loginApi 已成功写入令牌，但 userinfo/routers
 * 加载失败（本例 userinfo 返回 500）时，登录页必须回滚本地令牌与
 * 用户/权限状态——否则用户卡在半登录态：令牌残留但无任何资料与路由。
 */
describe('LoginPage 部分失败状态清理回归', () => {
  let mock: MockAdapter;
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(async () => {
    mock = new MockAdapter(service);
    // 验证码关闭：登录表单无需验证码字段
    mock.onGet('/auth/captcha').reply(200, {
      code: 200,
      msg: '操作成功',
      data: { captchaEnabled: false, uuid: '', img: '' },
    });
    // 登录成功：令牌下发
    mock.onPost('/auth/login').reply(200, {
      code: 200,
      msg: '操作成功',
      data: { access_token: 'partial-token', token_type: 'Bearer', expires_in: 7200 },
    });
    // userinfo 失败（HTTP 500，非 401——401 会由请求层统一清理，这里验证登录页自身回滚）
    mock.onGet('/auth/userinfo').reply(500, { code: 500, msg: '资料加载失败' });
    mock.onGet('/auth/routers').reply(200, { code: 200, msg: '操作成功', data: [] });

    // 清空登录态，避免挂载时"已登录直接回首页"短路
    useUserStore.getState().reset();
    usePermissionStore.getState().reset();
    localStorage.clear();

    document.body.innerHTML = '';
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => {
      root.render(
        <AntdApp>
          <LoginPage />
        </AntdApp>,
      );
    });
  });

  afterEach(async () => {
    mock.restore();
    await act(async () => {
      root.unmount();
    });
    document.body.innerHTML = '';
  });

  it('userinfo 失败时清空令牌与用户/权限状态', async () => {
    await waitFor(() => {
      if (!document.body.textContent?.includes('summer-admin 后台管理系统')) {
        throw new Error('登录页未渲染');
      }
    });
    await fillInput('账号', 'admin');
    await fillInput('密码', 'admin123');

    const submit = Array.from(document.querySelectorAll<HTMLElement>('button'))
      .find((node) => node.textContent?.replace(/\s/g, '') === '登录');
    expect(submit).toBeDefined();
    await act(async () => {
      submit!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await new Promise((resolve) => { setTimeout(resolve, 50); });
    });

    // 登录失败反馈出现（后端 msg 透传）
    await waitFor(() => {
      if (!document.body.textContent?.includes('资料加载失败')) {
        throw new Error('未出现登录失败提示');
      }
    });
    // 核心断言：loginApi 写入的令牌与状态必须被回滚，绝不残留半登录态
    expect(useUserStore.getState().token).toBeNull();
    expect(useUserStore.getState().userInfo).toBeNull();
    expect(usePermissionStore.getState().routes).toHaveLength(0);
    expect(localStorage.getItem('Admin-Token')).toBeNull();
  });
});
