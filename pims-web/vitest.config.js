import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// 独立测试配置：不加载 vite.config.js 的 pims-atomic-deploy 插件——
// vitest 启动 dev server 时会执行插件的 buildStart，把 pims-server/src/main/resources/static 清空（2026-10-09 实测踩坑）。
// 保留 vue 插件：测试会真实加载路由懒指向的 .vue 文件。
// 需 DOM 的测试文件用文件头注释 // @vitest-environment jsdom 声明。
export default defineConfig({
  plugins: [vue()],
  test: {
    include: ['tests/**/*.test.js'],
  },
})
