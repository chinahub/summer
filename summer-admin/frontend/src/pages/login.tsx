import { LockOutlined, SafetyOutlined, UserOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Checkbox, Form, Input } from 'antd';
import { history } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
import { captchaApi, loginApi, routersApi, userinfoApi, type CaptchaResult } from '@/api/login';
import { usePermissionStore } from '@/stores/permissionStore';
import { useUserStore } from '@/stores/userStore';
import { filterAuthorizedMenus } from '@/utils/menu';

/** 记住用户名的本地存储键 */
const REMEMBER_USERNAME_KEY = 'Admin-Remember-Username';

interface LoginFormValues {
  username: string;
  password: string;
  code?: string;
  remember?: boolean;
}

/** 登录页：账号 + 密码 + 图形验证码 + 记住用户名，无注册/第三方/语言切换入口 */
export default function LoginPage() {
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<LoginFormValues>();
  const [captcha, setCaptcha] = useState<CaptchaResult | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // 已登录直接回首页
  useEffect(() => {
    if (useUserStore.getState().token) {
      history.push('/');
    }
  }, []);

  /** 拉取最新验证码，登录失败与点击图片时刷新 */
  const refreshCaptcha = useCallback(() => {
    captchaApi()
      .then(setCaptcha)
      .catch(() => setCaptcha(null));
  }, []);

  useEffect(refreshCaptcha, [refreshCaptcha]);

  // 记住用户名回填
  useEffect(() => {
    const remembered = localStorage.getItem(REMEMBER_USERNAME_KEY);
    if (remembered) {
      form.setFieldsValue({ username: remembered, remember: true });
    }
  }, [form]);

  /** 登录成功：保存令牌并加载 userinfo/动态路由，随后进入首页 */
  async function handleLogin(values: LoginFormValues) {
    setSubmitting(true);
    try {
      const result = await loginApi({
        username: values.username.trim(),
        password: values.password,
        code: captcha?.captchaEnabled ? values.code : undefined,
        uuid: captcha?.captchaEnabled ? captcha.uuid : undefined,
      });
      if (values.remember) {
        localStorage.setItem(REMEMBER_USERNAME_KEY, values.username.trim());
      } else {
        localStorage.removeItem(REMEMBER_USERNAME_KEY);
      }
      const userStore = useUserStore.getState();
      userStore.setToken(result.access_token);
      const [userinfo, routers] = await Promise.all([userinfoApi(), routersApi()]);
      userStore.setUserInfo({
        userId: userinfo.user.userId,
        userName: userinfo.user.userName,
        nickName: userinfo.user.nickName,
        avatar: userinfo.user.avatar,
        roles: userinfo.roles ?? [],
        permissions: userinfo.permissions ?? [],
      });
      usePermissionStore
        .getState()
        .setRoutes(filterAuthorizedMenus(routers ?? [], userinfo.permissions ?? []));
      message.success('登录成功');
      history.push('/');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败');
      form.setFieldsValue({ code: undefined });
      refreshCaptcha();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #1f2d3d 0%, #3a506b 100%)',
      }}
    >
      <div
        style={{
          width: 400,
          padding: '40px 36px 32px',
          background: '#fff',
          borderRadius: 8,
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.25)',
        }}
      >
        <h2 style={{ textAlign: 'center', marginBottom: 24 }}>summer-admin 后台管理系统</h2>
        <Form<LoginFormValues>
          form={form}
          size="large"
          onFinish={handleLogin}
          initialValues={{ remember: false }}
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="账号" autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
              autoComplete="current-password"
            />
          </Form.Item>
          {captcha?.captchaEnabled && (
            <Form.Item
              name="code"
              rules={[{ required: true, message: '请输入验证码' }]}
            >
              <div style={{ display: 'flex', gap: 8 }}>
                <Input
                  prefix={<SafetyOutlined />}
                  placeholder="验证码"
                  maxLength={4}
                  style={{ flex: 1 }}
                />
                <img
                  src={captcha.img}
                  alt="验证码"
                  title="点击刷新验证码"
                  onClick={refreshCaptcha}
                  style={{ height: 40, cursor: 'pointer', borderRadius: 6 }}
                />
              </div>
            </Form.Item>
          )}
          <Form.Item name="remember" valuePropName="checked" style={{ marginBottom: 12 }}>
            <Checkbox>记住用户名</Checkbox>
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={submitting}>
              登 录
            </Button>
          </Form.Item>
        </Form>
      </div>
    </div>
  );
}
