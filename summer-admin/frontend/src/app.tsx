import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { ReactNode } from 'react';
import { useAppStore } from '@/stores/appStore';

// 全局唯一 QueryClient，配合 TanStack Query 管理服务端状态
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

/** Umi 运行时根容器：注入 Ant Design 主题/中文与 React Query */
export function rootContainer(container: ReactNode) {
  const { themeColor, dark } = useAppStore.getState();
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: { colorPrimary: themeColor },
        algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
      }}
    >
      <AntdApp>
        <QueryClientProvider client={queryClient}>{container}</QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}
