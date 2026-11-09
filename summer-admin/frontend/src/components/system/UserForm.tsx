import { Form, Input, Modal, Radio, Select } from 'antd';
import { useEffect } from 'react';
import type { DeptVo } from '@/api/system/dept';
import type { UserFormValues, UserVo } from '@/api/system/user';
import DeptTreeSelect from '@/components/system/DeptTreeSelect';

/** 角色/岗位下拉选项：来自后端分页接口（pageSize=100 取全量常用值） */
export interface OptionItem {
  label: string;
  value: number;
}

interface UserFormProps {
  open: boolean;
  /** null 表示新增，否则为编辑目标 */
  initial: UserVo | null;
  depts: DeptVo[];
  roleOptions: OptionItem[];
  postOptions: OptionItem[];
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: UserFormValues) => void;
}

/** 用户新增/编辑表单：部门树、角色/岗位多选、状态与性别（密码仅新增展示） */
export default function UserForm({
  open,
  initial,
  depts,
  roleOptions,
  postOptions,
  submitting,
  onCancel,
  onSubmit,
}: UserFormProps) {
  const [form] = Form.useForm<UserFormValues>();

  // 打开弹窗时按目标用户回填；新增恢复默认值
  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        userName: initial.userName,
        nickName: initial.nickName,
        deptId: initial.deptId,
        phonenumber: initial.phonenumber,
        email: initial.email,
        sex: initial.sex ?? '2',
        status: initial.status ?? '0',
        roleIds: initial.roleIds ?? [],
        postIds: initial.postIds ?? [],
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ sex: '2', status: '0', roleIds: [], postIds: [] });
    }
  }, [open, initial, form]);

  return (
    <Modal
      title={initial ? '修改用户' : '新增用户'}
      open={open}
      onCancel={onCancel}
      confirmLoading={submitting}
      onOk={() => {
        form.validateFields().then((values) => {
          onSubmit({ ...values, userId: initial?.userId });
        });
      }}
      afterClose={() => form.resetFields()}
      width={560}
      destroyOnHidden
    >
      <Form<UserFormValues> form={form} layout="vertical">
        <Form.Item
          label="用户昵称"
          name="nickName"
          rules={[
            { required: true, message: '请输入用户昵称' },
            { max: 30, message: '用户昵称长度不能超过30' },
          ]}
        >
          <Input maxLength={30} placeholder="请输入用户昵称" />
        </Form.Item>
        <Form.Item
          label="登录名称"
          name="userName"
          rules={[
            { required: true, message: '请输入登录名称' },
            { max: 30, message: '登录名称长度不能超过30' },
          ]}
        >
          <Input maxLength={30} placeholder="请输入登录名称" autoComplete="off" />
        </Form.Item>
        {initial ? null : (
          <Form.Item
            label="用户密码"
            name="password"
            rules={[
              { required: true, message: '请输入用户密码' },
              { min: 6, max: 64, message: '密码长度必须在6-64之间' },
            ]}
          >
            <Input.Password maxLength={64} placeholder="请输入用户密码" autoComplete="new-password" />
          </Form.Item>
        )}
        <Form.Item label="归属部门" name="deptId" rules={[{ required: true, message: '请选择归属部门' }]}>
          <DeptTreeSelect depts={depts} />
        </Form.Item>
        <Form.Item label="手机号码" name="phonenumber" rules={[{ max: 20, message: '手机号长度不能超过20' }]}>
          <Input maxLength={20} placeholder="请输入手机号码" />
        </Form.Item>
        <Form.Item
          label="邮箱"
          name="email"
          rules={[
            { type: 'email', message: '邮箱格式不正确' },
            { max: 50, message: '邮箱长度不能超过50' },
          ]}
        >
          <Input maxLength={50} placeholder="请输入邮箱" />
        </Form.Item>
        <Form.Item label="性别" name="sex">
          <Radio.Group>
            <Radio value="0">男</Radio>
            <Radio value="1">女</Radio>
            <Radio value="2">未知</Radio>
          </Radio.Group>
        </Form.Item>
        <Form.Item label="状态" name="status">
          <Radio.Group>
            <Radio value="0">正常</Radio>
            <Radio value="1">停用</Radio>
          </Radio.Group>
        </Form.Item>
        <Form.Item label="角色" name="roleIds">
          <Select
            mode="multiple"
            options={roleOptions}
            placeholder="请选择角色"
            optionFilterProp="label"
            allowClear
          />
        </Form.Item>
        <Form.Item label="岗位" name="postIds">
          <Select
            mode="multiple"
            options={postOptions}
            placeholder="请选择岗位"
            optionFilterProp="label"
            allowClear
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
