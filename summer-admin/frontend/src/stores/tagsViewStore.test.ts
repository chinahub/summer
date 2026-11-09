import { beforeEach, describe, expect, it } from 'vitest';
import { useTagsViewStore } from '@/stores/tagsViewStore';

describe('tagsViewStore 多标签页状态', () => {
  beforeEach(() => {
    useTagsViewStore.getState().delAll();
  });

  it('重复打开同一标签不会重复入栈', () => {
    const store = useTagsViewStore.getState();
    store.addView({ path: '/', title: '首页', affix: true });
    store.addView({ path: '/system/user', title: '用户管理' });
    useTagsViewStore.getState().addView({ path: '/system/user', title: '用户管理' });
    expect(useTagsViewStore.getState().visitedViews).toHaveLength(2);
  });

  it('关闭中间标签后选择右侧相邻标签激活', () => {
    const store = useTagsViewStore.getState();
    store.addView({ path: '/', title: '首页', affix: true });
    store.addView({ path: '/a', title: 'A' });
    store.addView({ path: '/b', title: 'B' });
    store.addView({ path: '/c', title: 'C' });
    const next = useTagsViewStore.getState().closeView('/b');
    const views = useTagsViewStore.getState().visitedViews;
    expect(views.map((item) => item.path)).toEqual(['/', '/a', '/c']);
    expect(next?.path).toBe('/c');
  });

  it('关闭末尾标签后选择左侧相邻标签激活', () => {
    const store = useTagsViewStore.getState();
    store.addView({ path: '/a', title: 'A' });
    store.addView({ path: '/b', title: 'B' });
    const next = useTagsViewStore.getState().closeView('/b');
    expect(next?.path).toBe('/a');
  });

  it('关闭最后一个标签后无激活目标', () => {
    useTagsViewStore.getState().addView({ path: '/a', title: 'A' });
    expect(useTagsViewStore.getState().closeView('/a')).toBeUndefined();
    expect(useTagsViewStore.getState().visitedViews).toHaveLength(0);
  });

  it('刷新标签保留其他标签并生成重挂载计数', () => {
    const store = useTagsViewStore.getState();
    store.addView({ path: '/a', title: 'A' });
    store.addView({ path: '/b', title: 'B' });
    store.addView({ path: '/c', title: 'C' });
    useTagsViewStore.getState().refreshView('/b');
    const state = useTagsViewStore.getState();
    expect(state.visitedViews.map((item) => item.path)).toEqual(['/a', '/b', '/c']);
    expect(state.refreshTicks['/b']).toBe(1);
    expect(state.refreshTicks['/a']).toBeUndefined();
    // 再次刷新计数递增
    state.refreshView('/b');
    expect(useTagsViewStore.getState().refreshTicks['/b']).toBe(2);
  });
});
