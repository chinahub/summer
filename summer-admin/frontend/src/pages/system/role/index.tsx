import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Form, Input, InputNumber, Modal, Popconfirm, Radio, Space, Tag } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { listMenuApi, type MenuVo } from '@/api/system/menu';
import {
  assignRoleDataScopeApi,
  assignRoleMenusApi,
  buildDataScopeBody,
  createRoleApi,
  DATA_SCOPE_OPTIONS,
  dataScopeEnumFromCode,
  deleteRoleApi,
  listRoleApi,
  updateRoleApi,
  type DataScopeTypeName,
  type RolePayload,
  type RoleVo,
} from '@/api/system/role';
import { visibleDeptApi } from '@/api/system/dept';
import RoleDataScope from '@/components/system/RoleDataScope';
import RoleMenuTree from '@/components/system/RoleMenuTree';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

interface RoleFormValues {
  roleName: string;
  roleKey: string;
  roleSort?: number;
  status?: string;
}

/** 角色管理：分页 CRUD、菜单授权（整树勾选替换）与五种数据范围授权 */
export default function RolePage() {
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const actionRef = useRef<ActionType>(undefined);
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);

  const [editingRole, setEditingRole] = useState<RoleVo | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [menuTarget, setMenuTarget] = useState<RoleVo | null>(null);
  const [scopeTarget, setScopeTarget] = useState<RoleVo | null>(null);
  const [form] = Form.useForm<RoleFormValues>();

  const { data: menus = [] } = useQuery<MenuVo[]>({
    queryKey: ['system-menus-all'],
    queryFn: listMenuApi,
    enabled: menuTarget !== null,
    retry: 1,
  });
  const { data: depts = [] } = useQuery({
    queryKey: ['system-depts-visible'],
    queryFn: visibleDeptApi,
    enabled: scopeTarget !== null,
    retry: 1,
  });

  // 打开弹窗时按角色回填表单
  useEffect(() => {
    if (!formOpen) {
      return;
    }
    if (editingRole) {
      form.setFieldsValue({
        roleName: editingRole.roleName,
        roleKey: editingRole.roleKey,
        roleSort: editingRole.roleSort,
        status: editingRole.status ?? '0',
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ roleSort: 0, status: '0' });
    }
  }, [formOpen, editingRole, form]);

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const saveMutation = useMutation({
    mutationFn: async (values: RolePayload) => {
      if (values.roleId !== undefined) {
        await updateRoleApi(values);
      } else {
        await createRoleApi(values);
      }
    },
    onSuccess: (_data, values) => {
      message.success(values.roleId ? '角色修改成功' : '角色新增成功');
      setFormOpen(false);
      setEditingRole(null);
      queryClient.invalidateQueries({ queryKey: ['system-roles'] });
    },
    onError: (error) => onError(error, '角色保存失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteRoleApi,
    onSuccess: () => {
      message.success('角色删除成功');
      queryClient.invalidateQueries({ queryKey: ['system-roles'] });
    },
    onError: (error) => onError(error, '角色删除失败'),
  });

  const menuMutation = useMutation({
    mutationFn: ({ roleId, menuIds }: { roleId: number; menuIds: number[] }) =>
      assignRoleMenusApi(roleId, menuIds),
    onSuccess: () => {
      message.success('菜单授权成功');
      setMenuTarget(null);
    },
    onError: (error) => onError(error, '菜单授权失败'),
  });

  const scopeMutation = useMutation({
    mutationFn: ({
      roleId,
      scopeType,
      deptIds,
    }: {
      roleId: number;
      scopeType: DataScopeTypeName;
      deptIds: number[];
    }) => assignRoleDataScopeApi(roleId, buildDataScopeBody(scopeType, deptIds)),
    onSuccess: () => {
      message.success('数据权限保存成功');
      setScopeTarget(null);
      queryClient.invalidateQueries({ queryKey: ['system-roles'] });
    },
    onError: (error) => onError(error, '数据权限保存失败'),
  });

  const columns: ProColumns<RoleVo>[] = [
    { title: '编号', dataIndex: 'roleId', width: 70, hideInSearch: true },
    { title: '角色名称', dataIndex: 'roleName', width: 140 },
    { title: '权限字符', dataIndex: 'roleKey', width: 140 },
    { title: '排序', dataIndex: 'roleSort', width: 70, hideInSearch: true },
    {
      title: '数据范围',
      dataIndex: 'dataScope',
      width: 150,
      hideInSearch: true,
      render: (_, row) => {
        const scope = dataScopeEnumFromCode(row.dataScope);
        const option = DATA_SCOPE_OPTIONS.find((o) => o.value === scope);
        return option ? <Tag>{option.label}</Tag> : <Tag>-</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      valueType: 'select',
      valueEnum: {
        '0': { text: '正常' },
        '1': { text: '停用' },
      },
      render: (_, row) =>
        row.status === '0' ? <Tag color="success">正常</Tag> : <Tag color="error">停用</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', width: 160, hideInSearch: true },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      render: (_, row) => (
        <Space size={4} wrap>
          {can('system:role:edit') && (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditingRole(row);
                setFormOpen(true);
              }}
            >
              修改
            </Button>
          )}
          {can('system:role:dataScope') && (
            <Button type="link" size="small" onClick={() => setScopeTarget(row)}>
              数据权限
            </Button>
          )}
          {can('system:role:edit') && (
            <Button type="link" size="small" onClick={() => setMenuTarget(row)}>
              分配菜单
            </Button>
          )}
          {can('system:role:remove') && (
            <Popconfirm title={`确认删除角色「${row.roleName}」？`} onConfirm={() => deleteMutation.mutate(row.roleId)}>
              <Button type="link" size="small" danger loading={deleteMutation.isPending}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<RoleVo>
        headerTitle="角色列表"
        rowKey="roleId"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 90 }}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        toolBarRender={() =>
          can('system:role:add')
            ? [
                <Button
                  key="add"
                  type="primary"
                  onClick={() => {
                    setEditingRole(null);
                    setFormOpen(true);
                  }}
                >
                  新增角色
                </Button>,
              ]
            : []
        }
        request={async (params) => {
          const data = await listRoleApi({
            pageNum: params.current ?? 1,
            pageSize: params.pageSize ?? 10,
            roleName: params.roleName,
            roleKey: params.roleKey,
            status: params.status,
          });
          return { data: data.rows, total: data.total, success: true };
        }}
      />
      <Modal
        title={editingRole ? '修改角色' : '新增角色'}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false);
          setEditingRole(null);
        }}
        confirmLoading={saveMutation.isPending}
        onOk={() => {
          form.validateFields().then((values) => {
            saveMutation.mutate({ ...editingRole, ...values });
          });
        }}
        afterClose={() => form.resetFields()}
        destroyOnHidden
      >
        <Form<RoleFormValues> form={form} layout="vertical">
          <Form.Item
            label="角色名称"
            name="roleName"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input maxLength={30} placeholder="请输入角色名称" />
          </Form.Item>
          <Form.Item
            label="权限字符"
            name="roleKey"
            rules={[{ required: true, message: '请输入权限字符' }]}
          >
            <Input maxLength={100} placeholder="请输入权限字符，如 common" />
          </Form.Item>
          <Form.Item label="显示排序" name="roleSort" rules={[{ required: true, message: '请输入显示排序' }]}>
            <InputNumber min={0} max={9999} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="状态" name="status">
            <Radio.Group>
              <Radio value="0">正常</Radio>
              <Radio value="1">停用</Radio>
            </Radio.Group>
          </Form.Item>
        </Form>
      </Modal>
      <RoleMenuTree
        open={menuTarget !== null}
        roleName={menuTarget?.roleName}
        menus={menus}
        submitting={menuMutation.isPending}
        onCancel={() => setMenuTarget(null)}
        onSubmit={(menuIds) => {
          if (menuTarget) {
            menuMutation.mutate({ roleId: menuTarget.roleId, menuIds });
          }
        }}
      />
      <RoleDataScope
        open={scopeTarget !== null}
        role={scopeTarget}
        depts={depts}
        submitting={scopeMutation.isPending}
        onCancel={() => setScopeTarget(null)}
        onSubmit={(scopeType, deptIds) => {
          if (scopeTarget) {
            scopeMutation.mutate({ roleId: scopeTarget.roleId, scopeType, deptIds });
          }
        }}
      />
    </>
  );
}
