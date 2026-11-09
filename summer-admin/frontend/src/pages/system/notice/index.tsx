import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useMutation } from '@tanstack/react-query';
import { App as AntdApp, Button, Form, Input, Modal, Popconfirm, Radio, Select, Tag } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  createNoticeApi,
  deleteNoticeApi,
  NOTICE_STATUS,
  updateNoticeApi,
  type NoticePayload,
  type NoticeVo,
  listNoticeApi,
} from '@/api/system/notice';
import { useDict } from '@/hooks/useDict';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

/** 公告状态展示：0已发布 1关闭 2草稿（与后端 NoticeService 常量一致） */
const STATUS_ENUM = {
  [NOTICE_STATUS.PUBLISHED]: { text: '已发布', status: 'Success' as const },
  [NOTICE_STATUS.CLOSED]: { text: '已关闭', status: 'Default' as const },
  [NOTICE_STATUS.DRAFT]: { text: '草稿', status: 'Warning' as const },
};

/** 公告表单值：新增/修改共用 */
interface NoticeFormValues {
  noticeTitle: string;
  noticeType?: string;
  noticeContent?: string;
  status?: string;
}

/** 通知公告：管理端分页 CRUD，可见草稿，支持 发布/关闭/草稿 状态流转 */
export default function NoticePage() {
  const { message } = AntdApp.useApp();
  const actionRef = useRef<ActionType>(undefined);
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);
  const [form] = Form.useForm<NoticeFormValues>();

  // 公告类型下拉选项来自字典（仅启用项，保持后端排序）；字典不可用回退内置两项
  const { dicts } = useDict('sys_notice_type');
  const noticeTypeOptions = useMemo(() => {
    const items = dicts.sys_notice_type ?? [];
    if (items.length === 0) {
      return [
        { value: '1', label: '通知' },
        { value: '2', label: '公告' },
      ];
    }
    return items.map((item) => ({ value: item.dictValue, label: item.dictLabel }));
  }, [dicts.sys_notice_type]);

  const [editingNotice, setEditingNotice] = useState<NoticeVo | null>(null);
  const [formOpen, setFormOpen] = useState(false);

  // 打开弹窗时按公告回填
  useEffect(() => {
    if (!formOpen) {
      return;
    }
    if (editingNotice) {
      form.setFieldsValue({
        noticeTitle: editingNotice.noticeTitle,
        noticeType: editingNotice.noticeType,
        noticeContent: editingNotice.noticeContent,
        status: editingNotice.status ?? NOTICE_STATUS.PUBLISHED,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ status: NOTICE_STATUS.DRAFT });
    }
  }, [formOpen, editingNotice, form]);

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const saveMutation = useMutation({
    mutationFn: async (values: NoticePayload) => {
      if (values.noticeId !== undefined) {
        await updateNoticeApi(values);
      } else {
        await createNoticeApi(values);
      }
    },
    onSuccess: (_data, values) => {
      message.success(values.noticeId !== undefined ? '公告修改成功' : '公告新增成功');
      setFormOpen(false);
      setEditingNotice(null);
      // ProTable 数据自管理：必须显式 reload 才能刷新列表
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '公告保存失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteNoticeApi,
    onSuccess: () => {
      message.success('公告删除成功');
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '公告删除失败'),
  });

  const columns: ProColumns<NoticeVo>[] = [
    { title: '编号', dataIndex: 'noticeId', width: 70, search: false },
    { title: '公告标题', dataIndex: 'noticeTitle', width: 220, ellipsis: true },
    {
      title: '公告类型',
      dataIndex: 'noticeType',
      width: 100,
      valueType: 'select',
      valueEnum: {
        '1': { text: '通知' },
        '2': { text: '公告' },
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueType: 'select',
      valueEnum: {
        [NOTICE_STATUS.PUBLISHED]: { text: '已发布' },
        [NOTICE_STATUS.CLOSED]: { text: '已关闭' },
        [NOTICE_STATUS.DRAFT]: { text: '草稿' },
      },
      render: (_, row) => {
        const meta = STATUS_ENUM[(row.status ?? NOTICE_STATUS.PUBLISHED) as keyof typeof STATUS_ENUM];
        return <Tag color={meta?.status === 'Success' ? 'success' : meta?.status === 'Warning' ? 'warning' : 'default'}>{meta?.text ?? row.status}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        can('system:notice:edit') ? (
          <Button
            key="edit"
            type="link"
            size="small"
            onClick={() => {
              setEditingNotice(row);
              setFormOpen(true);
            }}
          >
            修改
          </Button>
        ) : null,
        can('system:notice:remove') ? (
          <Popconfirm
            key="delete"
            title={`确认删除公告「${row.noticeTitle}」？`}
            onConfirm={() => row.noticeId !== undefined && deleteMutation.mutate(row.noticeId)}
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
      <ProTable<NoticeVo>
        headerTitle="公告列表"
        rowKey="noticeId"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 90 }}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        toolBarRender={() =>
          can('system:notice:add')
            ? [
                <Button
                  key="add"
                  type="primary"
                  onClick={() => {
                    setEditingNotice(null);
                    setFormOpen(true);
                  }}
                >
                  新增公告
                </Button>,
              ]
            : []
        }
        request={async (params) => {
          const data = await listNoticeApi({
            pageNum: params.current ?? 1,
            pageSize: params.pageSize ?? 10,
            noticeTitle: params.noticeTitle,
            noticeType: params.noticeType,
            status: params.status,
          });
          return { data: data.rows, total: data.total, success: true };
        }}
      />
      <Modal
        title={editingNotice ? '修改公告' : '新增公告'}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false);
          setEditingNotice(null);
        }}
        confirmLoading={saveMutation.isPending}
        onOk={() => {
          form.validateFields().then((values) => {
            saveMutation.mutate({ ...editingNotice, ...values });
          });
        }}
        afterClose={() => form.resetFields()}
        destroyOnHidden
      >
        <Form<NoticeFormValues> form={form} layout="vertical">
          <Form.Item
            label="公告标题"
            name="noticeTitle"
            rules={[
              { required: true, message: '请输入公告标题' },
              { max: 64, message: '公告标题长度不能超过64' },
            ]}
          >
            <Input maxLength={64} placeholder="请输入公告标题" />
          </Form.Item>
          <Form.Item label="公告类型" name="noticeType">
            <Select allowClear options={noticeTypeOptions} placeholder="请选择公告类型" />
          </Form.Item>
          <Form.Item label="状态" name="status">
            <Radio.Group>
              <Radio value={NOTICE_STATUS.PUBLISHED}>发布</Radio>
              <Radio value={NOTICE_STATUS.CLOSED}>关闭</Radio>
              <Radio value={NOTICE_STATUS.DRAFT}>草稿</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            label="公告内容"
            name="noticeContent"
            rules={[{ max: 2000, message: '公告内容长度不能超过2000' }]}
          >
            <Input.TextArea rows={5} maxLength={2000} placeholder="请输入公告内容" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
