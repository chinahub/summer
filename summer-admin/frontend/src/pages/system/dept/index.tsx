import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Space,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  collectDeptAndChildren,
  createDeptApi,
  deleteDeptApi,
  listDeptApi,
  updateDeptApi,
  type DeptPayload,
  type DeptVo,
} from '@/api/system/dept';
import DeptTreeSelect from '@/components/system/DeptTreeSelect';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

/** 部门表单值：新增/修改共用，parentId 通过部门树选择 */
interface DeptFormValues {
  parentId: number;
  deptName: string;
  orderNum?: number;
  leader?: string;
  phone?: string;
  email?: string;
  status?: string;
}

/** 将扁平列表转为树表格行：基于 parentId 挂 children，孤儿节点挂根；复制行对象避免污染查询缓存 */
function attachRows(depts: DeptVo[]): DeptVo[] {
  const copies = new Map<number, DeptVo>();
  for (const dept of depts) {
    copies.set(dept.deptId, { ...dept });
  }
  const roots: DeptVo[] = [];
  for (const dept of depts) {
    const row = copies.get(dept.deptId)!;
    const parent = dept.parentId !== 0 ? copies.get(dept.parentId) : undefined;
    if (parent) {
      (parent.children ??= []).push(row);
    } else {
      roots.push(row);
    }
  }
  const sortTree = (list: DeptVo[]) => {
    list.sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0));
    list.forEach((d) => d.children && sortTree(d.children));
  };
  sortTree(roots);
  return roots;
}

/** 部门管理：树表格展示部门层级，支持新增、修改（含移动）与删除 */
export default function DeptPage() {
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);
  const [form] = Form.useForm<DeptFormValues>();

  const [editingDept, setEditingDept] = useState<DeptVo | null>(null);
  const [formOpen, setFormOpen] = useState(false);

  const { data: depts = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['system-depts-all'],
    queryFn: listDeptApi,
    retry: 1,
  });

  // 编辑时排除自身及后代，防止把部门挂到自己或后代下形成环
  const excludeIds = useMemo(
    () => (editingDept ? collectDeptAndChildren(depts, editingDept.deptId) : new Set<number>()),
    [depts, editingDept],
  );

  // 打开弹窗时按部门回填
  useEffect(() => {
    if (!formOpen) {
      return;
    }
    if (editingDept) {
      form.setFieldsValue({
        parentId: editingDept.parentId,
        deptName: editingDept.deptName,
        orderNum: editingDept.orderNum,
        leader: editingDept.leader,
        phone: editingDept.phone,
        email: editingDept.email,
        status: editingDept.status ?? '0',
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ parentId: 0, orderNum: 0, status: '0' });
    }
  }, [formOpen, editingDept, form]);

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const saveMutation = useMutation({
    mutationFn: async (values: DeptPayload) => {
      if (values.deptId !== undefined) {
        await updateDeptApi(values);
      } else {
        await createDeptApi(values);
      }
    },
    onSuccess: (_data, values) => {
      message.success(values.deptId ? '部门修改成功' : '部门新增成功');
      setFormOpen(false);
      setEditingDept(null);
      queryClient.invalidateQueries({ queryKey: ['system-depts-all'] });
    },
    onError: (error) => onError(error, '部门保存失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteDeptApi,
    onSuccess: () => {
      message.success('部门删除成功');
      queryClient.invalidateQueries({ queryKey: ['system-depts-all'] });
    },
    onError: (error) => onError(error, '部门删除失败'),
  });

  const columns: NonNullable<TableProps<DeptVo>['columns']> = [
    { title: '部门名称', dataIndex: 'deptName' },
    { title: '排序', dataIndex: 'orderNum', width: 70 },
    { title: '负责人', dataIndex: 'leader', width: 110, render: (v: string | undefined) => v || '-' },
    { title: '联系电话', dataIndex: 'phone', width: 130, render: (v: string | undefined) => v || '-' },
    { title: '邮箱', dataIndex: 'email', width: 180, render: (v: string | undefined) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (value: string | undefined) =>
        value === '1' ? <Tag color="error">停用</Tag> : <Tag color="success">正常</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', width: 160 },
    {
      title: '操作',
      width: 180,
      render: (_, row) => (
        <Space size={4} wrap>
          {can('system:dept:add') && (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditingDept(null);
                setFormOpen(true);
                setTimeout(() => form.setFieldsValue({ parentId: row.deptId }));
              }}
            >
              新增
            </Button>
          )}
          {can('system:dept:edit') && (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditingDept(row);
                setFormOpen(true);
              }}
            >
              修改
            </Button>
          )}
          {can('system:dept:remove') && (
            <Popconfirm title={`确认删除部门「${row.deptName}」？`} onConfirm={() => deleteMutation.mutate(row.deptId)}>
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
    <Card
      title="部门列表"
      extra={
        can('system:dept:add') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditingDept(null);
              setFormOpen(true);
            }}
          >
            新增部门
          </Button>
        ) : null
      }
    >
      <Table<DeptVo>
        rowKey="deptId"
        loading={isLoading}
        columns={columns}
        dataSource={attachRows(depts)}
        pagination={false}
      />
      {isError && (
        <Button type="primary" onClick={() => refetch().catch(() => message.error('部门加载失败'))}>
          重新加载
        </Button>
      )}
      <Modal
        title={editingDept ? '修改部门' : '新增部门'}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false);
          setEditingDept(null);
        }}
        confirmLoading={saveMutation.isPending}
        onOk={() => {
          form.validateFields().then((values) => {
            saveMutation.mutate({ ...editingDept, ...values });
          });
        }}
        afterClose={() => form.resetFields()}
        destroyOnHidden
      >
        <Form<DeptFormValues> form={form} layout="vertical">
          <Form.Item label="上级部门" name="parentId" rules={[{ required: true, message: '请选择上级部门' }]}>
            <DeptTreeSelect
              depts={depts}
              placeholder="请选择上级部门"
              excludeDeptIds={excludeIds}
            />
          </Form.Item>
          <Form.Item
            label="部门名称"
            name="deptName"
            rules={[{ required: true, message: '请输入部门名称' }]}
          >
            <Input maxLength={30} placeholder="请输入部门名称" />
          </Form.Item>
          <Form.Item label="显示排序" name="orderNum" rules={[{ required: true, message: '请输入显示排序' }]}>
            <InputNumber min={0} max={9999} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="负责人" name="leader">
            <Input maxLength={20} placeholder="请输入负责人" />
          </Form.Item>
          <Form.Item label="联系电话" name="phone">
            <Input maxLength={20} placeholder="请输入联系电话" />
          </Form.Item>
          <Form.Item
            label="邮箱"
            name="email"
            rules={[{ type: 'email', message: '邮箱格式不正确' }]}
          >
            <Input maxLength={50} placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item label="状态" name="status">
            <Radio.Group>
              <Radio value="0">正常</Radio>
              <Radio value="1">停用</Radio>
            </Radio.Group>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
