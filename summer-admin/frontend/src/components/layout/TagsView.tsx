import { Dropdown, Tabs } from 'antd';
import type { MenuProps } from 'antd';
import { history, useLocation } from '@umijs/max';
import { useCallback } from 'react';
import { useTagsViewStore, type TagView } from '@/stores/tagsViewStore';

/** 关闭当前标签：返回应激活的相邻标签并跳转（关闭激活标签时） */
function closeTab(path: string, activePath: string): void {
  const next = useTagsViewStore.getState().closeView(path);
  if (path === activePath && next) {
    history.push(next.path);
  }
}

/** 多标签页：已访问页签的展示、切换与右键操作（刷新/关闭/关闭其他/全部关闭） */
export default function TagsView() {
  const location = useLocation();
  const visitedViews = useTagsViewStore((state) => state.visitedViews);
  const refreshView = useTagsViewStore((state) => state.refreshView);
  const delOthers = useTagsViewStore((state) => state.delOthers);
  const delAll = useTagsViewStore((state) => state.delAll);

  const activePath = location.pathname;

  const buildMenuItems = useCallback(
    (view: TagView): MenuProps['items'] => [
      { key: 'refresh', label: '刷新' },
      { key: 'close', label: '关闭当前', disabled: !!view.affix },
      { key: 'closeOthers', label: '关闭其他' },
      { key: 'closeAll', label: '全部关闭' },
    ],
    [],
  );

  const handleMenuClick = useCallback(
    (view: TagView) => (info: { key: string }) => {
      switch (info.key) {
        case 'refresh':
          refreshView(view.path);
          break;
        case 'close':
          closeTab(view.path, activePath);
          break;
        case 'closeOthers':
          delOthers(view.path);
          if (activePath !== view.path) {
            history.push(view.path);
          }
          break;
        case 'closeAll':
          delAll();
          history.push('/');
          break;
      }
    },
    [activePath, refreshView, delOthers, delAll],
  );

  const items = visitedViews.map((view) => ({
    key: view.path,
    closable: !view.affix,
    label: (
      <Dropdown
        menu={{ items: buildMenuItems(view), onClick: handleMenuClick(view) }}
        trigger={['contextMenu']}
      >
        <span>{view.title ?? view.path}</span>
      </Dropdown>
    ),
  }));

  return (
    <Tabs
      type="editable-card"
      size="small"
      hideAdd
      activeKey={activePath}
      items={items}
      onChange={(key) => history.push(key)}
      onEdit={(key, action) => {
        if (action === 'remove') {
          closeTab(String(key), activePath);
        }
      }}
      style={{ margin: '0 12px' }}
    />
  );
}
