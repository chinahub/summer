import { useLocation } from '@umijs/max';
import PathPage from '@/pages/PathPage';
import { useTagsViewStore } from '@/stores/tagsViewStore';

/**
 * 页签保持存活：为每个已访问页签渲染一份页面组件，
 * 非激活页签以 display:none 隐藏（组件不卸载、状态保留），
 * 刷新操作通过 key 追加重挂载计数强制对应页签重新挂载。
 */
export default function KeepAliveTabs() {
  const location = useLocation();
  const visitedViews = useTagsViewStore((state) => state.visitedViews);
  const refreshTicks = useTagsViewStore((state) => state.refreshTicks);

  const activePath = location.pathname;
  // 当前页签入栈前也要渲染，避免首帧空白
  const views = visitedViews.some((view) => view.path === activePath)
    ? visitedViews
    : [...visitedViews, { path: activePath }];

  return (
    <div>
      {views.map((view) => (
        <div
          key={`${view.path}:${refreshTicks[view.path] ?? 0}`}
          style={{ display: view.path === activePath ? 'block' : 'none' }}
        >
          <PathPage pathname={view.path} />
        </div>
      ))}
    </div>
  );
}
