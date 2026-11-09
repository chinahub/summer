import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useMutation } from '@tanstack/react-query';
import { App as AntdApp, Button, Descriptions, Drawer, Popconfirm, Tag } from 'antd';
import { useRef, useState } from 'react';
import {
  clearLoginLogsApi,
  deleteLoginLogsApi,
  getLoginLogApi,
  listLoginLogsApi,
  type LoginLogVo,
} from '@/api/system/log';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

/**
 * 登录日志：条件分页、详情、批量删除与清空。
 * 列表 monitor:log:list；详情 monitor:log:query；删除/清空 monitor:log:remove。
 * 敏感字段只展示后端脱敏后的返回值，前端不做额外处理。
 */
export default function LoginLogPage() {
  const { message } = AntdApp.useApp();
  const actionRef = useRef<ActionType>(undefined);
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);

  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [detail, setDetail] = useState<LoginLogVo | null>(null);

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const deleteMutation = useMutation({
    mutationFn: deleteLoginLogsApi,
    onSuccess: () => {
      message.success('登录日志删除成功');
      setSelectedIds([]);
      // ProTable 数据自管理：必须显式 reload 才能刷新列表
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '登录日志删除失败'),
  });

  const clearMutation = useMutation({
    mutationFn: clearLoginLogsApi,
    onSuccess: () => {
      message.success('登录日志已清空');
      setSelectedIds([]);
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '登录日志清空失败'),
  });

  const detailMutation = useMutation({
    mutationFn: getLoginLogApi,
    onSuccess: (data) => setDetail(data),
    onError: (error) => onError(error, '登录日志详情加载失败'),
  });

  const columns: ProColumns<LoginLogVo>[] = [
    { title: '访问编号', dataIndex: 'infoId', width: 90, search: false },
    { title: '用户账号', dataIndex: 'userName', width: 130 },
    { title: '登录IP', dataIndex: 'ipaddr', width: 130 },
    { title: '登录地点', dataIndex: 'loginLocation', width: 120, search: false },
    { title: '浏览器', dataIndex: 'browser', width: 120, search: false },
    { title: '操作系统', dataIndex: 'os', width: 120, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueType: 'select',
      valueEnum: {
        '0': { text: '成功' },
        '1': { text: '失败' },
      },
      render: (_, row) =>
        row.status === undefined ? null : (
          <Tag color={row.status === '0' ? 'success' : 'error'}>
            {row.status === '0' ? '成功' : '失败'}
          </Tag>
        ),
    },
    { title: '描述', dataIndex: 'msg', width: 140, search: false, ellipsis: true },
    { title: '登录时间', dataIndex: 'loginTime', width: 170, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 90,
      render: (_, row) => [
        can('monitor:log:query') ? (
          <Button
            key="detail"
            type="link"
            size="small"
            loading={detailMutation.isPending && detailMutation.variables === row.infoId}
            onClick={() => detailMutation.mutate(row.infoId)}
          >
            详情
          </Button>
        ) : null,
      ],
    },
  ];

  return (
    <>
      <ProTable<LoginLogVo>
        headerTitle="登录日志列表"
        rowKey="infoId"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 90 }}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        rowSelection={
          can('monitor:log:remove')
            ? {
                selectedRowKeys: selectedIds,
                onChange: (keys) => setSelectedIds(keys.map(Number)),
              }
            : undefined
        }
        tableAlertOptionRender={false}
        toolBarRender={() =>
          can('monitor:log:remove')
            ? [
                <Popconfirm
                  key="delete"
                  title={`确认删除选中的 ${selectedIds.length} 条登录日志？`}
                  disabled={selectedIds.length === 0}
                  onConfirm={() => deleteMutation.mutate(selectedIds.join(','))}
                >
                  <Button danger disabled={selectedIds.length === 0} loading={deleteMutation.isPending}>
                    删除
                  </Button>
                </Popconfirm>,
                <Popconfirm
                  key="clear"
                  title="确认清空所有登录日志？该操作不可恢复，并会在操作日志留下一条清空审计事件。"
                  onConfirm={() => clearMutation.mutate()}
                >
                  <Button danger loading={clearMutation.isPending}>
                    清空
                  </Button>
                </Popconfirm>,
              ]
            : []
        }
        request={async (params) => {
          const data = await listLoginLogsApi({
            pageNum: params.current ?? 1,
            pageSize: params.pageSize ?? 10,
            userName: params.userName,
            ipaddr: params.ipaddr,
            status: params.status,
          });
          return { data: data.rows, total: data.total, success: true };
        }}
      />
      <Drawer title="登录日志详情" open={detail !== null} onClose={() => setDetail(null)} width={480}>
        {detail && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="访问编号">{detail.infoId}</Descriptions.Item>
            <Descriptions.Item label="用户账号">{detail.userName}</Descriptions.Item>
            <Descriptions.Item label="登录IP">{detail.ipaddr}</Descriptions.Item>
            <Descriptions.Item label="登录地点">{detail.loginLocation}</Descriptions.Item>
            <Descriptions.Item label="浏览器">{detail.browser}</Descriptions.Item>
            <Descriptions.Item label="操作系统">{detail.os}</Descriptions.Item>
            <Descriptions.Item label="状态">
              {detail.status === '0' ? '成功' : detail.status === '1' ? '失败' : detail.status}
            </Descriptions.Item>
            <Descriptions.Item label="描述">{detail.msg}</Descriptions.Item>
            <Descriptions.Item label="登录时间">{detail.loginTime}</Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </>
  );
}
