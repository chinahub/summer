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

/** 外观设置的本地存储键：刷新后保留主题色/明暗/布局偏好 */
const APP_SETTING_KEY = 'Admin-App-Setting';

/** 读取本地保存的外观偏好，缺省返回默认值 */
function loadSetting(): { themeColor: string; dark: boolean; layout: LayoutMode } {
  try {
    const raw = localStorage.getItem(APP_SETTING_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<AppState>;
      return {
        themeColor: typeof parsed.themeColor === 'string' ? parsed.themeColor : DEFAULT_THEME_COLOR,
        dark: parsed.dark === true,
        layout: parsed.layout === 'top' || parsed.layout === 'mix' ? parsed.layout : 'side',
      };
    }
  } catch {
    // 本地数据损坏时按默认处理
  }
  return { themeColor: DEFAULT_THEME_COLOR, dark: false, layout: 'side' };
}

const initialSetting = loadSetting();

/** 应用外观状态：侧栏折叠、主题色、明暗与布局模式；主题偏好持久化到本地 */
export const useAppStore = create<AppState>((set) => ({
  collapsed: false,
  ...initialSetting,
  toggleCollapsed: () => set((state) => ({ collapsed: !state.collapsed })),
  setThemeColor: (themeColor) => set({ themeColor }),
  setDark: (dark) => set({ dark }),
  setLayout: (layout) => set({ layout }),
}));

// 外观偏好变更时写回本地存储
useAppStore.subscribe((state) => {
  localStorage.setItem(
    APP_SETTING_KEY,
    JSON.stringify({ themeColor: state.themeColor, dark: state.dark, layout: state.layout }),
  );
});
