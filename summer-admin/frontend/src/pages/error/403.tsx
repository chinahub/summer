import { Button, Result } from 'antd';
import { history } from '@umijs/max';

/** 403：已登录但无权限访问的受保护路由统一回退页 */
export default function ForbiddenPage() {
  return (
    <Result
      status="403"
      title="403"
      subTitle="抱歉，你没有权限访问该页面"
      extra={
        <Button type="primary" onClick={() => history.push('/')}>
          返回首页
        </Button>
      }
    />
  );
}
