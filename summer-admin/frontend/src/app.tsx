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

/** 主题容器：订阅外观状态，主题色/明暗变化即时生效 */
function ThemeContainer({ children }: { children: ReactNode }) {
  const themeColor = useAppStore((state) => state.themeColor);
  const dark = useAppStore((state) => state.dark);
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: { colorPrimary: themeColor },
        algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
      }}
    >
      {children}
    </ConfigProvider>
  );
}

/** Umi 运行时根容器：注入 Ant Design 主题/中文与 React Query */
export function rootContainer(container: ReactNode) {
  return (
    <ThemeContainer>
      <AntdApp>
        <QueryClientProvider client={queryClient}>{container}</QueryClientProvider>
      </AntdApp>
    </ThemeContainer>
  );
}
