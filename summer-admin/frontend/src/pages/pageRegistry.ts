import { lazy, type ComponentType, type LazyExoticComponent } from 'react';

/** 安全回退键：白名单未命中统一映射到安全 404 */
export const NOT_FOUND_KEY = 'error/404';

/**
 * 页面组件白名单：键为后端 RouterVo.component 字符串，
 * 值为静态 import 的 lazy 加载器；禁止用后端字符串构造动态 import。
 * 本任务仅注册占位首页与 404，后续任务在此追加各自页面键。
 */
export const pageRegistry: Record<string, () => Promise<{ default: ComponentType }>> = {
  index: () => import('./index'),
  [NOT_FOUND_KEY]: () => import('./error/404'),
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
