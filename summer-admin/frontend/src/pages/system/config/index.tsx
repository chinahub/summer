import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useMutation } from '@tanstack/react-query';
import { App as AntdApp, Button, Form, Input, Modal, Popconfirm, Radio } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  createConfigApi,
  deleteConfigApi,
  updateConfigApi,
  type ConfigPayload,
  type ConfigVo,
  listConfigApi,
} from '@/api/system/config';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

/** 参数表单值：新增/修改共用 */
interface ConfigFormValues {
  configName: string;
  configKey: string;
  configValue?: string;
  configType?: string;
  status?: string;
}

/** 参数管理：标准分页 CRUD，支持名称/键名/内置/状态条件查询 */
export default function ConfigPage() {
  const { message } = AntdApp.useApp();
  const actionRef = useRef<ActionType>(undefined);
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);
  const [form] = Form.useForm<ConfigFormValues>();

  const [editingConfig, setEditingConfig] = useState<ConfigVo | null>(null);
  const [formOpen, setFormOpen] = useState(false);

  // 打开弹窗时按参数回填
  useEffect(() => {
    if (!formOpen) {
      return;
    }
    if (editingConfig) {
      form.setFieldsValue({
        configName: editingConfig.configName,
        configKey: editingConfig.configKey,
        configValue: editingConfig.configValue,
        configType: editingConfig.configType ?? 'N',
        status: editingConfig.status ?? '0',
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ configType: 'N', status: '0' });
    }
  }, [formOpen, editingConfig, form]);

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const saveMutation = useMutation({
    mutationFn: async (values: ConfigPayload) => {
      if (values.configId !== undefined) {
        await updateConfigApi(values);
      } else {
        await createConfigApi(values);
      }
    },
    onSuccess: (_data, values) => {
      message.success(values.configId !== undefined ? '参数修改成功' : '参数新增成功');
      setFormOpen(false);
      setEditingConfig(null);
      // ProTable 数据自管理：必须显式 reload 才能刷新列表
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '参数保存失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteConfigApi,
    onSuccess: () => {
      message.success('参数删除成功');
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '参数删除失败'),
  });

  const columns: ProColumns<ConfigVo>[] = [
    { title: '编号', dataIndex: 'configId', width: 70, search: false },
    { title: '参数名称', dataIndex: 'configName', width: 160 },
    { title: '参数键名', dataIndex: 'configKey', width: 220 },
    { title: '参数键值', dataIndex: 'configValue', width: 180, search: false, ellipsis: true },
    {
      title: '系统内置',
      dataIndex: 'configType',
      width: 100,
      valueType: 'select',
      valueEnum: {
        Y: { text: '是' },
        N: { text: '否' },
      },
    },
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
      width: 140,
      render: (_, row) => [
        can('system:config:edit') ? (
          <Button
            key="edit"
            type="link"
            size="small"
            onClick={() => {
              setEditingConfig(row);
              setFormOpen(true);
            }}
          >
            修改
          </Button>
        ) : null,
        can('system:config:remove') ? (
          <Popconfirm
            key="delete"
            title={`确认删除参数「${row.configName}」？`}
            onConfirm={() => row.configId !== undefined && deleteMutation.mutate(row.configId)}
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
      <ProTable<ConfigVo>
        headerTitle="参数列表"
        rowKey="configId"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 90 }}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        toolBarRender={() =>
          can('system:config:add')
            ? [
                <Button
                  key="add"
                  type="primary"
                  onClick={() => {
                    setEditingConfig(null);
                    setFormOpen(true);
                  }}
                >
                  新增参数
                </Button>,
              ]
            : []
        }
        request={async (params) => {
          const data = await listConfigApi({
            pageNum: params.current ?? 1,
            pageSize: params.pageSize ?? 10,
            configName: params.configName,
            configKey: params.configKey,
            configType: params.configType,
            status: params.status,
          });
          return { data: data.rows, total: data.total, success: true };
        }}
      />
      <Modal
        title={editingConfig ? '修改参数' : '新增参数'}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false);
          setEditingConfig(null);
        }}
        confirmLoading={saveMutation.isPending}
        onOk={() => {
          form.validateFields().then((values) => {
            saveMutation.mutate({ ...editingConfig, ...values });
          });
        }}
        afterClose={() => form.resetFields()}
        destroyOnHidden
      >
        <Form<ConfigFormValues> form={form} layout="vertical">
          <Form.Item
            label="参数名称"
            name="configName"
            rules={[
              { required: true, message: '请输入参数名称' },
              { max: 100, message: '参数名称长度不能超过100' },
            ]}
          >
            <Input maxLength={100} placeholder="请输入参数名称" />
          </Form.Item>
          <Form.Item
            label="参数键名"
            name="configKey"
            rules={[
              { required: true, message: '请输入参数键名' },
              { max: 100, message: '参数键名长度不能超过100' },
            ]}
          >
            <Input maxLength={100} placeholder="请输入参数键名" />
          </Form.Item>
          <Form.Item label="参数键值" name="configValue" rules={[{ max: 500, message: '参数键值长度不能超过500' }]}>
            <Input.TextArea rows={3} maxLength={500} placeholder="请输入参数键值" />
          </Form.Item>
          <Form.Item label="系统内置" name="configType">
            <Radio.Group>
              <Radio value="Y">是</Radio>
              <Radio value="N">否</Radio>
            </Radio.Group>
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
