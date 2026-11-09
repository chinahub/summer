import { Form, Input, Modal } from 'antd';
import { useEffect } from 'react';

interface ResetPasswordDialogProps {
  open: boolean;
  userName?: string;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (password: string) => void;
}

interface ResetPasswordFormValues {
  password: string;
  confirmPassword: string;
}

/** 管理端重置密码弹窗：新密码 6-64 位且二次确认，提交回调纯密码串 */
export default function ResetPasswordDialog({
  open,
  userName,
  submitting,
  onCancel,
  onSubmit,
}: ResetPasswordDialogProps) {
  const [form] = Form.useForm<ResetPasswordFormValues>();

  useEffect(() => {
    if (open) {
      form.resetFields();
    }
  }, [open, form]);

  return (
    <Modal
      title={`重置密码${userName ? `：${userName}` : ''}`}
      open={open}
      onCancel={onCancel}
      confirmLoading={submitting}
      onOk={() => {
        form.validateFields().then((values) => {
          onSubmit(values.password);
        });
      }}
      afterClose={() => form.resetFields()}
      destroyOnHidden
    >
      <Form<ResetPasswordFormValues> form={form} layout="vertical">
        <Form.Item
          label="新密码"
          name="password"
          rules={[
            { required: true, message: '请输入新密码' },
            { min: 6, max: 64, message: '密码长度必须在6-64之间' },
          ]}
        >
          <Input.Password maxLength={64} placeholder="请输入新密码" autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          label="确认密码"
          name="confirmPassword"
          dependencies={['password']}
          rules={[
            { required: true, message: '请确认新密码' },
            ({ getFieldValue }) => ({
              validator: (_, value) =>
                !value || value === getFieldValue('password')
                  ? Promise.resolve()
                  : Promise.reject(new Error('两次输入的密码不一致')),
            }),
          ]}
        >
          <Input.Password maxLength={64} placeholder="请确认新密码" autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
