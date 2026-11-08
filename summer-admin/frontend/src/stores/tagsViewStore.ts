import { create } from 'zustand';

export interface TagView {
  path: string;
  fullPath?: string;
  title?: string;
  affix?: boolean;
}

interface TagsViewState {
  visitedViews: TagView[];
  addView: (view: TagView) => void;
  delView: (path: string) => TagView[];
  delOthers: (path: string) => void;
  delAll: () => void;
}

/** 多标签页状态：重复打开不重复入栈，关闭当前标签由调用方选择相邻标签 */
export const useTagsViewStore = create<TagsViewState>((set, get) => ({
  visitedViews: [],
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
  delAll: () => set({ visitedViews: [] }),
}));
