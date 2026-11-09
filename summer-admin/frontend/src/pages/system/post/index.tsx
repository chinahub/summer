import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Form, Input, InputNumber, Modal, Popconfirm, Radio } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  createPostApi,
  deletePostApi,
  listPostApi,
  updatePostApi,
  type PostPayload,
  type PostVo,
} from '@/api/system/post';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

/** 岗位表单值：新增/修改共用 */
interface PostFormValues {
  postName: string;
  postCode: string;
  postSort?: number;
  status?: string;
}

/** 岗位管理：标准分页 CRUD，支持名称/编码/状态条件查询 */
export default function PostPage() {
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const actionRef = useRef<ActionType>(undefined);
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);
  const [form] = Form.useForm<PostFormValues>();

  const [editingPost, setEditingPost] = useState<PostVo | null>(null);
  const [formOpen, setFormOpen] = useState(false);

  // 打开弹窗时按岗位回填
  useEffect(() => {
    if (!formOpen) {
      return;
    }
    if (editingPost) {
      form.setFieldsValue({
        postName: editingPost.postName,
        postCode: editingPost.postCode,
        postSort: editingPost.postSort,
        status: editingPost.status ?? '0',
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ postSort: 0, status: '0' });
    }
  }, [formOpen, editingPost, form]);

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const saveMutation = useMutation({
    mutationFn: async (values: PostPayload) => {
      if (values.postId !== undefined) {
        await updatePostApi(values);
      } else {
        await createPostApi(values);
      }
    },
    onSuccess: (_data, values) => {
      message.success(values.postId ? '岗位修改成功' : '岗位新增成功');
      setFormOpen(false);
      setEditingPost(null);
      queryClient.invalidateQueries({ queryKey: ['system-posts'] });
    },
    onError: (error) => onError(error, '岗位保存失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: deletePostApi,
    onSuccess: () => {
      message.success('岗位删除成功');
      queryClient.invalidateQueries({ queryKey: ['system-posts'] });
    },
    onError: (error) => onError(error, '岗位删除失败'),
  });

  const columns: ProColumns<PostVo>[] = [
    { title: '编号', dataIndex: 'postId', width: 70, search: false },
    { title: '岗位名称', dataIndex: 'postName', width: 160 },
    { title: '岗位编码', dataIndex: 'postCode', width: 160 },
    { title: '排序', dataIndex: 'postSort', width: 70, search: false },
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
        can('system:post:edit') ? (
          <Button
            key="edit"
            type="link"
            size="small"
            onClick={() => {
              setEditingPost(row);
              setFormOpen(true);
            }}
          >
            修改
          </Button>
        ) : null,
        can('system:post:remove') ? (
          <Popconfirm key="delete" title={`确认删除岗位「${row.postName}」？`} onConfirm={() => deleteMutation.mutate(row.postId)}>
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
      <ProTable<PostVo>
        headerTitle="岗位列表"
        rowKey="postId"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 90 }}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        toolBarRender={() =>
          can('system:post:add')
            ? [
                <Button
                  key="add"
                  type="primary"
                  onClick={() => {
                    setEditingPost(null);
                    setFormOpen(true);
                  }}
                >
                  新增岗位
                </Button>,
              ]
            : []
        }
        request={async (params) => {
          const data = await listPostApi({
            pageNum: params.current ?? 1,
            pageSize: params.pageSize ?? 10,
            postName: params.postName,
            postCode: params.postCode,
            status: params.status,
          });
          return { data: data.rows, total: data.total, success: true };
        }}
      />
      <Modal
        title={editingPost ? '修改岗位' : '新增岗位'}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false);
          setEditingPost(null);
        }}
        confirmLoading={saveMutation.isPending}
        onOk={() => {
          form.validateFields().then((values) => {
            saveMutation.mutate({ ...editingPost, ...values });
          });
        }}
        afterClose={() => form.resetFields()}
        destroyOnHidden
      >
        <Form<PostFormValues> form={form} layout="vertical">
          <Form.Item
            label="岗位名称"
            name="postName"
            rules={[
              { required: true, message: '请输入岗位名称' },
              { max: 50, message: '岗位名称长度不能超过50' },
            ]}
          >
            <Input maxLength={50} placeholder="请输入岗位名称" />
          </Form.Item>
          <Form.Item
            label="岗位编码"
            name="postCode"
            rules={[
              { required: true, message: '请输入岗位编码' },
              { max: 64, message: '岗位编码长度不能超过64' },
            ]}
          >
            <Input maxLength={64} placeholder="请输入岗位编码" />
          </Form.Item>
          <Form.Item label="显示排序" name="postSort" rules={[{ required: true, message: '请输入显示排序' }]}>
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
    </>
  );
}
