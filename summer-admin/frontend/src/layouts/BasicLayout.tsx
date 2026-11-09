import {
  BookOutlined,
  EditOutlined,
  FileTextOutlined,
  FormOutlined,
  HomeOutlined,
  IdcardOutlined,
  KeyOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  NotificationOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { App as AntdApp, Avatar, Breadcrumb, Dropdown, Layout, Menu, Spin } from 'antd';
import type { MenuProps } from 'antd';
import { history, useLocation } from '@umijs/max';
import { useEffect, useMemo } from 'react';
import type { ReactNode } from 'react';
import LayoutSettings from '@/components/layout/LayoutSettings';
import Screenfull from '@/components/layout/Screenfull';
import TagsView from '@/components/layout/TagsView';
import { logoutApi, routersApi, userinfoApi } from '@/api/login';
import DynamicPage from '@/pages/dynamicPage';
import { useAppStore } from '@/stores/appStore';
import { usePermissionStore } from '@/stores/permissionStore';
import { useTagsViewStore } from '@/stores/tagsViewStore';
import { useUserStore } from '@/stores/userStore';
import type { RouterVo } from '@/api/types';
import {
  filterAuthorizedMenus,
  filterEmptyDirectories,
  flattenRoutes,
  isExternalPath,
  isSafeExternalUrl,
  joinRoutePath,
} from '@/utils/menu';

const { Header, Sider, Content } = Layout;

type MenuItem = Required<MenuProps>['items'][number];

/** 后端菜单 icon 字符串到 antd 图标的映射，未知图标不渲染 */
const MENU_ICONS: Record<string, ReactNode> = {
  home: <HomeOutlined />,
  setting: <SettingOutlined />,
  user: <UserOutlined />,
  peoples: <TeamOutlined />,
  tree: <FormOutlined />,
  post: <IdcardOutlined />,
  dict: <BookOutlined />,
  edit: <EditOutlined />,
  message: <NotificationOutlined />,
  log: <FileTextOutlined />,
  form: <FormOutlined />,
  key: <KeyOutlined />,
};

/** 将授权路由树转换为 antd Menu 节点；外部链接校验协议，空目录与隐藏菜单剔除 */
function buildMenuItems(routes: RouterVo[], parentPath = ''): MenuItem[] {
  const items: MenuItem[] = [];
  for (const route of routes) {
    if (route.hidden) {
      continue;
    }
    if (isExternalPath(route.path)) {
      // 外部链接仅放行安全协议，危险协议（javascript:/data: 等）直接剔除
      if (isSafeExternalUrl(route.path)) {
        items.push({
          key: route.path,
          icon: MENU_ICONS[route.meta?.icon ?? ''],
          label: (
            <a href={route.path} target="_blank" rel="noreferrer">
              {route.meta?.title ?? route.path}
            </a>
          ),
        });
      }
      continue;
    }
    const fullPath = parentPath ? joinRoutePath(parentPath, route.path) : route.path;
    const children = route.children?.length
      ? buildMenuItems(route.children, fullPath)
      : [];
    items.push({
      key: fullPath,
      icon: MENU_ICONS[route.meta?.icon ?? ''],
      label: route.meta?.title ?? route.path,
      ...(children.length ? { children } : {}),
    });
  }
  return items;
}

/** 计算当前路径应展开的目录祖先键，如 /system/user → ['/system'] */
function computeOpenKeys(routes: RouterVo[], pathname: string): string[] {
  const flat = flattenRoutes(routes);
  const prefixes = pathname
    .split('/')
    .filter(Boolean)
    .reduce<string[]>((acc, segment) => {
      acc.push(`${acc[acc.length - 1] ?? ''}/${segment}`);
      return acc;
    }, []);
  return prefixes.filter((prefix) =>
    flat.some((item) => item.fullPath === prefix && !!item.route.children?.length),
  );
}

/** 计算面包屑：首页 + 当前路径命中的各级菜单标题 */
function computeBreadcrumb(routes: RouterVo[], pathname: string): string[] {
  if (pathname === '/') {
    return ['首页'];
  }
  const flat = flattenRoutes(routes);
  const prefixes = pathname
    .split('/')
    .filter(Boolean)
    .reduce<string[]>((acc, segment) => {
      acc.push(`${acc[acc.length - 1] ?? ''}/${segment}`);
      return acc;
    }, []);
  const titles = ['首页'];
  for (const prefix of prefixes) {
    const hit = flat.find((item) => item.fullPath === prefix);
    if (hit?.route.meta?.title) {
      titles.push(hit.route.meta.title);
    }
  }
  return titles;
}

/** 管理端主布局：动态菜单、折叠、多标签、用户菜单、主题/布局设置与全屏 */
export default function BasicLayout() {
  const { message } = AntdApp.useApp();
  const location = useLocation();
  const token = useUserStore((state) => state.token);
  const userInfo = useUserStore((state) => state.userInfo);
  const routes = usePermissionStore((state) => state.routes);
  const collapsed = useAppStore((state) => state.collapsed);
  const toggleCollapsed = useAppStore((state) => state.toggleCollapsed);
  const layout = useAppStore((state) => state.layout);
  const dark = useAppStore((state) => state.dark);

  const pathname = location.pathname;

  // 未登录访问受保护页：回登录页
  useEffect(() => {
    if (!token) {
      history.replace('/login');
    }
  }, [token]);

  // 刷新后令牌仍在但状态丢失：重新加载 userinfo 与动态路由
  useEffect(() => {
    if (!token || userInfo) {
      return;
    }
    let cancelled = false;
    Promise.all([userinfoApi(), routersApi()])
      .then(([userinfo, routers]) => {
        if (cancelled) {
          return;
        }
        const userStore = useUserStore.getState();
        userStore.setUserInfo({
          userId: userinfo.user.userId,
          userName: userinfo.user.userName,
          nickName: userinfo.user.nickName,
          avatar: userinfo.user.avatar,
          roles: userinfo.roles ?? [],
          permissions: userinfo.permissions ?? [],
        });
        usePermissionStore
          .getState()
          .setRoutes(filterAuthorizedMenus(routers ?? [], userinfo.permissions ?? []));
      })
      .catch(() => {
        if (!cancelled) {
          message.error('用户信息加载失败，请重新登录');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [token, userInfo, message]);

  /** 登出：服务端仅记录日志，前端清空令牌/用户/路由/页签后回登录页 */
  async function handleLogout() {
    try {
      await logoutApi();
    } catch {
      // 登出接口失败不阻塞本地清理
    }
    useUserStore.getState().reset();
    usePermissionStore.getState().reset();
    useTagsViewStore.getState().delAll();
    history.push('/login');
  }

  // 可见菜单树（空目录已过滤）
  const menuRoutes = useMemo(
    () => filterEmptyDirectories(routes).filter((route) => !route.hidden),
    [routes],
  );
  const allMenuItems = useMemo(() => buildMenuItems(menuRoutes), [menuRoutes]);
  // 首页固定入口置于菜单首位
  const homeItem: MenuItem = { key: '/', icon: MENU_ICONS.home, label: '首页' };

  // 混合布局：顶部展示一级目录，侧栏展示当前激活目录的子菜单
  const activeRootPath = useMemo(() => {
    for (const route of menuRoutes) {
      const rootPath = route.path.startsWith('/') ? route.path : `/${route.path}`;
      if (pathname === rootPath || pathname.startsWith(`${rootPath}/`)) {
        return rootPath;
      }
    }
    return '';
  }, [menuRoutes, pathname]);
  const mixSiderRoutes = useMemo(
    () => menuRoutes.filter((route) => {
      const rootPath = route.path.startsWith('/') ? route.path : `/${route.path}`;
      return rootPath === activeRootPath;
    }),
    [menuRoutes, activeRootPath],
  );

  const showSider = layout === 'side' || (layout === 'mix' && mixSiderRoutes.length > 0);
  const siderItems =
    layout === 'mix' ? buildMenuItems(mixSiderRoutes, activeRootPath) : allMenuItems;

  const openKeys = useMemo(() => computeOpenKeys(menuRoutes, pathname), [menuRoutes, pathname]);
  const breadcrumbItems = useMemo(
    () => computeBreadcrumb(routes, pathname).map((title) => ({ title })),
    [routes, pathname],
  );

  const userMenuItems: MenuProps['items'] = [
    { key: 'profile', icon: <UserOutlined />, label: '个人中心' },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
  ];

  if (!token || (token && !userInfo)) {
    return <Spin style={{ display: 'block', margin: '200px auto' }} />;
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {showSider && (
        <Sider collapsed={collapsed} theme={dark ? 'dark' : 'light'} width={220}>
          <div
            style={{
              height: 48,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: collapsed ? 14 : 18,
            }}
          >
            {collapsed ? 'SA' : 'summer-admin'}
          </div>
          <Menu
            mode="inline"
            theme={dark ? 'dark' : 'light'}
            selectedKeys={[pathname]}
            defaultOpenKeys={collapsed ? undefined : openKeys}
            items={[homeItem, ...siderItems]}
            onClick={({ key }) => history.push(String(key))}
          />
        </Sider>
      )}
      <Layout>
        <Header
          style={{
            padding: '0 16px',
            background: dark ? '#141414' : '#fff',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            lineHeight: 'normal',
          }}
        >
          {layout !== 'top' && (
            <span onClick={toggleCollapsed} style={{ cursor: 'pointer', fontSize: 16 }}>
              {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            </span>
          )}
          <Breadcrumb items={breadcrumbItems} style={{ flex: 1 }} />
          <Screenfull />
          <LayoutSettings />
          <Dropdown
            menu={{
              items: userMenuItems,
              onClick: ({ key }) => {
                if (key === 'profile') {
                  history.push('/system/user/profile/index');
                } else if (key === 'logout') {
                  handleLogout().catch(() => message.error('退出失败'));
                }
              },
            }}
          >
            <span style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <Avatar size="small" icon={<UserOutlined />} src={userInfo?.avatar} />
              {userInfo?.nickName ?? userInfo?.userName}
            </span>
          </Dropdown>
        </Header>
        {layout === 'top' && (
          <Menu
            mode="horizontal"
            selectedKeys={[pathname]}
            items={[homeItem, ...allMenuItems]}
            onClick={({ key }) => history.push(String(key))}
          />
        )}
        <TagsView />
        <Content style={{ margin: 16 }}>
          <DynamicPage />
        </Content>
      </Layout>
    </Layout>
  );
}
