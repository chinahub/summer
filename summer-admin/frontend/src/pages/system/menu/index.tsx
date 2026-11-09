import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Space,
  Table,
  Tag,
  TreeSelect,
} from 'antd';
import type { FormInstance, TableProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  buildMenuTree,
  createMenuApi,
  deleteMenuApi,
  listMenuApi,
  updateMenuApi,
  type MenuPayload,
  type MenuVo,
} from '@/api/system/menu';
import { useUserStore } from '@/stores/userStore';
import { hasPermission } from '@/utils/permission';

/** 菜单表单值：新增/修改共用，parentId 通过树选择器选择 */
interface MenuFormValues {
  parentId: number;
  menuType: string;
  menuName: string;
  orderNum?: number;
  path?: string;
  component?: string;
  perms?: string;
  icon?: string;
  isFrame?: number;
  isCache?: number;
  visible?: string;
  status?: string;
  query?: string;
}

const MENU_TYPE_TAG: Record<string, { color: string; text: string }> = {
  M: { color: 'blue', text: '目录' },
  C: { color: 'green', text: '菜单' },
  F: { color: 'orange', text: '按钮' },
};

/** 收集菜单自身及后代 ID：编辑时禁止把菜单挂到自己或后代下 */
function collectMenuAndChildren(menus: MenuVo[], menuId: number): Set<number> {
  const childrenOf = new Map<number, number[]>();
  for (const menu of menus) {
    const siblings = childrenOf.get(menu.parentId);
    if (siblings) {
      siblings.push(menu.menuId);
    } else {
      childrenOf.set(menu.parentId, [menu.menuId]);
    }
  }
  const result = new Set<number>();
  const stack = [menuId];
  while (stack.length > 0) {
    const current = stack.pop()!;
    if (result.has(current)) {
      continue;
    }
    result.add(current);
    for (const child of childrenOf.get(current) ?? []) {
      stack.push(child);
    }
  }
  return result;
}

/** 将扁平列表转为树表格行：基于 parentId 挂 children，孤儿节点挂根；复制行对象避免污染查询缓存 */
function attachRows(menus: MenuVo[], treeData: ReturnType<typeof buildMenuTree>): MenuVo[] {
  const copies = new Map(menus.map((m) => [m.menuId, { ...m }]));
  const toRow = (node: (typeof treeData)[number]): MenuVo => {
    const menu = copies.get(node.key)!;
    return { ...menu, children: node.children?.map(toRow) };
  };
  return treeData.map(toRow);
}

/** 菜单表单主体：类型联动校验（按钮无需路由/组件，菜单必填组件） */
function MenuFormContent({
  form,
  treeData,
  excludeIds,
}: {
  form: FormInstance<MenuFormValues>;
  treeData: ReturnType<typeof buildMenuTree>;
  excludeIds: Set<number>;
}) {
  const menuType = Form.useWatch('menuType', form);
  return (
    <Form<MenuFormValues> form={form} layout="vertical">
      <Form.Item label="上级菜单" name="parentId" rules={[{ required: true, message: '请选择上级菜单' }]}>
        <TreeSelect
          treeData={[
            { key: 0, value: 0, title: '根目录', children: treeData.filter((n) => !excludeIds.has(n.key)) },
          ]}
          treeDefaultExpandAll
          styles={{ popup: { root: { maxHeight: 300, overflow: 'auto' } } }}
        />
      </Form.Item>
      <Form.Item label="菜单类型" name="menuType" rules={[{ required: true, message: '请选择菜单类型' }]}>
        <Radio.Group>
          <Radio value="M">目录</Radio>
          <Radio value="C">菜单</Radio>
          <Radio value="F">按钮</Radio>
        </Radio.Group>
      </Form.Item>
      <Form.Item
        label="菜单名称"
        name="menuName"
        rules={[{ required: true, message: '请输入菜单名称' }]}
      >
        <Input maxLength={50} placeholder="请输入菜单名称" />
      </Form.Item>
      <Form.Item label="显示排序" name="orderNum" rules={[{ required: true, message: '请输入显示排序' }]}>
        <InputNumber min={0} max={9999} style={{ width: '100%' }} />
      </Form.Item>
      {menuType !== 'F' && (
        <>
          <Form.Item
            label="路由地址"
            name="path"
            rules={[{ required: true, message: '请输入路由地址' }]}
          >
            <Input maxLength={200} placeholder="请输入路由地址，如 user" />
          </Form.Item>
          <Form.Item
            label="组件路径"
            name="component"
            rules={[{ required: menuType === 'C', message: '请输入组件路径' }]}
          >
            <Input maxLength={255} placeholder="请输入组件路径，如 system/user/index" />
          </Form.Item>
          <Form.Item label="图标" name="icon">
            <Input maxLength={100} placeholder="请输入图标名，如 user" />
          </Form.Item>
        </>
      )}
      <Form.Item label="权限标识" name="perms">
        <Input maxLength={100} placeholder="请输入权限标识，如 system:user:list" />
      </Form.Item>
      <Form.Item label="路由参数" name="query">
        <Input maxLength={255} placeholder="请输入路由参数" />
      </Form.Item>
      <Form.Item label="是否外链" name="isFrame" hidden={menuType === 'F'}>
        <Radio.Group>
          <Radio value={1}>否</Radio>
          <Radio value={0}>是</Radio>
        </Radio.Group>
      </Form.Item>
      <Form.Item label="是否缓存" name="isCache" hidden={menuType === 'F'}>
        <Radio.Group>
          <Radio value={0}>缓存</Radio>
          <Radio value={1}>不缓存</Radio>
        </Radio.Group>
      </Form.Item>
      <Form.Item label="显示状态" name="visible" hidden={menuType === 'F'}>
        <Radio.Group>
          <Radio value="0">显示</Radio>
          <Radio value="1">隐藏</Radio>
        </Radio.Group>
      </Form.Item>
      <Form.Item label="菜单状态" name="status">
        <Radio.Group>
          <Radio value="0">正常</Radio>
          <Radio value="1">停用</Radio>
        </Radio.Group>
      </Form.Item>
    </Form>
  );
}

/** 菜单管理：树表格展示目录/菜单/按钮，支持新增子级、修改与删除 */
export default function MenuPage() {
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const permissions = useUserStore((state) => state.userInfo?.permissions ?? []);
  const can = (perm: string) => hasPermission(perm, permissions);
  const [form] = Form.useForm<MenuFormValues>();

  const [editingMenu, setEditingMenu] = useState<MenuVo | null>(null);
  const [formOpen, setFormOpen] = useState(false);

  const { data: menus = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['system-menus-all'],
    queryFn: listMenuApi,
    retry: 1,
  });

  const treeData = useMemo(() => buildMenuTree(menus), [menus]);
  const excludeIds = useMemo(
    () => (editingMenu ? collectMenuAndChildren(menus, editingMenu.menuId) : new Set<number>()),
    [menus, editingMenu],
  );

  // 打开弹窗时按菜单回填；新增子级时预填父级与类型
  useEffect(() => {
    if (!formOpen) {
      return;
    }
    if (editingMenu) {
      form.setFieldsValue({
        parentId: editingMenu.parentId,
        menuType: editingMenu.menuType,
        menuName: editingMenu.menuName,
        orderNum: editingMenu.orderNum,
        path: editingMenu.path,
        component: editingMenu.component,
        perms: editingMenu.perms,
        icon: editingMenu.icon,
        isFrame: editingMenu.isFrame ?? 1,
        isCache: editingMenu.isCache ?? 0,
        visible: editingMenu.visible ?? '0',
        status: editingMenu.status ?? '0',
        query: editingMenu.query,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ parentId: 0, menuType: 'C', orderNum: 0, isFrame: 1, isCache: 0, visible: '0', status: '0' });
    }
  }, [formOpen, editingMenu, form]);

  function onError(error: unknown, fallback: string) {
    message.error(error instanceof Error ? error.message : fallback);
  }

  const saveMutation = useMutation({
    mutationFn: async (values: MenuPayload) => {
      if (values.menuId !== undefined) {
        await updateMenuApi(values);
      } else {
        await createMenuApi(values);
      }
    },
    onSuccess: (_data, values) => {
      message.success(values.menuId ? '菜单修改成功' : '菜单新增成功');
      setFormOpen(false);
      setEditingMenu(null);
      queryClient.invalidateQueries({ queryKey: ['system-menus-all'] });
    },
    onError: (error) => onError(error, '菜单保存失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteMenuApi,
    onSuccess: () => {
      message.success('菜单删除成功');
      queryClient.invalidateQueries({ queryKey: ['system-menus-all'] });
    },
    onError: (error) => onError(error, '菜单删除失败'),
  });

  function openForm(menu: MenuVo | null, presetParentId?: number) {
    setEditingMenu(menu);
    setFormOpen(true);
    if (!menu && presetParentId !== undefined) {
      // 等 Modal 挂载后设置新增子级的预填值
      setTimeout(() => form.setFieldsValue({ parentId: presetParentId, menuType: 'C' }));
    }
  }

  const columns: NonNullable<TableProps<MenuVo>['columns']> = [
    { title: '菜单名称', dataIndex: 'menuName' },
    { title: '排序', dataIndex: 'orderNum', width: 70 },
    {
      title: '权限标识',
      dataIndex: 'perms',
      width: 180,
      render: (value: string | undefined) => value || '-',
    },
    { title: '组件路径', dataIndex: 'component', width: 180, render: (v: string | undefined) => v || '-' },
    { title: '路由地址', dataIndex: 'path', width: 120, render: (v: string | undefined) => v || '-' },
    {
      title: '类型',
      dataIndex: 'menuType',
      width: 80,
      render: (value: string) => {
        const tag = MENU_TYPE_TAG[value] ?? { color: 'default', text: value };
        return <Tag color={tag.color}>{tag.text}</Tag>;
      },
    },
    {
      title: '可见',
      dataIndex: 'visible',
      width: 70,
      render: (value: string | undefined) => (value === '1' ? <Tag>隐藏</Tag> : <Tag color="success">显示</Tag>),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 70,
      render: (value: string | undefined) =>
        value === '1' ? <Tag color="error">停用</Tag> : <Tag color="success">正常</Tag>,
    },
    {
      title: '操作',
      width: 200,
      render: (_, row) => (
        <Space size={4} wrap>
          {can('system:menu:add') && row.menuType !== 'F' && (
            <Button type="link" size="small" onClick={() => openForm(null, row.menuId)}>
              新增
            </Button>
          )}
          {can('system:menu:edit') && (
            <Button type="link" size="small" onClick={() => openForm(row)}>
              修改
            </Button>
          )}
          {can('system:menu:remove') && (
            <Popconfirm title={`确认删除菜单「${row.menuName}」？`} onConfirm={() => deleteMutation.mutate(row.menuId)}>
              <Button type="link" size="small" danger loading={deleteMutation.isPending}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="菜单列表"
      extra={
        can('system:menu:add') ? (
          <Button type="primary" onClick={() => openForm(null)}>
            新增菜单
          </Button>
        ) : null
      }
    >
      <Table<MenuVo>
        rowKey="menuId"
        loading={isLoading}
        columns={columns}
        dataSource={treeData.length > 0 ? attachRows(menus, treeData) : menus}
        expandable={{ defaultExpandAllRows: true }}
        pagination={false}
      />
      {isError && (
        <Button type="primary" onClick={() => refetch().catch(() => message.error('菜单加载失败'))}>
          重新加载
        </Button>
      )}
      <Modal
        title={editingMenu ? '修改菜单' : '新增菜单'}
        open={formOpen}
        width={640}
        onCancel={() => {
          setFormOpen(false);
          setEditingMenu(null);
        }}
        confirmLoading={saveMutation.isPending}
        onOk={() => {
          form.validateFields().then((values) => {
            saveMutation.mutate({ ...editingMenu, ...values });
          });
        }}
        afterClose={() => form.resetFields()}
        destroyOnHidden
      >
        <MenuFormContent form={form} treeData={treeData} excludeIds={excludeIds} />
      </Modal>
    </Card>
  );
}
