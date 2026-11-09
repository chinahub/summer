import { defineConfig } from '@umijs/max';

export default defineConfig({
  // 生产使用 hash history，避免服务端路由回退依赖
  history: { type: 'hash' },
  npmClient: 'pnpm',
  // 多 chunk 共享 esbuild 辅助函数，避免 async chunk 命名冲突
  esbuildMinifyIIFE: true,
  // 开发环境代理后端 API（开发端口 8000 由 dev 脚本的 PORT 环境变量固定）；
  // 目标可用 API_PROXY_TARGET 覆盖（如本机 8080 被占用时指向临时端口）
  proxy: {
    '/api': {
      target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
      changeOrigin: true,
    },
  },
  routes: [
    { path: '/login', component: 'login' },
    // 主布局承载首页、动态路由与个人中心；未知路径由布局内 403/404 兜底
    { path: '/', component: '@/layouts/BasicLayout' },
    { path: '*', component: '@/layouts/BasicLayout' },
  ],
});
