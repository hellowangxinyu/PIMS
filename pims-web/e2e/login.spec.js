import { test, expect } from '@playwright/test'

// 凭据走环境变量（不入库）；未配置时仅跳过依赖账号的用例，守卫类用例照常真跑
const USER = process.env.PIMS_USER
const PASS = process.env.PIMS_PASS

// 不需要凭据：未登录访问被守卫重定向登录页（真打测试机）
test('未登录直输业务页 → 重定向登录页（守卫拦截）', async ({ page }) => {
  await page.goto('/sales')
  await expect(page).toHaveURL(/\/login/)
})

test.describe('登录与版本链路', () => {
  test.skip(!USER || !PASS, '未配置 PIMS_USER/PIMS_PASS 环境变量')

  test('登录成功 → 首页可见 → 侧边栏版本串符合 vX.Y · hash 格式', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名', { exact: true }).fill(USER)
    await page.getByLabel('密码', { exact: true }).fill(PASS)
    await page.locator('.login-btn').click()

    // 登录后落首页：侧边栏用户卡片出现（密码错误则停留登录页，此断言自然失败）
    await expect(page.locator('.user-name')).toBeVisible()

    // 版本串（v12.0 起页脚显示构建版本——该用例同时回归"版本可见"需求）
    const ver = page.locator('.ver-tag')
    await expect(ver).toBeVisible()
    await expect(ver).toContainText(/v\d+\.\d+ · [0-9a-f]{7}/)
  })
})

