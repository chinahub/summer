import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

// Vitest 专用配置：Umi Max 自身构建不走此文件，仅统一 @ 别名与测试环境。
// 纯函数测试无需 JSX 编译插件，避免与 Umi 内置 vite 版本冲突。
export default defineConfig({
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      // 测试中直接映射到生成的 ESM 运行时（与 tsconfig paths 一致），
      // 避免 vitest 预打包 CJS 入口把 umi CLI/esbuild 依赖链内联进 jsdom
      '@umijs/max': resolve(__dirname, 'src/.umi/exports.ts'),
      '@@': resolve(__dirname, 'src/.umi'),
      '@@/core/helmet.ts': resolve(__dirname, 'src/test/helmetStub.ts'),
      // 测试中 'umi' 以最小桩替代，避免把 CLI/esbuild 依赖链载入 jsdom
      umi: resolve(__dirname, 'src/test/umiStub.ts'),
      // pro-components 3.x 无 exports 映射，Node ESM 会错误落到 CJS lib（与 type:module 冲突）；
      // 显式指向 ESM es 构建并由下方 inline 交给 vite 解析目录导入，等同 webpack 的 module 字段行为
      '@ant-design/pro-components': resolve(
        __dirname,
        'node_modules/@ant-design/pro-components/es/index.js',
      ),
      // 强制单实例 React：renderer-react 的 pnpm 嵌套依赖默认解析到 react@18 副本，
      // 这里统一指向 react@19 兼容变体（路径与 pnpm-lock 绑定，变更需同步）
      react: resolve(__dirname, 'node_modules/react'),
      'react-dom': resolve(__dirname, 'node_modules/react-dom'),
      'react-router-dom': resolve(
        __dirname,
        'node_modules/.pnpm/react-router-dom@6.3.0_reac_3898c9ae8d2eef5ed541cd8854b51303/node_modules/react-router-dom',
      ),
      'react-router': resolve(
        __dirname,
        'node_modules/.pnpm/react-router-dom@6.3.0_reac_3898c9ae8d2eef5ed541cd8854b51303/node_modules/react-router',
      ),
      'react-helmet-async': resolve(
        __dirname,
        'node_modules/.pnpm/react-helmet-async@1.3.0_re_5c90926f1b9e1fa09ee8640effd5a037/node_modules/react-helmet-async',
      ),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['./src/test/setup.ts'],
    server: {
      deps: {
        // renderer-react 经绝对路径引入，默认被外部化导致双 React 实例；
        // 内联后与其余源码共享同一模块解析（单实例 React）
        // pro-components 3.x ESM 构建内部使用目录导入（'./card'），Node ESM 不支持；
      // 交给 vite 转换管线解析（等同 webpack module 字段行为），勿走 Node 外部化
      inline: [/renderer-react/, /react-router/, /react-helmet-async/, /react-side-effect/, /@ant-design\/pro-/],
      },
    },
  },
});
