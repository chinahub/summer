import { Button, Result } from 'antd';
import { history } from '@umijs/max';

/** 安全 404：白名单未命中、路由不存在时的统一回退页 */
export default function NotFoundPage() {
  return (
    <Result
      status="404"
      title="404"
      subTitle="页面不存在或没有访问权限"
      extra={
        <Button type="primary" onClick={() => history.push('/')}>
          返回首页
        </Button>
      }
    />
  );
}
