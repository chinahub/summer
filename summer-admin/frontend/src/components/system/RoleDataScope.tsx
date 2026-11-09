import { App as AntdApp, Modal, Radio, Tree } from 'antd';
import type { TreeProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { buildDeptTree, type DeptVo } from '@/api/system/dept';
import {
  DATA_SCOPE_OPTIONS,
  dataScopeEnumFromCode,
  type DataScopeTypeName,
  type RoleVo,
} from '@/api/system/role';

interface RoleDataScopeProps {
  open: boolean;
  role: RoleVo | null;
  depts: DeptVo[];
  /** 角色 CUSTOM 范围当前已选部门 ID（编辑弹窗回填勾选） */
  initialDeptIds?: number[];
  submitting: boolean;
  onCancel: () => void;
  /** 提交数据范围枚举名与自定义部门集合（CUSTOM 时 deptIds 非空） */
  onSubmit: (scopeType: DataScopeTypeName, deptIds: number[]) => void;
}

/** 角色数据范围弹窗：五种范围单选，CUSTOM 展开部门树多选（必选校验） */
export default function RoleDataScope({
  open,
  role,
  depts,
  initialDeptIds,
  submitting,
  onCancel,
  onSubmit,
}: RoleDataScopeProps) {
  const { message } = AntdApp.useApp();
  const [scopeType, setScopeType] = useState<DataScopeTypeName>('ALL');
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const deptTree = useMemo(() => buildDeptTree(depts), [depts]);

  // 打开时按角色当前数据范围编码回填，并回填 CUSTOM 已选部门
  useEffect(() => {
    if (!open) {
      return;
    }
    setScopeType(dataScopeEnumFromCode(role?.dataScope) ?? 'ALL');
    setCheckedKeys(initialDeptIds ?? []);
  }, [open, role, initialDeptIds]);

  const onCheck: TreeProps['onCheck'] = (keys) => {
    setCheckedKeys(Array.isArray(keys) ? keys : keys.checked);
  };

  function handleOk() {
    const deptIds = checkedKeys.map(Number);
    if (scopeType === 'CUSTOM' && deptIds.length === 0) {
      message.warning('请选择自定义数据权限的部门');
      return;
    }
    onSubmit(scopeType, deptIds);
  }

  return (
    <Modal
      title={`分配数据权限${role?.roleName ? `：${role.roleName}` : ''}`}
      open={open}
      onCancel={onCancel}
      confirmLoading={submitting}
      onOk={handleOk}
      width={520}
      destroyOnHidden
    >
      <Radio.Group
        value={scopeType}
        onChange={(e) => setScopeType(e.target.value as DataScopeTypeName)}
        style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}
      >
        {DATA_SCOPE_OPTIONS.map((option) => (
          <Radio key={option.value} value={option.value}>
            {option.label}
          </Radio>
        ))}
      </Radio.Group>
      {scopeType === 'CUSTOM' && (
        <Tree
          checkable
          selectable={false}
          defaultExpandAll
          treeData={deptTree}
          checkedKeys={checkedKeys}
          onCheck={onCheck}
          height={320}
        />
      )}
    </Modal>
  );
}
