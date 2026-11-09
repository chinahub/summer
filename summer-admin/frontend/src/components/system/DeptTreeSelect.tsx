import { TreeSelect } from 'antd';
import { buildDeptTree, type DeptVo } from '@/api/system/dept';

interface DeptTreeSelectProps {
  depts: DeptVo[];
  value?: number;
  onChange?: (value?: number) => void;
  placeholder?: string;
  disabled?: boolean;
  /** 编辑时排除的部门子树（避免把部门挂到自己或后代下） */
  excludeDeptIds?: Set<number>;
}

/** 部门树选择器：扁平部门列表实时组装为树，支持单选与禁用 */
export default function DeptTreeSelect({
  depts,
  value,
  onChange,
  placeholder = '请选择归属部门',
  disabled,
  excludeDeptIds,
}: DeptTreeSelectProps) {
  const treeData = buildDeptTree(
    excludeDeptIds ? depts.filter((d) => !excludeDeptIds.has(d.deptId)) : depts,
  );
  return (
    <TreeSelect
      treeData={treeData}
      value={value}
      onChange={(next) => onChange?.(next as number | undefined)}
      placeholder={placeholder}
      disabled={disabled}
      allowClear
      treeDefaultExpandAll
      styles={{ popup: { root: { maxHeight: 400, overflow: 'auto' } } }}
    />
  );
}
