import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useMutation } from '@tanstack/react-query';
import { App as AntdApp, Button, Form, Input, Modal, Popconfirm, Radio } from 'antd';
import { history } from '@umijs/max';
import { useEffect, useRef, useState } from 'react';
import {
  createDictTypeApi,
  deleteDictTypeApi,
  updateDictTypeApi,
  type DictTypePayload,
  type DictTypeVo,
  listDictTypeApi,
} from '@/api/system/dict';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

/** 字典类型表单值：新增/修改共用 */
interface DictTypeFormValues {
  dictName: string;
  dictType: string;
  status?: string;
}

/** 字典管理（主表）：类型分页 CRUD，可跳转字典数据主从页 */
export default function DictTypePage() {
  const { message } = AntdApp.useApp();
  const actionRef = useRef<ActionType>(undefined);
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);
  const [form] = Form.useForm<DictTypeFormValues>();

  const [editingType, setEditingType] = useState<DictTypeVo | null>(null);
  const [formOpen, setFormOpen] = useState(false);

  // 打开弹窗时按字典类型回填
  useEffect(() => {
    if (!formOpen) {
      return;
    }
    if (editingType) {
      form.setFieldsValue({
        dictName: editingType.dictName,
        dictType: editingType.dictType,
        status: editingType.status ?? '0',
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ status: '0' });
    }
  }, [formOpen, editingType, form]);

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const saveMutation = useMutation({
    mutationFn: async (values: DictTypePayload) => {
      if (values.dictId !== undefined) {
        await updateDictTypeApi(values);
      } else {
        await createDictTypeApi(values);
      }
    },
    onSuccess: (_data, values) => {
      message.success(values.dictId !== undefined ? '字典类型修改成功' : '字典类型新增成功');
      setFormOpen(false);
      setEditingType(null);
      // ProTable 数据自管理：必须显式 reload 才能刷新列表
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '字典类型保存失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteDictTypeApi,
    onSuccess: () => {
      message.success('字典类型删除成功');
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '字典类型删除失败'),
  });

  const columns: ProColumns<DictTypeVo>[] = [
    { title: '编号', dataIndex: 'dictId', width: 70, search: false },
    { title: '字典名称', dataIndex: 'dictName', width: 160 },
    { title: '字典类型', dataIndex: 'dictType', width: 200 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueType: 'select',
      valueEnum: {
        '0': { text: '正常' },
        '1': { text: '停用' },
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        can('system:dict:query') ? (
          <Button
            key="data"
            type="link"
            size="small"
            onClick={() =>
              history.push(`/system/dict/data/index?dictType=${encodeURIComponent(row.dictType)}`)
            }
          >
            数据
          </Button>
        ) : null,
        can('system:dict:edit') ? (
          <Button
            key="edit"
            type="link"
            size="small"
            onClick={() => {
              setEditingType(row);
              setFormOpen(true);
            }}
          >
            修改
          </Button>
        ) : null,
        can('system:dict:remove') ? (
          <Popconfirm
            key="delete"
            title={`确认删除字典「${row.dictName}」？`}
            onConfirm={() => deleteMutation.mutate(row.dictId)}
          >
            <Button type="link" size="small" danger loading={deleteMutation.isPending}>
              删除
            </Button>
          </Popconfirm>
        ) : null,
      ],
    },
  ];

  return (
    <>
      <ProTable<DictTypeVo>
        headerTitle="字典类型列表"
        rowKey="dictId"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 90 }}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        toolBarRender={() =>
          can('system:dict:add')
            ? [
                <Button
                  key="add"
                  type="primary"
                  onClick={() => {
                    setEditingType(null);
                    setFormOpen(true);
                  }}
                >
                  新增字典类型
                </Button>,
              ]
            : []
        }
        request={async (params) => {
          const data = await listDictTypeApi({
            pageNum: params.current ?? 1,
            pageSize: params.pageSize ?? 10,
            dictName: params.dictName,
            dictType: params.dictType,
            status: params.status,
          });
          return { data: data.rows, total: data.total, success: true };
        }}
      />
      <Modal
        title={editingType ? '修改字典类型' : '新增字典类型'}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false);
          setEditingType(null);
        }}
        confirmLoading={saveMutation.isPending}
        onOk={() => {
          form.validateFields().then((values) => {
            saveMutation.mutate({ ...editingType, ...values });
          });
        }}
        afterClose={() => form.resetFields()}
        destroyOnHidden
      >
        <Form<DictTypeFormValues> form={form} layout="vertical">
          <Form.Item
            label="字典名称"
            name="dictName"
            rules={[
              { required: true, message: '请输入字典名称' },
              { max: 100, message: '字典名称长度不能超过100' },
            ]}
          >
            <Input maxLength={100} placeholder="请输入字典名称" />
          </Form.Item>
          <Form.Item
            label="字典类型"
            name="dictType"
            rules={[
              { required: true, message: '请输入字典类型' },
              { max: 100, message: '字典类型长度不能超过100' },
            ]}
          >
            <Input maxLength={100} placeholder="请输入字典类型，如 sys_xxx" />
          </Form.Item>
          <Form.Item label="状态" name="status">
            <Radio.Group>
              <Radio value="0">正常</Radio>
              <Radio value="1">停用</Radio>
            </Radio.Group>
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
