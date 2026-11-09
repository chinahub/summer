import { beforeEach, describe, expect, it, vi } from 'vitest';
import { pageRegistry, resolvePageKey } from '@/pages/pageRegistry';

// 以桩替换统一请求层，捕获每次调用的 method/url/data/params，
// 证明前端发出的端点路径与字段名与后端 Controller/DTO 完全一致
const requestMock = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('@/api/request', () => ({
  service: {},
  request: requestMock.request,
}));

import {
  buildCreateUserPayload,
  buildUpdateUserPayload,
  createUserApi,
  deleteUserApi,
  listUserApi,
  resetUserPasswordApi,
  updateUserApi,
  updateUserStatusApi,
  type UserFormValues,
} from '@/api/system/user';
import {
  assignRoleDataScopeApi,
  assignRoleMenusApi,
  buildDataScopeBody,
  buildMenuAssignBody,
  createRoleApi,
  dataScopeCodeFromEnum,
  dataScopeEnumFromCode,
  DATA_SCOPE_ENUMS,
  getRoleDeptIdsApi,
  getRoleMenuIdsApi,
  listRoleApi,
  type DataScopeTypeName,
} from '@/api/system/role';
import { createMenuApi, listMenuApi, updateMenuApi } from '@/api/system/menu';
import { createDeptApi, listDeptApi, updateDeptApi, visibleDeptApi } from '@/api/system/dept';
import { createPostApi, listPostApi, updatePostApi } from '@/api/system/post';

describe('页面注册表：五个后端 component 键', () => {
  it('用户/角色/菜单/部门/页面均映射到已注册页面', () => {
    const keys = Object.keys(pageRegistry);
    for (const key of [
      'system/user/index',
      'system/role/index',
      'system/menu/index',
      'system/dept/index',
      'system/post/index',
    ]) {
      expect(keys).toContain(key);
      expect(resolvePageKey(key)).toBe(key);
    }
  });
});

describe('用户表单转换：与 CreateUserRequest/UpdateUserRequest 字段一致', () => {
  const formValues: UserFormValues = {
    userName: 'zhangsan',
    nickName: '张三',
    password: 'Secret123',
    deptId: 101,
    phonenumber: '13800000000',
    email: 'zhangsan@example.com',
    sex: '0',
    status: '0',
    roleIds: [2, 3],
    postIds: [4],
  };

  it('新增载荷字段名与后端 DTO 完全一致', () => {
    const payload = buildCreateUserPayload(formValues);
    expect(payload).toEqual({
      userName: 'zhangsan',
      nickName: '张三',
      password: 'Secret123',
      deptId: 101,
      email: 'zhangsan@example.com',
      phonenumber: '13800000000',
      sex: '0',
      status: '0',
      roleIds: [2, 3],
      postIds: [4],
    });
  });

  it('修改载荷必带 userId 且 roleIds/postIds 整体替换', () => {
    const payload = buildUpdateUserPayload({ userId: 7, ...formValues });
    expect(payload.userId).toBe(7);
    expect(payload.roleIds).toEqual([2, 3]);
    expect(payload.postIds).toEqual([4]);
    // 更新载荷不含密码字段
    expect('password' in payload).toBe(false);
  });

  it('空角色/岗位转换为 undefined（后端语义：不携带关系字段）', () => {
    const payload = buildCreateUserPayload({ ...formValues, roleIds: [], postIds: [] });
    expect(payload.roleIds).toBeUndefined();
    expect(payload.postIds).toBeUndefined();
  });
});

describe('角色授权转换：菜单/数据范围与后端请求体一致', () => {
  it('菜单授权体为 {menuIds}', () => {
    expect(buildMenuAssignBody([1, 2, 3])).toEqual({ menuIds: [1, 2, 3] });
    expect(buildMenuAssignBody([])).toEqual({ menuIds: [] });
  });

  it('五种数据范围枚举名与后端 DataScopeType 完全一致', () => {
    expect([...DATA_SCOPE_ENUMS]).toEqual([
      'ALL',
      'CUSTOM',
      'DEPT',
      'DEPT_AND_CHILDREN',
      'SELF',
    ]);
  });

  it('存储编码与枚举名双向映射覆盖全部五种', () => {
    const mapping: Record<string, DataScopeTypeName> = {
      '1': 'ALL',
      '2': 'CUSTOM',
      '3': 'DEPT',
      '4': 'DEPT_AND_CHILDREN',
      '5': 'SELF',
    };
    for (const [code, name] of Object.entries(mapping)) {
      expect(dataScopeEnumFromCode(code)).toBe(name);
      expect(dataScopeCodeFromEnum(name)).toBe(code);
    }
    expect(dataScopeEnumFromCode('9')).toBeNull();
    expect(dataScopeEnumFromCode('')).toBeNull();
  });

  it('数据范围体为 {scopeType, deptIds}，CUSTOM 必须携带部门', () => {
    expect(buildDataScopeBody('DEPT', [])).toEqual({ scopeType: 'DEPT', deptIds: [] });
    expect(buildDataScopeBody('CUSTOM', [10, 11])).toEqual({
      scopeType: 'CUSTOM',
      deptIds: [10, 11],
    });
  });

  it('CUSTOM 无部门时前端阻止提交（直接抛错，不产生请求体）', () => {
    expect(() => buildDataScopeBody('CUSTOM', [])).toThrow('请选择自定义数据权限的部门');
    expect(() => buildDataScopeBody('CUSTOM', undefined)).toThrow('请选择自定义数据权限的部门');
  });
});

describe('API 端点：method/url/载荷与后端 Controller 一致', () => {
  beforeEach(() => {
    requestMock.request.mockReset();
    requestMock.request.mockResolvedValue({});
  });

  it('用户管理端点', async () => {
    await listUserApi({ pageNum: 1, pageSize: 10, userName: 'z', status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/users/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, userName: 'z', status: '0' },
    });

    // 部门树筛选：deptId 原样传给服务端（服务端按子树过滤，AND 数据范围）
    await listUserApi({ pageNum: 1, pageSize: 10, deptId: 2 });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/users/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, deptId: 2 },
    });

    // 空字符串条件不发送，避免后端 like '%%'
    await listUserApi({ pageNum: 1, pageSize: 10, userName: '' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/users/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10 },
    });

    await createUserApi(buildCreateUserPayload({ userName: 'a', nickName: 'A', password: 'x123456', deptId: 1 }));
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/users',
      method: 'post',
      data: { userName: 'a', nickName: 'A', password: 'x123456', deptId: 1 },
    });

    await updateUserApi(buildUpdateUserPayload({ userId: 1, userName: 'a' }));
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/users',
      method: 'put',
      data: { userId: 1, userName: 'a' },
    });

    await updateUserStatusApi(1, '1');
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/users/1/status',
      method: 'put',
      data: { status: '1' },
    });

    await resetUserPasswordApi(1, 'NewPass123');
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/users/1/password',
      method: 'put',
      data: { password: 'NewPass123' },
    });

    await deleteUserApi(1);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/users/1',
      method: 'delete',
    });
  });

  it('角色管理端点（含菜单授权与数据范围授权）', async () => {
    await listRoleApi({ pageNum: 1, pageSize: 10, roleName: '管理' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/roles/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, roleName: '管理' },
    });

    await createRoleApi({ roleName: '测试角色', roleKey: 'test', roleSort: 0, status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/roles',
      method: 'post',
      data: { roleName: '测试角色', roleKey: 'test', roleSort: 0, status: '0' },
    });

    await assignRoleMenusApi(2, [100, 101]);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/roles/2/menus',
      method: 'put',
      data: { menuIds: [100, 101] },
    });

    await assignRoleDataScopeApi(2, buildDataScopeBody('CUSTOM', [5]));
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/roles/2/data-scope',
      method: 'put',
      data: { scopeType: 'CUSTOM', deptIds: [5] },
    });

    // 编辑弹窗回填：角色已授权菜单与 CUSTOM 已选部门读回端点
    await getRoleMenuIdsApi(2);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/roles/2/menus',
      method: 'get',
    });
    await getRoleDeptIdsApi(2);
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/roles/2/depts',
      method: 'get',
    });
  });

  it('菜单管理端点', async () => {
    await listMenuApi();
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/menus/list',
      method: 'get',
    });

    await createMenuApi({ menuName: '新菜单', parentId: 0, menuType: 'C', orderNum: 1 });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/menus',
      method: 'post',
      data: { menuName: '新菜单', parentId: 0, menuType: 'C', orderNum: 1 },
    });

    await updateMenuApi({ menuId: 5, menuName: '改菜单', parentId: 0, menuType: 'C', orderNum: 2 });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/menus',
      method: 'put',
      data: { menuId: 5, menuName: '改菜单', parentId: 0, menuType: 'C', orderNum: 2 },
    });
  });

  it('部门管理端点（含数据范围可见部门）', async () => {
    await listDeptApi();
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/depts/list',
      method: 'get',
    });

    await visibleDeptApi();
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/depts/visible',
      method: 'get',
    });

    await createDeptApi({ deptName: '研发部', parentId: 0, orderNum: 1 });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/depts',
      method: 'post',
      data: { deptName: '研发部', parentId: 0, orderNum: 1 },
    });

    await updateDeptApi({ deptId: 3, deptName: '研发部', parentId: 0, orderNum: 1 });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/depts',
      method: 'put',
      data: { deptId: 3, deptName: '研发部', parentId: 0, orderNum: 1 },
    });
  });

  it('岗位管理端点', async () => {
    await listPostApi({ pageNum: 1, pageSize: 10 });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/posts/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10 },
    });

    await createPostApi({ postName: '架构师', postCode: 'arch', postSort: 0, status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/posts',
      method: 'post',
      data: { postName: '架构师', postCode: 'arch', postSort: 0, status: '0' },
    });

    await updatePostApi({ postId: 1, postName: '架构师', postCode: 'arch', postSort: 0, status: '0' });
    expect(requestMock.request).toHaveBeenLastCalledWith({
      url: '/system/posts',
      method: 'put',
      data: { postId: 1, postName: '架构师', postCode: 'arch', postSort: 0, status: '0' },
    });
  });
});
