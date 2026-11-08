import { defineConfig } from '@umijs/max';

export default defineConfig({
  // 生产使用 hash history，避免服务端路由回退依赖
  history: { type: 'hash' },
  npmClient: 'pnpm',
  // 开发环境代理后端 API（开发端口 8000 由 dev 脚本的 PORT 环境变量固定）
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
  routes: [
    { path: '/', component: 'index' },
    { path: '*', component: 'error/404' },
  ],
});
