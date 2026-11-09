import { create } from 'zustand';

export interface TagView {
  path: string;
  fullPath?: string;
  title?: string;
  affix?: boolean;
}

interface TagsViewState {
  visitedViews: TagView[];
  /** 各标签的重挂载计数：刷新页签时递增，KeepAlive 以此强制重新挂载 */
  refreshTicks: Record<string, number>;
  addView: (view: TagView) => void;
  delView: (path: string) => TagView[];
  delOthers: (path: string) => void;
  delAll: () => void;
  /** 关闭指定标签并返回应激活的相邻标签（右侧优先，末尾取左侧），无剩余返回 undefined */
  closeView: (path: string) => TagView | undefined;
  /** 刷新指定标签：保留全部其他标签，仅递增该标签的重挂载计数 */
  refreshView: (path: string) => void;
}

/** 多标签页状态：重复打开不重复入栈，关闭当前标签由调用方选择相邻标签 */
export const useTagsViewStore = create<TagsViewState>((set, get) => ({
  visitedViews: [],
  refreshTicks: {},
  addView: (view) => {
    const { visitedViews } = get();
    if (!visitedViews.some((item) => item.path === view.path)) {
      set({ visitedViews: [...visitedViews, view] });
    }
  },
  delView: (path) => {
    const visitedViews = get().visitedViews.filter((item) => item.path !== path);
    set({ visitedViews });
    return visitedViews;
  },
  delOthers: (path) => {
    const visitedViews = get().visitedViews.filter((item) => item.path === path || item.affix);
    set({ visitedViews });
  },
  delAll: () => set({ visitedViews: [], refreshTicks: {} }),
  closeView: (path) => {
    const { visitedViews } = get();
    const index = visitedViews.findIndex((item) => item.path === path);
    if (index === -1) {
      return undefined;
    }
    const remaining = visitedViews.filter((item) => item.path !== path);
    set({ visitedViews: remaining });
    if (remaining.length === 0) {
      return undefined;
    }
    return remaining[Math.min(index, remaining.length - 1)];
  },
  refreshView: (path) => {
    const { visitedViews, refreshTicks } = get();
    if (!visitedViews.some((item) => item.path === path)) {
      return;
    }
    set({ refreshTicks: { ...refreshTicks, [path]: (refreshTicks[path] ?? 0) + 1 } });
  },
}));
