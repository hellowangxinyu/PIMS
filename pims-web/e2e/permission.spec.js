import { test, expect } from '@playwright/test'

// 低权限账号：验证 v9.3 直输 URL 拦截 + v12.0.1 /dashboard 回退修复
const LOW_USER = process.env.PIMS_LOW_USER
const LOW_PASS = process.env.PIMS_LOW_PASS

async function login(page, user, pass) {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(user)
  await page.getByLabel('密码').fill(pass)
  await page.locator('.login-btn').click()
  await expect(page.locator('.user-name')).toBeVisible()
}

test.describe('权限直输拦截', () => {
  test.skip(!LOW_USER || !LOW_PASS, '未配置 PIMS_LOW_USER/PIMS_LOW_PASS（低权限账号）')

  test('无权限账号直输 /sales → 拦回首页并提示所需权限码', async ({ page }) => {
    await login(page, LOW_USER, LOW_PASS)

    await page.goto('/sales')

    // v12.0.1 修复点：拦回目标必须是存在的路由（曾是 /dashboard 空白页）
    expect(new URL(page.url()).pathname).toBe('/')
    // 守卫浮出警告，点名所需权限
    await expect(page.locator('.el-message').last()).toContainText('sales:read')
  })
})
