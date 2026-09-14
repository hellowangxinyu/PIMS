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
      // v9.2（P2-6 审计）：原子构建——原 buildStart 先清空 static，构建中途失败=产物被清空。
      // 改为构建到本项目 dist，成功后 robocopy /MIR 镜像到后端 static（顺带清掉旧 hash 堆积；
      // 中文路径必须用系统命令，libuv 已知坑）。robocopy 退出码 0-7 均为成功（1=有文件复制），>=8 才失败。
      name: 'pims-atomic-deploy',
      closeBundle() {
        if (process.platform !== 'win32') return
        try {
          execSync('robocopy dist "..\\pims-server\\src\\main\\resources\\static" /MIR /NJH /NJS /NDL /NFL >nul', { stdio: 'ignore', shell: 'cmd.exe' })
        } catch (e) {
          if (e.status === undefined || e.status >= 8) throw e
        }
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
