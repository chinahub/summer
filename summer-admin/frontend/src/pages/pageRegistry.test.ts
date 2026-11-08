import { describe, expect, it } from 'vitest';
import { pageRegistry, resolvePage, resolvePageKey } from '@/pages/pageRegistry';

describe('pageRegistry 白名单注册表', () => {
  it('当前已注册键必须指向本任务创建的页面文件', () => {
    const keys = Object.keys(pageRegistry);
    // 本任务仅注册占位首页与安全 404，后续任务在此追加
    expect(keys.sort()).toEqual(['error/404', 'index']);
  });

  it('未知组件解析为安全 404', () => {
    expect(resolvePageKey('system/user/index')).toBe('error/404');
    expect(resolvePageKey('not/exist')).toBe('error/404');
  });

  it('空组件与 undefined 解析为安全 404', () => {
    expect(resolvePageKey('')).toBe('error/404');
    expect(resolvePageKey(undefined)).toBe('error/404');
    expect(resolvePageKey(null)).toBe('error/404');
  });

  it('路径穿越（../ 开头）解析为安全 404', () => {
    expect(resolvePageKey('../secret')).toBe('error/404');
    expect(resolvePageKey('..\\secret')).toBe('error/404');
    expect(resolvePageKey('a/../../secret')).toBe('error/404');
  });

  it('已注册键原样返回', () => {
    expect(resolvePageKey('index')).toBe('index');
    expect(resolvePageKey('error/404')).toBe('error/404');
  });

  it('resolvePage 返回可渲染组件且永不抛错', () => {
    expect(() => resolvePage('index')).not.toThrow();
    expect(() => resolvePage('../evil')).not.toThrow();
    const fallback = resolvePage('../evil');
    const notFound = resolvePage('error/404');
    expect(fallback).toBe(notFound);
  });
});
