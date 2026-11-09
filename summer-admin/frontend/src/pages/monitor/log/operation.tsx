import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useMutation } from '@tanstack/react-query';
import { App as AntdApp, Button, Descriptions, Drawer, Popconfirm, Space, Tag } from 'antd';
import { useRef, useState } from 'react';
import {
  clearOperLogsApi,
  deleteOperLogsApi,
  getOperLogApi,
  listOperLogsApi,
  type OperLogVo,
} from '@/api/system/log';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

/** 业务类型展示：与后端 OperationLogAspect 动作映射一致（0其它 1新增 2修改 3删除 4导出 5导入 6强退 7清空） */
const BUSINESS_TYPE_ENUM: Record<number, string> = {
  0: '其它',
  1: '新增',
  2: '修改',
  3: '删除',
  4: '导出',
  5: '导入',
  6: '强退',
  7: '清空',
};

/**
 * 操作日志：条件分页、详情、批量删除与清空。
 * 列表 monitor:log:list；详情 monitor:log:query；删除/清空 monitor:log:remove。
 * 敏感字段只展示后端脱敏后的返回值，前端不做额外处理。
 */
export default function OperationLogPage() {
  const { message } = AntdApp.useApp();
  const actionRef = useRef<ActionType>(undefined);
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);

  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [detail, setDetail] = useState<OperLogVo | null>(null);

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const deleteMutation = useMutation({
    mutationFn: deleteOperLogsApi,
    onSuccess: () => {
      message.success('操作日志删除成功');
      setSelectedIds([]);
      // ProTable 数据自管理：必须显式 reload 才能刷新列表
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '操作日志删除失败'),
  });

  const clearMutation = useMutation({
    mutationFn: clearOperLogsApi,
    onSuccess: () => {
      message.success('操作日志已清空');
      setSelectedIds([]);
      actionRef.current?.reload();
    },
    onError: (error) => onError(error, '操作日志清空失败'),
  });

  const detailMutation = useMutation({
    mutationFn: getOperLogApi,
    onSuccess: (data) => setDetail(data),
    onError: (error) => onError(error, '操作日志详情加载失败'),
  });

  const columns: ProColumns<OperLogVo>[] = [
    { title: '日志编号', dataIndex: 'operId', width: 90, search: false },
    { title: '操作模块', dataIndex: 'title', width: 140 },
    {
      title: '业务类型',
      dataIndex: 'businessType',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(BUSINESS_TYPE_ENUM).map(([value, text]) => [value, { text }]),
      ),
      render: (_, row) => (row.businessType !== undefined ? (BUSINESS_TYPE_ENUM[row.businessType] ?? row.businessType) : null),
    },
    { title: '操作人员', dataIndex: 'operName', width: 120 },
    { title: '操作IP', dataIndex: 'operIp', width: 130, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueType: 'select',
      valueEnum: {
        0: { text: '成功' },
        1: { text: '失败' },
      },
      render: (_, row) =>
        row.status === undefined ? null : (
          <Tag color={row.status === 0 ? 'success' : 'error'}>{row.status === 0 ? '成功' : '失败'}</Tag>
        ),
    },
    { title: '操作时间', dataIndex: 'operTime', width: 170, search: false },
    { title: '耗时(ms)', dataIndex: 'costTime', width: 90, search: false },
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
            loading={detailMutation.isPending && detailMutation.variables === row.operId}
            onClick={() => detailMutation.mutate(row.operId)}
          >
            详情
          </Button>
        ) : null,
      ],
    },
  ];

  return (
    <>
      <ProTable<OperLogVo>
        headerTitle="操作日志列表"
        rowKey="operId"
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
                  title={`确认删除选中的 ${selectedIds.length} 条操作日志？`}
                  disabled={selectedIds.length === 0}
                  onConfirm={() => deleteMutation.mutate(selectedIds.join(','))}
                >
                  <Button danger disabled={selectedIds.length === 0} loading={deleteMutation.isPending}>
                    删除
                  </Button>
                </Popconfirm>,
                <Popconfirm
                  key="clear"
                  title="确认清空所有操作日志？该操作不可恢复，并将留下一条清空审计事件。"
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
          const data = await listOperLogsApi({
            pageNum: params.current ?? 1,
            pageSize: params.pageSize ?? 10,
            title: params.title,
            operName: params.operName,
            businessType: params.businessType !== undefined ? Number(params.businessType) : undefined,
            status: params.status !== undefined ? Number(params.status) : undefined,
          });
          return { data: data.rows, total: data.total, success: true };
        }}
      />
      <Drawer
        title="操作日志详情"
        open={detail !== null}
        onClose={() => setDetail(null)}
        width={560}
      >
        {detail && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="日志编号">{detail.operId}</Descriptions.Item>
            <Descriptions.Item label="操作模块">{detail.title}</Descriptions.Item>
            <Descriptions.Item label="业务类型">
              {detail.businessType !== undefined ? (BUSINESS_TYPE_ENUM[detail.businessType] ?? detail.businessType) : null}
            </Descriptions.Item>
            <Descriptions.Item label="请求方式">
              <Space>
                {detail.requestMethod}
                {detail.operUrl}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="调用方法">{detail.method}</Descriptions.Item>
            <Descriptions.Item label="操作人员">{detail.operName}</Descriptions.Item>
            <Descriptions.Item label="所属部门">{detail.deptName}</Descriptions.Item>
            <Descriptions.Item label="操作IP">{detail.operIp}</Descriptions.Item>
            <Descriptions.Item label="操作地点">{detail.operLocation}</Descriptions.Item>
            <Descriptions.Item label="状态">
              {detail.status === 0 ? '成功' : detail.status === 1 ? '失败' : detail.status}
            </Descriptions.Item>
            <Descriptions.Item label="错误消息">{detail.errorMsg}</Descriptions.Item>
            <Descriptions.Item label="操作时间">{detail.operTime}</Descriptions.Item>
            <Descriptions.Item label="耗时(ms)">{detail.costTime}</Descriptions.Item>
            <Descriptions.Item label="请求参数">
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{detail.operParam}</pre>
            </Descriptions.Item>
            <Descriptions.Item label="返回参数">
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{detail.jsonResult}</pre>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </>
  );
}
