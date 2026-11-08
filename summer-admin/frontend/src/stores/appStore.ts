import { create } from 'zustand';

export type LayoutMode = 'side' | 'top' | 'mix';

interface AppState {
  collapsed: boolean;
  themeColor: string;
  dark: boolean;
  layout: LayoutMode;
  toggleCollapsed: () => void;
  setThemeColor: (color: string) => void;
  setDark: (dark: boolean) => void;
  setLayout: (layout: LayoutMode) => void;
}

const DEFAULT_THEME_COLOR = '#1677ff';

/** 应用外观状态：侧栏折叠、主题色、明暗与布局模式 */
export const useAppStore = create<AppState>((set) => ({
  collapsed: false,
  themeColor: DEFAULT_THEME_COLOR,
  dark: false,
  layout: 'side',
  toggleCollapsed: () => set((state) => ({ collapsed: !state.collapsed })),
  setThemeColor: (themeColor) => set({ themeColor }),
  setDark: (dark) => set({ dark }),
  setLayout: (layout) => set({ layout }),
}));
