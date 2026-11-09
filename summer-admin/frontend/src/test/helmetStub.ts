// 测试桩：react-helmet-async 在 jsdom 下会引入独立 React 副本导致元素版本冲突，
// 而 head 标签管理对路由/导航测试无关，这里以透传实现替代生成的 helmet 插件
export const innerProvider = (container: unknown) => container;
