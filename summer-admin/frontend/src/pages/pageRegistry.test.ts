import { describe, expect, it } from 'vitest';
import { pageRegistry, resolvePage, resolvePageKey } from '@/pages/pageRegistry';

describe('pageRegistry 白名单注册表', () => {
  it('当前已注册键必须指向本任务创建的页面文件', () => {
    const keys = Object.keys(pageRegistry);
    // 首页、个人中心、五个系统管理页、字典/参数/公告/日志页与字典数据主从页、安全 404
    expect(keys.sort()).toEqual([
      'error/404',
      'index',
      'monitor/logininfor/index',
      'monitor/operlog/index',
      'system/config/index',
      'system/dept/index',
      'system/dict/data/index',
      'system/dict/index',
      'system/menu/index',
      'system/notice/index',
      'system/post/index',
      'system/role/index',
      'system/user/index',
      'system/user/profile/index',
    ]);
  });

  it('未知组件解析为安全 404', () => {
    expect(resolvePageKey('system/dict/unknown')).toBe('error/404');
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
