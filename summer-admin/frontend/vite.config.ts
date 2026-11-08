import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

// Vitest 专用配置：Umi Max 自身构建不走此文件，仅统一 @ 别名与测试环境。
// 纯函数测试无需 JSX 编译插件，避免与 Umi 内置 vite 版本冲突。
export default defineConfig({
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
