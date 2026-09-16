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
      // v9.6.1：原子构建修正——robocopy /MIR 经 cmd 转发实测未删旧 hash（同页多版本 chunk 并存，
      // index 与 chunk 版本错配→对应页面静默空白）。改为「成功后先 del 清空再 xcopy 全量拷」，
      // 两者均为 Windows 原生命令（中文路径 libuv 坑），语义等价镜像且各步可验证。
      name: 'pims-atomic-deploy',
      closeBundle() {
        if (process.platform !== 'win32') return
        execSync('cmd /c "del /q /s ..\\pims-server\\src\\main\\resources\\static\\*.* >nul 2>&1"')
        execSync('xcopy /E /Y /I /Q dist ..\\pims-server\\src\\main\\resources\\static', { stdio: 'ignore' })
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
    outDir: 'dist',
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
