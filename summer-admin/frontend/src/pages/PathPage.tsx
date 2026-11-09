import { Suspense, useMemo } from 'react';
import { Spin } from 'antd';
import { resolveCurrentView } from '@/pages/pageResolver';
import { resolvePage } from '@/pages/pageRegistry';
import { usePermissionStore } from '@/stores/permissionStore';

/** 按路径渲染白名单页面（KeepAlive 对每个页签各渲染一份） */
export default function PathPage({ pathname }: { pathname: string }) {
  const routes = usePermissionStore((state) => state.routes);
  const view = useMemo(() => resolveCurrentView(pathname, routes), [pathname, routes]);
  const PageComponent = resolvePage(view.kind === 'page' ? view.componentKey : null);
  return (
    <Suspense fallback={<Spin style={{ display: 'block', margin: '120px auto' }} />}>
      <PageComponent />
    </Suspense>
  );
}
