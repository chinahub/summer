import {
  BellOutlined,
  ClusterOutlined,
  TeamOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { App as AntdApp, Button, Card, Col, Row, Statistic } from 'antd';
import type { ReactNode } from 'react';
import { dashboardSummaryApi } from '@/api/dashboard';

interface SummaryItem {
  title: string;
  valueKey: keyof import('@/api/dashboard').DashboardSummary;
  icon: ReactNode;
  color: string;
}

/** 首页汇总卡片配置：计数全部来自 /api/dashboard/summary 真实接口 */
const SUMMARY_ITEMS: SummaryItem[] = [
  { title: '用户数量', valueKey: 'userCount', icon: <TeamOutlined />, color: '#1677ff' },
  { title: '角色数量', valueKey: 'roleCount', icon: <UserSwitchOutlined />, color: '#722ed1' },
  { title: '部门数量', valueKey: 'deptCount', icon: <ClusterOutlined />, color: '#13c2c2' },
  { title: '公告数量', valueKey: 'noticeCount', icon: <BellOutlined />, color: '#fa8c16' },
];

/** 首页：展示当前登录用户可见的系统汇总计数 */
export default function IndexPage() {
  const { message } = AntdApp.useApp();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: dashboardSummaryApi,
    retry: 1,
  });

  if (isError) {
    return (
      <Card>
        <Statistic title="汇总加载失败" value="请稍后重试" />
        <Button
          type="primary"
          style={{ marginTop: 16 }}
          onClick={() => {
            refetch().catch(() => message.error('汇总加载失败'));
          }}
        >
          重新加载
        </Button>
      </Card>
    );
  }

  return (
    <div>
      <Row gutter={[16, 16]}>
        {SUMMARY_ITEMS.map((item) => (
          <Col xs={24} sm={12} xl={6} key={item.valueKey}>
            <Card loading={isLoading}>
              <Statistic
                title={item.title}
                value={data?.[item.valueKey] ?? 0}
                prefix={<span style={{ color: item.color, marginRight: 8 }}>{item.icon}</span>}
              />
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
}
