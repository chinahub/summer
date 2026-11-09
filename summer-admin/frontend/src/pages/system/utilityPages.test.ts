import { readFileSync } from 'node:fs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FRONTEND_STATIC_PAGES, pageRegistry, resolvePageKey } from '@/pages/pageRegistry';

// 以桩替换统一请求层，捕获每次调用的 method/url/data/params，
// 证明前端发出的端点路径与字段名与后端 Controller/DTO 完全一致
const requestMock = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('@/api/request', () => ({
  service: {},
  request: requestMock.request,
}));

import { clearDictCache, fetchDictData, useDict } from '@/hooks/useDict';
import {
  createDictDataApi,
  createDictTypeApi,
  deleteDictDataApi,
  deleteDictTypeApi,
  listDictDataApi,
  listDictDataByTypeApi,
  listDictTypeApi,
  updateDictDataApi,
  updateDictTypeApi,
} from '@/api/system/dict';
import {
  createConfigApi,
  deleteConfigApi,
  listConfigApi,
  updateConfigApi,
} from '@/api/system/config';
import {
  createNoticeApi,
  deleteNoticeApi,
  listNoticeApi,
  listPublishedNoticeApi,
  updateNoticeApi,
} from '@/api/system/notice';
import {
  clearLoginLogsApi,
  clearOperLogsApi,
  deleteLoginLogsApi,
  deleteOperLogsApi,
  getLoginLogApi,
  getOperLogApi,
  listLoginLogsApi,
  listOperLogsApi,
} from '@/api/system/log';

describe('页面注册表：字典/参数/公告/日志六个组件键', () => {
  it('六个后端 component 键均映射到已注册页面', () => {
    const keys = Object.keys(pageRegistry);
    for (const key of [
      'system/dict/index',
      'system/config/index',
      'system/notice/index',
      'monitor/operlog/index',
      'monitor/logininfor/index',
    ]) {
      expect(keys).toContain(key);
      expect(resolvePageKey(key)).toBe(key);
    }
  });

  it('字典数据主从页注册为前端内置静态页', () => {
    expect(pageRegistry['system/dict/data/index']).toBeTypeOf('function');
    expect(FRONTEND_STATIC_PAGES['/system/dict/data/index']).toEqual({
      componentKey: 'system/dict/data/index',
      title: '字典数据',
    });
  });
});

describe('useDict 字典缓存：按类型缓存、保持顺序、失败可重试', () => {
  beforeEach(() => {
    clearDictCache();
    requestMock.request.mockReset();
  });

  it('同类型并发/重复读取只请求一次，不同类型分别请求', async () => {
    requestMock.request.mockResolvedValue([
      { dictCode: 1, dictLabel: '正常', dictValue: '0', dictSort: 1 },
    ]);
    const [first, second] = await Promise.all([
      fetchDictData('sys_normal_disable'),
      fetchDictData('sys_normal_disable'),
    ]);
    expect(first).toBe(second);
    await fetchDictData('sys_normal_disable');
    expect(requestMock.request).toHaveBeenCalledTimes(1);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/data/type/sys_normal_disable',
      method: 'get',
    });

    await fetchDictData('sys_user_sex');
    expect(requestMock.request).toHaveBeenCalledTimes(2);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/data/type/sys_user_sex',
      method: 'get',
    });
  });

  it('保持后端返回的启用项顺序，前端不做重排', async () => {
    // 后端按 dict_sort 升序返回；此处故意给乱序，断言 Hook 原样透传
    const ordered = [
      { dictCode: 2, dictLabel: '第二', dictValue: '2', dictSort: 2 },
      { dictCode: 1, dictLabel: '第一', dictValue: '1', dictSort: 1 },
    ];
    requestMock.request.mockResolvedValue(ordered);
    const items = await fetchDictData('sys_order_probe');
    expect(items.map((item) => item.dictLabel)).toEqual(['第二', '第一']);
  });

  it('请求失败不缓存，下一次调用重试并成功', async () => {
    requestMock.request.mockRejectedValueOnce(new Error('网络异常，请稍后重试'));
    await expect(fetchDictData('sys_retry')).rejects.toThrow('网络异常，请稍后重试');
    // 失败结果不得进入缓存：再次调用必须重新发请求
    requestMock.request.mockResolvedValueOnce([{ dictCode: 9, dictLabel: '重试成功', dictValue: '9' }]);
    const items = await fetchDictData('sys_retry');
    expect(items).toHaveLength(1);
    expect(requestMock.request).toHaveBeenCalledTimes(2);
  });

  it('clearDictCache 可按类型或全量失效缓存', async () => {
    requestMock.request.mockResolvedValue([]);
    await fetchDictData('sys_a');
    clearDictCache('sys_a');
    await fetchDictData('sys_a');
    expect(requestMock.request).toHaveBeenCalledTimes(2);
    await fetchDictData('sys_b');
    clearDictCache();
    await fetchDictData('sys_a');
    await fetchDictData('sys_b');
    expect(requestMock.request).toHaveBeenCalledTimes(5);
  });

  it('useDict 导出为函数（Hook 语义在页面集成中验证）', () => {
    expect(typeof useDict).toBe('function');
  });
});

describe('API 端点：字典/参数/公告/日志与后端 Controller 一致', () => {
  beforeEach(() => {
    requestMock.request.mockReset();
    requestMock.request.mockResolvedValue({});
  });

  it('字典类型与字典数据端点（维护走 system:dict:*，按类型读取仅认证）', async () => {
    await listDictTypeApi({ pageNum: 1, pageSize: 10, dictName: '状态', dictType: 'sys_', status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/types/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, dictName: '状态', dictType: 'sys_', status: '0' },
    });

    await createDictTypeApi({ dictName: '颜色', dictType: 'sys_color', status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/types',
      method: 'post',
      data: { dictName: '颜色', dictType: 'sys_color', status: '0' },
    });

    await updateDictTypeApi({ dictId: 5, dictName: '颜色', dictType: 'sys_color', status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/types',
      method: 'put',
      data: { dictId: 5, dictName: '颜色', dictType: 'sys_color', status: '0' },
    });

    await deleteDictTypeApi(5);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/types/5',
      method: 'delete',
    });

    await listDictDataApi({ pageNum: 1, pageSize: 10, dictType: 'sys_color', dictLabel: '红', status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/data/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, dictType: 'sys_color', dictLabel: '红', status: '0' },
    });

    await listDictDataByTypeApi('sys_color');
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/data/type/sys_color',
      method: 'get',
    });

    await createDictDataApi({
      dictType: 'sys_color',
      dictLabel: '红',
      dictValue: '1',
      dictSort: 1,
      isDefault: 'Y',
      status: '0',
    });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/data',
      method: 'post',
      data: {
        dictType: 'sys_color',
        dictLabel: '红',
        dictValue: '1',
        dictSort: 1,
        isDefault: 'Y',
        status: '0',
      },
    });

    await updateDictDataApi({
      dictCode: 3,
      dictType: 'sys_color',
      dictLabel: '深红',
      dictValue: '1',
      dictSort: 2,
    });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/data',
      method: 'put',
      data: { dictCode: 3, dictType: 'sys_color', dictLabel: '深红', dictValue: '1', dictSort: 2 },
    });

    await deleteDictDataApi(3);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/dict/data/3',
      method: 'delete',
    });
  });

  it('参数管理端点', async () => {
    await listConfigApi({ pageNum: 1, pageSize: 10, configName: '登录', configKey: 'sys.', configType: 'Y', status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/configs/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, configName: '登录', configKey: 'sys.', configType: 'Y', status: '0' },
    });

    await createConfigApi({ configName: '验证码开关', configKey: 'sys.captcha', configValue: 'true', configType: 'Y', status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/configs',
      method: 'post',
      data: { configName: '验证码开关', configKey: 'sys.captcha', configValue: 'true', configType: 'Y', status: '0' },
    });

    await updateConfigApi({ configId: 2, configName: '验证码开关', configKey: 'sys.captcha', configValue: 'false', configType: 'Y', status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/configs',
      method: 'put',
      data: { configId: 2, configName: '验证码开关', configKey: 'sys.captcha', configValue: 'false', configType: 'Y', status: '0' },
    });

    await deleteConfigApi(2);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/configs/2',
      method: 'delete',
    });
  });

  it('公告管理端点（管理端可见草稿；published 仅认证）', async () => {
    await listNoticeApi({ pageNum: 1, pageSize: 10, noticeTitle: '升级', noticeType: '1', status: '2' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/notices/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, noticeTitle: '升级', noticeType: '1', status: '2' },
    });

    await listPublishedNoticeApi({ pageNum: 1, pageSize: 10 });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/notices/published/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10 },
    });

    await createNoticeApi({ noticeTitle: '停机通知', noticeType: '1', noticeContent: '内容', status: '2' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/notices',
      method: 'post',
      data: { noticeTitle: '停机通知', noticeType: '1', noticeContent: '内容', status: '2' },
    });

    await updateNoticeApi({ noticeId: 3, noticeTitle: '停机通知', noticeType: '1', noticeContent: '内容', status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/notices',
      method: 'put',
      data: { noticeId: 3, noticeTitle: '停机通知', noticeType: '1', noticeContent: '内容', status: '0' },
    });

    await deleteNoticeApi(3);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/notices/3',
      method: 'delete',
    });
  });

  it('日志端点：条件分页/详情/批量删除/清空（monitor:log:*）', async () => {
    await listOperLogsApi({ pageNum: 1, pageSize: 10, title: '用户', operName: 'admin', businessType: 1, status: 0 });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/logs/operations/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, title: '用户', operName: 'admin', businessType: 1, status: 0 },
    });

    await getOperLogApi(7);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/logs/operations/7',
      method: 'get',
    });

    await deleteOperLogsApi('1,2,3');
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/logs/operations/1,2,3',
      method: 'delete',
    });

    await clearOperLogsApi();
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/logs/operations/clean',
      method: 'delete',
    });

    await listLoginLogsApi({ pageNum: 1, pageSize: 10, userName: 'admin', ipaddr: '127.0.0.1', status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/logs/logins/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, userName: 'admin', ipaddr: '127.0.0.1', status: '0' },
    });

    await getLoginLogApi(9);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/logs/logins/9',
      method: 'get',
    });

    await deleteLoginLogsApi('4,5');
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/logs/logins/4,5',
      method: 'delete',
    });

    await clearLoginLogsApi();
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/logs/logins/clean',
      method: 'delete',
    });
  });
});

describe('日志清空：非 GET 请求且页面必须二次确认', () => {
  beforeEach(() => {
    requestMock.request.mockReset();
    requestMock.request.mockResolvedValue({});
  });

  it('清空接口一律使用 DELETE，绝不使用 GET', async () => {
    await clearOperLogsApi();
    const oper = requestMock.request.mock.lastCall![0] as { method: string };
    expect(oper.method).not.toBe('get');
    await clearLoginLogsApi();
    const login = requestMock.request.mock.lastCall![0] as { method: string };
    expect(login.method).not.toBe('get');
  });

  it('两类日志页的清空按钮均带 Popconfirm 二次确认', () => {
    const operationSource = readFileSync('src/pages/monitor/log/operation.tsx', 'utf-8');
    expect(operationSource).toContain('Popconfirm');
    expect(operationSource).toContain('clearOperLogsApi');
    const loginSource = readFileSync('src/pages/monitor/log/login.tsx', 'utf-8');
    expect(loginSource).toContain('Popconfirm');
    expect(loginSource).toContain('clearLoginLogsApi');
  });
});
