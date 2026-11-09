import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useMutation } from '@tanstack/react-query';
import { App as AntdApp, Button, Form, Input, InputNumber, Modal, Popconfirm, Radio, Select, Tag } from 'antd';
import { history, useLocation } from '@umijs/max';
import { useEffect, useRef, useState } from 'react';
import {
  createDictDataApi,
  deleteDictDataApi,
  listDictDataApi,
  updateDictDataApi,
  type DictDataPayload,
  type DictDataVo,
} from '@/api/system/dict';
import { clearDictCache } from '@/hooks/useDict';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

/** 字典数据表单值：新增/修改共用 */
interface DictDataFormValues {
  dictType: string;
  dictLabel: string;
  dictValue: string;
  dictSort?: number;
  isDefault?: string;
  listClass?: string;
  status?: string;
}

/** 回显样式可选项（对应后端 list_class） */
const LIST_CLASS_OPTIONS = ['primary', 'success', 'warning', 'danger', 'info'].map((value) => ({
  value,
  label: value,
}));

/**
 * 字典数据（从表）：由字典类型页跳转进入，按 query 中的 dictType 过滤；
 * 列表/详情要求 system:dict:query，维护按钮要求 system:dict:add/edit/remove。
 */
export default function DictDataPage() {
  const { message } = AntdApp.useApp();
  const location = useLocation();
  const actionRef = useRef<ActionType>(undefined);
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);
  const [form] = Form.useForm<DictDataFormValues>();

  // 主从跳转携带的字典类型（/system/dict/data/index?dictType=xxx）
  const initialDictType = new URLSearchParams(location.search).get('dictType') ?? undefined;
  const [editingData, setEditingData] = useState<DictDataVo | null>(null);
  const [formOpen, setFormOpen] = useState(false);

  // 打开弹窗时按字典数据回填
  useEffect(() => {
    if (!formOpen) {
      return;
    }
    if (editingData) {
      form.setFieldsValue({
        dictType: editingData.dictType,
        dictLabel: editingData.dictLabel,
        dictValue: editingData.dictValue,
        dictSort: editingData.dictSort ?? 0,
        isDefault: editingData.isDefault ?? 'N',
        listClass: editingData.listClass,
        status: editingData.status ?? '0',
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        dictType: initialDictType,
        dictSort: 0,
        isDefault: 'N',
        status: '0',
      });
    }
  }, [formOpen, editingData, initialDictType, form]);

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const saveMutation = useMutation({
    mutationFn: async (values: DictDataPayload) => {
      if (values.dictCode !== undefined) {
        await updateDictDataApi(values);
      } else {
        await createDictDataApi(values);
      }
    },
    onSuccess: (_data, values) => {
      message.success(values.dictCode !== undefined ? '字典数据修改成功' : '字典数据新增成功');
      setFormOpen(false);
      setEditingData(null);
      // 维护后失效该类型的字典缓存，useDict 消费者下次读取拉取最新数据
      clearDictCache(values.dictType);
      // ProTable 数据自管理：必须显式 reload 才能刷新列表
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '字典数据保存失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteDictDataApi,
    onSuccess: () => {
      message.success('字典数据删除成功');
      clearDictCache(initialDictType);
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '字典数据删除失败'),
  });

  const columns: ProColumns<DictDataVo>[] = [
    { title: '编码', dataIndex: 'dictCode', width: 70, search: false },
    { title: '字典类型', dataIndex: 'dictType', width: 180, initialValue: initialDictType },
    { title: '字典标签', dataIndex: 'dictLabel', width: 140 },
    { title: '字典键值', dataIndex: 'dictValue', width: 110, search: false },
    { title: '排序', dataIndex: 'dictSort', width: 70, search: false },
    {
      title: '回显样式',
      dataIndex: 'listClass',
      width: 100,
      search: false,
      render: (_, row) => (row.listClass ? <Tag color="blue">{row.listClass}</Tag> : null),
    },
    {
      title: '是否默认',
      dataIndex: 'isDefault',
      width: 90,
      search: false,
      render: (_, row) => (row.isDefault === 'Y' ? '是' : '否'),
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
        can('system:dict:edit') ? (
          <Button
            key="edit"
            type="link"
            size="small"
            onClick={() => {
              setEditingData(row);
              setFormOpen(true);
            }}
          >
            修改
          </Button>
        ) : null,
        can('system:dict:remove') ? (
          <Popconfirm
            key="delete"
            title={`确认删除字典项「${row.dictLabel}」？`}
            onConfirm={() => row.dictCode !== undefined && deleteMutation.mutate(row.dictCode)}
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
      <ProTable<DictDataVo>
        headerTitle="字典数据列表"
        rowKey="dictCode"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 90 }}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        toolBarRender={() => [
          can('system:dict:add') ? (
            <Button
              key="add"
              type="primary"
              onClick={() => {
                setEditingData(null);
                setFormOpen(true);
              }}
            >
              新增字典数据
            </Button>
          ) : null,
          <Button key="back" onClick={() => history.push('/system/dict/index')}>
            返回类型
          </Button>,
        ]}
        request={async (params) => {
          const data = await listDictDataApi({
            pageNum: params.current ?? 1,
            pageSize: params.pageSize ?? 10,
            dictType: params.dictType,
            dictLabel: params.dictLabel,
            status: params.status,
          });
          return { data: data.rows, total: data.total, success: true };
        }}
      />
      <Modal
        title={editingData ? '修改字典数据' : '新增字典数据'}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false);
          setEditingData(null);
        }}
        confirmLoading={saveMutation.isPending}
        onOk={() => {
          form.validateFields().then((values) => {
            saveMutation.mutate({ ...editingData, ...values });
          });
        }}
        afterClose={() => form.resetFields()}
        destroyOnHidden
      >
        <Form<DictDataFormValues> form={form} layout="vertical">
          <Form.Item
            label="字典类型"
            name="dictType"
            rules={[
              { required: true, message: '请输入字典类型' },
              { max: 100, message: '字典类型长度不能超过100' },
            ]}
          >
            <Input maxLength={100} placeholder="请输入字典类型" disabled={editingData !== null} />
          </Form.Item>
          <Form.Item
            label="字典标签"
            name="dictLabel"
            rules={[
              { required: true, message: '请输入字典标签' },
              { max: 100, message: '字典标签长度不能超过100' },
            ]}
          >
            <Input maxLength={100} placeholder="请输入字典标签" />
          </Form.Item>
          <Form.Item
            label="字典键值"
            name="dictValue"
            rules={[
              { required: true, message: '请输入字典键值' },
              { max: 100, message: '字典键值长度不能超过100' },
            ]}
          >
            <Input maxLength={100} placeholder="请输入字典键值" />
          </Form.Item>
          <Form.Item label="显示排序" name="dictSort" rules={[{ required: true, message: '请输入显示排序' }]}>
            <InputNumber min={0} max={9999} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="是否默认" name="isDefault">
            <Radio.Group>
              <Radio value="Y">是</Radio>
              <Radio value="N">否</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item label="回显样式" name="listClass">
            <Select allowClear options={LIST_CLASS_OPTIONS} placeholder="请选择回显样式" />
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
