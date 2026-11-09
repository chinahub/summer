import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Card, Col, Popconfirm, Row, Space, Tag, Tree } from 'antd';
import { useRef, useState } from 'react';
import { collectDeptAndChildren, buildDeptTree, visibleDeptApi } from '@/api/system/dept';
import { listPostApi } from '@/api/system/post';
import { listRoleApi } from '@/api/system/role';
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
  type UserVo,
} from '@/api/system/user';
import ResetPasswordDialog from '@/components/system/ResetPasswordDialog';
import UserForm from '@/components/system/UserForm';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

/** 用户管理：左侧数据范围部门树筛选，右侧分页列表与角色/岗位分配、状态、重置密码 */
export default function UserPage() {
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const actionRef = useRef<ActionType>(undefined);
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);

  const [selectedDept, setSelectedDept] = useState<number>();
  const [editingUser, setEditingUser] = useState<UserVo | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [resetTarget, setResetTarget] = useState<UserVo | null>(null);

  // 左侧部门树：按当前用户数据范围可见（后端解析），失败时展示错误并允许重试
  const { data: depts = [], isError: deptError, refetch: refetchDept } = useQuery({
    queryKey: ['system-depts-visible'],
    queryFn: visibleDeptApi,
    retry: 1,
  });

  // 角色/岗位下拉选项：角色接口需 system:role:query 权限，无权限用户不请求
  const { data: roleData } = useQuery({
    queryKey: ['system-roles-options'],
    queryFn: () => listRoleApi({ pageNum: 1, pageSize: 100 }),
    enabled: can('system:role:query'),
    retry: 1,
  });
  const { data: postData } = useQuery({
    queryKey: ['system-posts-options'],
    queryFn: () => listPostApi({ pageNum: 1, pageSize: 100 }),
    retry: 1,
  });
  const roleOptions = (roleData?.rows ?? []).map((r) => ({ label: r.roleName, value: r.roleId }));
  const postOptions = (postData?.rows ?? []).map((p) => ({ label: p.postName, value: p.postId }));

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const saveMutation = useMutation({
    mutationFn: async (values: UserFormValues) => {
      if (values.userId !== undefined) {
        await updateUserApi(buildUpdateUserPayload({ ...values, userId: values.userId }));
      } else {
        await createUserApi(buildCreateUserPayload(values));
      }
    },
    onSuccess: (_data, values) => {
      message.success(values.userId ? '用户修改成功' : '用户新增成功');
      setFormOpen(false);
      setEditingUser(null);
      queryClient.invalidateQueries({ queryKey: ['system-users'] });
    },
    onError: (error) => onError(error, '用户保存失败'),
  });

  const statusMutation = useMutation({
    mutationFn: ({ userId, status }: { userId: number; status: string }) =>
      updateUserStatusApi(userId, status),
    onSuccess: () => {
      message.success('状态修改成功');
      queryClient.invalidateQueries({ queryKey: ['system-users'] });
    },
    onError: (error) => onError(error, '状态修改失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteUserApi,
    onSuccess: () => {
      message.success('用户删除成功');
      queryClient.invalidateQueries({ queryKey: ['system-users'] });
    },
    onError: (error) => onError(error, '用户删除失败'),
  });

  const resetMutation = useMutation({
    mutationFn: ({ userId, password }: { userId: number; password: string }) =>
      resetUserPasswordApi(userId, password),
    onSuccess: () => {
      message.success('密码重置成功');
      setResetTarget(null);
    },
    onError: (error) => onError(error, '密码重置失败'),
  });

  const columns: ProColumns<UserVo>[] = [
    { title: '编号', dataIndex: 'userId', width: 70, hideInSearch: true },
    { title: '登录名称', dataIndex: 'userName', width: 110 },
    { title: '用户昵称', dataIndex: 'nickName', width: 110, hideInSearch: true },
    { title: '部门', dataIndex: 'deptName', width: 110, hideInSearch: true },
    {
      title: '手机号码',
      dataIndex: 'phonenumber',
      width: 120,
      fieldProps: { maxLength: 20 },
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
    {
      title: '角色',
      dataIndex: 'roleNames',
      width: 160,
      hideInSearch: true,
      render: (_, row) =>
        (row.roleNames ?? []).map((name) => <Tag key={name}>{name}</Tag>),
    },
    {
      title: '岗位',
      dataIndex: 'postNames',
      width: 140,
      hideInSearch: true,
      render: (_, row) => (row.postNames ?? []).map((name) => <Tag key={name}>{name}</Tag>),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 160, hideInSearch: true },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, row) => (
        <Space size={4} wrap>
          {can('system:user:edit') && (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditingUser(row);
                setFormOpen(true);
              }}
            >
              修改
            </Button>
          )}
          {can('system:user:edit') && (
            <Popconfirm
              title={`确认${row.status === '0' ? '停用' : '启用'}该用户？`}
              onConfirm={() =>
                statusMutation.mutate({
                  userId: row.userId,
                  status: row.status === '0' ? '1' : '0',
                })
              }
            >
              <Button type="link" size="small" loading={statusMutation.isPending}>
                {row.status === '0' ? '停用' : '启用'}
              </Button>
            </Popconfirm>
          )}
          {can('system:user:resetPwd') && (
            <Button type="link" size="small" onClick={() => setResetTarget(row)}>
              重置密码
            </Button>
          )}
          {can('system:user:remove') && (
            <Popconfirm title={`确认删除用户「${row.userName}」？`} onConfirm={() => deleteMutation.mutate(row.userId)}>
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
    <Row gutter={16}>
      <Col xs={24} md={6} style={{ marginBottom: 16 }}>
        <Card
          title="部门列表"
          styles={{ body: { padding: 8 } }}
          style={{ minHeight: 400 }}
        >
          {deptError ? (
            <Button
              type="primary"
              onClick={() => refetchDept().catch(() => message.error('部门加载失败'))}
            >
              重新加载
            </Button>
          ) : (
            <Tree
              blockNode
              selectedKeys={selectedDept ? [selectedDept] : []}
              treeData={buildDeptTree(depts)}
              onSelect={(keys) => setSelectedDept(keys.length > 0 ? Number(keys[0]) : undefined)}
            />
          )}
        </Card>
      </Col>
      <Col xs={24} md={18}>
        <ProTable<UserVo>
          headerTitle="用户列表"
          rowKey="userId"
          actionRef={actionRef}
          columns={columns}
          search={{ labelWidth: 90 }}
          pagination={{ defaultPageSize: 10, showSizeChanger: true }}
          toolBarRender={() =>
            can('system:user:add')
              ? [
                  <Button
                    key="add"
                    type="primary"
                    onClick={() => {
                      setEditingUser(null);
                      setFormOpen(true);
                    }}
                  >
                    新增用户
                  </Button>,
                ]
              : []
          }
          request={async (params) => {
            const data = await listUserApi({
              pageNum: params.current ?? 1,
              pageSize: params.pageSize ?? 10,
              userName: params.userName,
              phonenumber: params.phonenumber,
              status: params.status,
            });
            // 后端列表不支持部门过滤参数：选中部门时按当前数据范围子树做前端筛选
            let rows = data.rows;
            if (selectedDept) {
              const scoped = collectDeptAndChildren(depts, selectedDept);
              rows = rows.filter((row) => row.deptId !== undefined && scoped.has(row.deptId));
            }
            return { data: rows, total: data.total, success: true };
          }}
        />
      </Col>
      <UserForm
        open={formOpen}
        initial={editingUser}
        depts={depts}
        roleOptions={roleOptions}
        postOptions={postOptions}
        submitting={saveMutation.isPending}
        onCancel={() => {
          setFormOpen(false);
          setEditingUser(null);
        }}
        onSubmit={(values) => saveMutation.mutate(values)}
      />
      <ResetPasswordDialog
        open={resetTarget !== null}
        userName={resetTarget?.userName}
        submitting={resetMutation.isPending}
        onCancel={() => setResetTarget(null)}
        onSubmit={(password) => {
          if (resetTarget) {
            resetMutation.mutate({ userId: resetTarget.userId, password });
          }
        }}
      />
    </Row>
  );
}
