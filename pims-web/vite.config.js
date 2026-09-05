import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
// 前端产物直接输出到后端 static 目录，随 jar 一体部署
const staticDir = path.resolve(__dirname, '../pims-server/src/main/resources/static')

export default defineConfig({
  plugins: [
    vue(),
    {
      name: 'pims-clean-static',
      buildStart() {
        // 构建前清空旧产物，避免 hash 文件无限堆积（Vite 对项目外 outDir 的 emptyOutDir 不可靠）。
        // 注意：Node fs.rmSync 对中文路径（D:\开发\PIMS\...）静默删除失败（libuv 已知问题），
        // 必须用系统命令 cmd del（Windows 构建环境实测可靠）。
        execSync('cmd /c "del /q /s ..\\pims-server\\src\\main\\resources\\static\\*.* >nul 2>&1"', { stdio: 'ignore' })
      }
    }
  ],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: staticDir,
    emptyOutDir: true,
    // v7.1 分包：vendor（Element Plus/vue/router ~1MB）hash 跨版本稳定——发版只重下业务包（几十 KB）
    rollupOptions: {
      output: {
        manualChunks: { vendor: ['element-plus', '@element-plus/icons-vue', 'vue', 'vue-router'] }
      }
    },
    chunkSizeWarningLimit: 1600
  }
})
