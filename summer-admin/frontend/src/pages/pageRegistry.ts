import { lazy, type ComponentType, type LazyExoticComponent } from 'react';

/** 安全回退键：白名单未命中统一映射到安全 404 */
export const NOT_FOUND_KEY = 'error/404';

/**
 * 页面组件白名单：键为后端 RouterVo.component 字符串，
 * 值为静态 import 的 lazy 加载器；禁止用后端字符串构造动态 import。
 */
export const pageRegistry: Record<string, () => Promise<{ default: ComponentType }>> = {
  index: () => import('./index'),
  'system/user/index': () => import('./system/user'),
  'system/user/profile/index': () => import('./system/user/profile'),
  'system/role/index': () => import('./system/role'),
  'system/menu/index': () => import('./system/menu'),
  'system/dept/index': () => import('./system/dept'),
  'system/post/index': () => import('./system/post'),
  'system/dict/index': () => import('./system/dict'),
  'system/dict/data/index': () => import('./system/dict/data'),
  'system/config/index': () => import('./system/config'),
  'system/notice/index': () => import('./system/notice'),
  'monitor/operlog/index': () => import('./monitor/log/operation'),
  'monitor/logininfor/index': () => import('./monitor/log/login'),
  [NOT_FOUND_KEY]: () => import('./error/404'),
};

/**
 * 前端内置静态页：不依赖后端动态路由，任何登录用户可访问。
 * 键为完整 hash 路径，值为白名单组件键与页签标题。
 */
export const FRONTEND_STATIC_PAGES: Record<string, { componentKey: string; title: string }> = {
  '/system/user/profile/index': { componentKey: 'system/user/profile/index', title: '个人中心' },
  '/system/dict/data/index': { componentKey: 'system/dict/data/index', title: '字典数据' },
};

// 解析结果缓存，保证同一组件多次解析返回同一 lazy 引用
const resolvedCache = new Map<string, LazyExoticComponent<ComponentType>>();

/** 将后端 component 字符串解析为白名单键；未知/空/路径穿越统一回退 404 */
export function resolvePageKey(component?: string | null): string {
  if (!component) {
    return NOT_FOUND_KEY;
  }
  // 拒绝路径穿越与绝对/相对路径，仅接受白名单键
  if (
    component.includes('..') ||
    component.startsWith('/') ||
    component.startsWith('\\') ||
    component.startsWith('.')
  ) {
    return NOT_FOUND_KEY;
  }
  return pageRegistry[component] ? component : NOT_FOUND_KEY;
}

/** 解析白名单页面组件；任何非法输入都安全回退 404，永不抛错 */
export function resolvePage(component?: string | null): LazyExoticComponent<ComponentType> {
  const key = resolvePageKey(component);
  let page = resolvedCache.get(key);
  if (!page) {
    page = lazy(pageRegistry[key]);
    resolvedCache.set(key, page);
  }
  return page;
}
