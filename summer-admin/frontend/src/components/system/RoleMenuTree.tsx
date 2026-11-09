import { Modal, Tree } from 'antd';
import type { TreeProps } from 'antd';
import { useEffect, useState } from 'react';
import { buildMenuTree, type MenuVo } from '@/api/system/menu';

interface RoleMenuTreeProps {
  open: boolean;
  roleName?: string;
  menus: MenuVo[];
  /** 角色当前已授权的菜单 ID（编辑弹窗回填勾选） */
  initialCheckedKeys?: number[];
  submitting: boolean;
  onCancel: () => void;
  /** 提交勾选的菜单 ID 集合（后端整体替换授权） */
  onSubmit: (menuIds: number[]) => void;
}

/** 角色菜单授权弹窗：可勾选菜单树，提交整体替换该角色的菜单授权 */
export default function RoleMenuTree({
  open,
  roleName,
  menus,
  initialCheckedKeys,
  submitting,
  onCancel,
  onSubmit,
}: RoleMenuTreeProps) {
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);

  // 打开时回填角色当前已授权菜单（无初始值时从空开始）
  useEffect(() => {
    if (open) {
      setCheckedKeys(initialCheckedKeys ?? []);
    }
  }, [open, initialCheckedKeys]);

  const onCheck: TreeProps['onCheck'] = (keys) => {
    setCheckedKeys(Array.isArray(keys) ? keys : keys.checked);
  };

  return (
    <Modal
      title={`分配菜单权限${roleName ? `：${roleName}` : ''}`}
      open={open}
      onCancel={onCancel}
      confirmLoading={submitting}
      onOk={() => onSubmit(checkedKeys.map(Number))}
      width={480}
      destroyOnHidden
    >
      <Tree
        checkable
        selectable={false}
        defaultExpandAll
        treeData={buildMenuTree(menus)}
        checkedKeys={checkedKeys}
        onCheck={onCheck}
        height={420}
      />
    </Modal>
  );
}
