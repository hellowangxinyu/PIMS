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
      // v10.1：原子构建三步——①构建前 rd 清 dist 与 static（vite 自身 emptyOutDir 在中文路径下静默失效，
      // dist 会按版本无限堆积）；②构建成功后 xcopy 镜像到后端 static。
      // 均为 Windows 原生命令绕开 node fs 中文路径坑；构建失败时 static 为空属预期（jar 打包是显式后续步骤）。
      name: 'pims-atomic-deploy',
      buildStart() {
        if (process.platform !== 'win32' || process.env.PIMS_SKIP_DEPLOY) return
        execSync('cmd /c "if exist dist rd /s /q dist >nul 2>&1 & del /q /s ..\\pims-server\\src\\main\\resources\\static\\*.* >nul 2>&1"')
      },
      closeBundle() {
        if (process.platform !== 'win32' || process.env.PIMS_SKIP_DEPLOY) return
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
