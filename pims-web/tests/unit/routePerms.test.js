import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { ROUTE_PERMS } from '../../src/utils/routePerms.js'

/**
 * 三方一致性：路由表 ⇄ ROUTE_PERMS（URL 直输守卫）⇄ Layout NAV_GROUPS（侧边栏）
 * routePerms.js 文件头自己写着"改菜单时同步改这里"——本用例把手工同步钉成自动化。
 * 解析方式是文本级（node 环境无 DOM，不走 createWebHistory），新增路由/菜单后自动纳入校验。
 */
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const routerSrc = readFileSync(path.join(root, 'src/router/index.js'), 'utf-8')
const layoutSrc = readFileSync(path.join(root, 'src/views/Layout.vue'), 'utf-8')

// 路由表：children 相对路径归一为 /xxx；忽略布局壳('/')、Dashboard('') 与 /login
const routePaths = [...routerSrc.matchAll(/\bpath:\s*'([^']*)'/g)]
  .map(m => m[1])
  .filter(p => p !== '' && p !== '/' && p !== '/login')
  .map(p => (p.startsWith('/') ? p : '/' + p))

// 侧边栏 NAV_GROUPS 区块（到菜单搜索注释为止），每项单行 { path, title, icon, perm }
const navBlock = layoutSrc.slice(layoutSrc.indexOf('const NAV_GROUPS'), layoutSrc.indexOf('// 菜单搜索'))
const navItems = [...navBlock.matchAll(/\{ path: '([^']+)', title: '[^']+', icon: [^,]+, perm: '([^']+)' \}/g)]
  .map(m => ({ path: m[1], perm: m[2] }))

describe('三方一致性：路由表 ⇄ ROUTE_PERMS ⇄ 侧边栏菜单', () => {
  it('解析结果规模正常（页面级路由 80+，菜单项 80+）', () => {
    expect(routePaths.length).toBeGreaterThan(80)
    expect(navItems.length).toBeGreaterThan(80)
    expect(routePaths).toContain('/sales')
    expect(routePaths).toContain('/report-finance-trend')
  })

  it('每个路由都有权限映射（否则直输 URL 无守卫）', () => {
    const missing = routePaths.filter(p => !(p in ROUTE_PERMS))
    expect(missing, `缺权限映射的路由: ${missing.join(', ')}`).toEqual([])
  })

  it('ROUTE_PERMS 无死键（每个键都对应真实存在的路由）', () => {
    const dead = Object.keys(ROUTE_PERMS).filter(p => !routePaths.includes(p))
    expect(dead, `ROUTE_PERMS 死键: ${dead.join(', ')}`).toEqual([])
  })

  it('菜单项全部有真实路由且全部被权限覆盖', () => {
    const noRoute = navItems.filter(i => !routePaths.includes(i.path)).map(i => i.path)
    const noPerm = navItems.filter(i => !(i.path in ROUTE_PERMS)).map(i => i.path)
    expect(noRoute, `菜单项无路由: ${noRoute.join(', ')}`).toEqual([])
    expect(noPerm, `菜单项缺权限: ${noPerm.join(', ')}`).toEqual([])
  })

  it('菜单 perm 与守卫 perm 同口径（防"菜单看得见、直输被拦"）', () => {
    const drift = navItems
      .filter(i => ROUTE_PERMS[i.path] !== i.perm)
      .map(i => `${i.path}: 菜单=${i.perm} 守卫=${ROUTE_PERMS[i.path]}`)
    expect(drift, `perm 漂移: ${drift.join(' | ')}`).toEqual([])
  })

  it('守卫无权限回退目标必须可路由（v9.3 曾误写 /dashboard 导致无权限用户空白页，修复为 /，此用例回归钉死）', () => {
    const m = routerSrc.match(/from\.path\s*:\s*'([^']+)'/)
    expect(m, '未从守卫代码解析到回退目标').toBeTruthy()
    const fallback = m[1]
    expect(fallback === '/' || routePaths.includes(fallback),
      `守卫回退目标 ${fallback} 不在路由表中，用户将看到空白页`).toBe(true)
  })
})
