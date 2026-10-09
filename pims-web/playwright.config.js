import { defineConfig } from '@playwright/test'

// L3 E2E：打阿里云测试机（整机测试专用）。
// 凭据走环境变量 PIMS_USER/PIMS_PASS（不入库）；单 worker 串行——共享一个真实库，并行会互踩数据。
// 用法：PIMS_USER=xx PIMS_PASS=xx npm run test:e2e
export default defineConfig({
  testDir: './e2e',
  workers: 1,
  retries: 0,
  timeout: 30000,
  use: {
    baseURL: process.env.PIMS_BASE_URL || 'http://182.92.95.12',
    headless: true,
  },
})
