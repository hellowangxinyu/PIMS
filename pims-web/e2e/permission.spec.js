import { test, expect } from '@playwright/test'

// 越权纵深防御链：不依赖低权限账号——注入"空权限"伪造登录态，走真实浏览器里的同一守卫代码路径。
// 守卫拦回 + 警告文案已由 L2 单测（routerGuard.test.js）钉死；本链验证端到端后果：
// 伪登录态无 HttpOnly Cookie → 前端守卫拦回 → 业务 API 401 → 清态跳登录页（服务端兜底真实有效）。
test('伪造空权限登录态直输 /sales → 纵深防御最终踢回登录页', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('user', JSON.stringify({ username: 'forged', permissions: [] }))
  })

  await page.goto('/sales')

  // v12.0.1 修复点回归：守卫拦回目标不再是空白 /dashboard；无后端凭据最终落在登录页
  await expect(page).toHaveURL(/\/login/, { timeout: 10000 })
  // 登录页渲染正常（而非 401 的裸响应/错误页）
  await expect(page.getByLabel('用户名', { exact: true })).toBeVisible()
})
