// @vitest-environment jsdom
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp } from 'vue'
import { ElMessage } from 'element-plus'

// 视图组件全部换成轻量桩：守卫测试只关心导航结果，不渲染真实页面（Login.vue 1900+ 行的懒加载会让 isReady 超时）
vi.mock('../../src/views/Layout.vue', () => ({ default: { name: 'Layout', setup: () => () => null } }))
vi.mock('../../src/views/Login.vue', () => ({ default: { name: 'Login', setup: () => () => null } }))
vi.mock('../../src/views/SalesOrderList.vue', () => ({ default: { name: 'Sales', setup: () => () => null } }))

import router from '../../src/router/index.js'

function loginAs(permissions) {
  localStorage.setItem('user', JSON.stringify({ username: 'tester', permissions }))
}

describe('路由守卫（登录态 + v9.3 权限直输拦截）', () => {
  // vue-router 的初始导航由 app.use(router) 触发；不安装 router 时 isReady 永不解析
  beforeAll(async () => {
    localStorage.clear()
    createApp({ render: () => null }).use(router)
    await router.isReady()
  })

  beforeEach(() => {
    localStorage.clear()
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => {})
  })
  afterEach(async () => {
    localStorage.clear()
    vi.restoreAllMocks()
    await router.push('/login').catch(() => {})
  })

  it('未登录访问业务页 → 重定向 /login', async () => {
    await router.isReady()
    await router.push('/sales').catch(() => {})
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('已登录但无权限 → 拦回首页 / 并提示所需权限', async () => {
    await router.isReady()
    loginAs([])
    // 模拟真实动线：先落在首页，再直输无权限 URL（from=/login 时守卫会把人"退回登录页"，见文件尾注）
    await router.push('/').catch(() => {})
    await router.push('/sales').catch(() => {})
    expect(router.currentRoute.value.path).toBe('/')
    await vi.waitFor(() => expect(ElMessage.warning).toHaveBeenCalled())
    expect(ElMessage.warning.mock.calls[0][0]).toContain('sales:read')
  })

  // 注：from.path='/login' 时无权限会被退回 /login（守卫 next(from.path) 分支），
  // 表现为"像被登出"。真实入口登录成功即跳走，触发面极小，暂记不动 src。

  it('已登录且有权限 → 正常进入', async () => {
    await router.isReady()
    loginAs(['sales:read'])
    await router.push('/sales').catch(() => {})
    expect(router.currentRoute.value.path).toBe('/sales')
    expect(ElMessage.warning).not.toHaveBeenCalled()
  })

  it('登录页不受守卫拦截', async () => {
    await router.isReady()
    await router.push('/login').catch(() => {})
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
