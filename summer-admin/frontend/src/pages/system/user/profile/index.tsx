import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Card, Col, Descriptions, Form, Input, Radio, Row } from 'antd';
import { useEffect } from 'react';
import {
  changePasswordApi,
  getProfileApi,
  updateProfileApi,
  type ProfileVo,
} from '@/api/system/profile';
import { useUserStore } from '@/stores/userStore';

interface ProfileFormValues {
  nickName?: string;
  phonenumber?: string;
  email?: string;
  sex?: string;
}

interface PasswordFormValues {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/** 个人中心：白名单资料修改与原密码校验的修改密码 */
export default function ProfilePage() {
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const [profileForm] = Form.useForm<ProfileFormValues>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();
  const setUserInfo = useUserStore((state) => state.setUserInfo);
  const userInfo = useUserStore((state) => state.userInfo);

  const { data: profile, isLoading, isError } = useQuery({
    queryKey: ['system-profile'],
    queryFn: getProfileApi,
    retry: 1,
  });

  // 资料回填编辑表单（仅白名单字段）
  useEffect(() => {
    if (profile) {
      profileForm.setFieldsValue({
        nickName: profile.nickName,
        phonenumber: profile.phonenumber,
        email: profile.email,
        sex: profile.sex ?? '0',
      });
    }
  }, [profile, profileForm]);

  /** 资料更新成功后同步 userStore 与查询缓存 */
  function applyProfile(next: ProfileVo) {
    if (userInfo) {
      setUserInfo({
        ...userInfo,
        nickName: next.nickName,
        avatar: next.avatar,
      });
    }
    queryClient.setQueryData(['system-profile'], next);
  }

  const updateMutation = useMutation({
    mutationFn: updateProfileApi,
    onSuccess: async () => {
      message.success('资料修改成功');
      const next = await getProfileApi();
      applyProfile(next);
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '资料修改失败');
    },
  });

  const passwordMutation = useMutation({
    mutationFn: changePasswordApi,
    onSuccess: () => {
      message.success('密码修改成功');
      passwordForm.resetFields();
    },
    onError: (error) => {
      message.error(error instanceof Error ? error.message : '密码修改失败');
    },
  });

  async function handleUpdateProfile(values: ProfileFormValues) {
    updateMutation.mutate(values);
  }

  async function handleChangePassword(values: PasswordFormValues) {
    passwordMutation.mutate({
      oldPassword: values.oldPassword,
      newPassword: values.newPassword,
    });
  }

  if (isError) {
    return (
      <Card title="个人中心">
        <Button
          type="primary"
          onClick={() => {
            queryClient.invalidateQueries({ queryKey: ['system-profile'] }).catch(() => {
              message.error('资料加载失败');
            });
          }}
        >
          重新加载
        </Button>
      </Card>
    );
  }

  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={9}>
        <Card title="个人信息" loading={isLoading}>
          {profile && (
            <Descriptions column={1}>
              <Descriptions.Item label="登录名称">{profile.userName}</Descriptions.Item>
              <Descriptions.Item label="所属部门">{profile.deptName ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="所属角色">
                {profile.roleNames?.join('、') || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="所属岗位">
                {profile.postNames?.join('、') || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="创建日期">{profile.createTime ?? '-'}</Descriptions.Item>
            </Descriptions>
          )}
        </Card>
      </Col>
      <Col xs={24} lg={15}>
        <Card title="基本资料" style={{ marginBottom: 16 }} loading={isLoading}>
          <Form<ProfileFormValues>
            form={profileForm}
            labelCol={{ span: 4 }}
            onFinish={handleUpdateProfile}
          >
            <Form.Item
              label="用户昵称"
              name="nickName"
              rules={[{ required: true, message: '请输入用户昵称' }]}
            >
              <Input maxLength={30} placeholder="请输入用户昵称" />
            </Form.Item>
            <Form.Item
              label="手机号码"
              name="phonenumber"
              rules={[{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]}
            >
              <Input maxLength={11} placeholder="请输入手机号码" />
            </Form.Item>
            <Form.Item
              label="邮箱"
              name="email"
              rules={[{ type: 'email', message: '邮箱格式不正确' }]}
            >
              <Input maxLength={50} placeholder="请输入邮箱" />
            </Form.Item>
            <Form.Item label="性别" name="sex">
              <Radio.Group>
                <Radio value="0">男</Radio>
                <Radio value="1">女</Radio>
                <Radio value="2">保密</Radio>
              </Radio.Group>
            </Form.Item>
            <Form.Item wrapperCol={{ offset: 4 }}>
              <Button type="primary" htmlType="submit" loading={updateMutation.isPending}>
                保存修改
              </Button>
            </Form.Item>
          </Form>
        </Card>
        <Card title="修改密码">
          <Form<PasswordFormValues>
            form={passwordForm}
            labelCol={{ span: 4 }}
            onFinish={handleChangePassword}
          >
            <Form.Item
              label="旧密码"
              name="oldPassword"
              rules={[{ required: true, message: '请输入旧密码' }]}
            >
              <Input.Password maxLength={50} placeholder="请输入旧密码" autoComplete="current-password" />
            </Form.Item>
            <Form.Item
              label="新密码"
              name="newPassword"
              rules={[
                { required: true, message: '请输入新密码' },
                { min: 6, message: '密码长度不能少于 6 位' },
              ]}
            >
              <Input.Password maxLength={50} placeholder="请输入新密码" autoComplete="new-password" />
            </Form.Item>
            <Form.Item
              label="确认密码"
              name="confirmPassword"
              dependencies={['newPassword']}
              rules={[
                { required: true, message: '请确认新密码' },
                ({ getFieldValue }) => ({
                  validator: (_, value) =>
                    !value || value === getFieldValue('newPassword')
                      ? Promise.resolve()
                      : Promise.reject(new Error('两次输入的密码不一致')),
                }),
              ]}
            >
              <Input.Password maxLength={50} placeholder="请确认新密码" autoComplete="new-password" />
            </Form.Item>
            <Form.Item wrapperCol={{ offset: 4 }}>
              <Button type="primary" htmlType="submit" loading={passwordMutation.isPending}>
                修改密码
              </Button>
            </Form.Item>
          </Form>
        </Card>
      </Col>
    </Row>
  );
}
