import { useEffect, useMemo } from 'react';
import { history, useLocation } from '@umijs/max';
import ForbiddenPage from '@/pages/error/403';
import { resolveCurrentView } from '@/pages/pageResolver';
import KeepAliveTabs from '@/components/layout/KeepAliveTabs';
import { usePermissionStore } from '@/stores/permissionStore';
import { useTagsViewStore } from '@/stores/tagsViewStore';
import { useUserStore } from '@/stores/userStore';

/** 动态页：解析当前路由，管理页签与目录重定向；页面内容由 KeepAliveTabs 渲染 */
export default function DynamicPage() {
  const location = useLocation();
  const routes = usePermissionStore((state) => state.routes);
  const token = useUserStore((state) => state.token);
  const addView = useTagsViewStore((state) => state.addView);

  const pathname = location.pathname;
  const view = useMemo(() => resolveCurrentView(pathname, routes), [pathname, routes]);

  // 目录直达重定向到第一个可见子菜单
  useEffect(() => {
    if (view.kind === 'redirect' && view.to !== pathname) {
      history.replace(view.to);
    }
  }, [view, pathname]);

  // 已打开的页签进入标签栏（首页固定不可关闭）
  useEffect(() => {
    if (token && view.kind === 'page') {
      addView({
        path: pathname,
        fullPath: pathname,
        title: view.title ?? pathname,
        affix: pathname === '/',
      });
    }
  }, [token, pathname, view, addView]);

  if (view.kind === 'forbidden') {
    return <ForbiddenPage />;
  }
  return <KeepAliveTabs />;
}
