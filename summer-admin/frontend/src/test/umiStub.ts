// Vitest 环境下替代 'umi' 包的最小运行时桩：
// 直接 import 'umi' 会把 CLI 依赖链（含 esbuild）拉进 jsdom 并因 realm 差异崩溃，
// 测试只需插件容器与少量运行时导出，语义与 Umi PluginManager 的 modify/event/compose 一致。
type AnyFn = (...args: unknown[]) => unknown;

interface PluginApply {
  [key: string]: AnyFn | undefined;
}

const enum ApplyType {
  compose = 'compose',
  modify = 'modify',
  event = 'event',
}

/** 与 Umi ApplyPluginsType 枚举一致的常量对象 */
export const ApplyPluginsType = {
  compose: ApplyType.compose,
  modify: ApplyType.modify,
  event: ApplyType.event,
} as const;

/** 插件容器：按注册顺序执行 modify（顺序归约）/event（顺序触发）/compose（洋葱包裹） */
export class PluginManager {
  private readonly applies: PluginApply[];

  private constructor(applies: PluginApply[]) {
    this.applies = applies;
  }

  static create(opts: { plugins: Array<{ apply?: PluginApply }>; validKeys?: string[] }) {
    return new PluginManager((opts.plugins ?? []).map((p) => p.apply ?? {}));
  }

  applyPlugins(opts: {
    key: string;
    type: ApplyType;
    initialValue?: unknown;
    args?: unknown;
  }): unknown {
    const hooks = this.applies
      .map((apply) => apply[opts.key])
      .filter((hook): hook is AnyFn => typeof hook === 'function');
    if (opts.type === ApplyType.event) {
      for (const hook of hooks) {
        hook(opts.args);
      }
      return undefined;
    }
    if (opts.type === ApplyType.modify) {
      return hooks.reduce((value, hook) => hook(value, opts.args) ?? value, opts.initialValue);
    }
    // compose：每个插件把上一层的实现包裹后返回，最终得到可调用链
    return hooks.reduce((fn, hook) => hook(fn, opts.args) ?? fn, opts.initialValue);
  }
}

/** EmptyRoute 依赖的路由出口占位（测试路由均为扁平路由，不会被真实使用） */
export const Outlet = () => null;
export const useOutletContext = () => undefined;
