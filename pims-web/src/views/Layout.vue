<template>
  <div class="app-layout" v-if="ready">
    <!-- 移动端遮罩 -->
    <div class="sidebar-overlay" :class="{ visible: sidebarOpen }" @click="sidebarOpen = false"></div>
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-brand">
        <div class="brand-mark">
          <svg viewBox="0 0 64 64" width="20" height="20" aria-hidden="true">
            <path d="M32 11c6.5 9 14.5 16.4 14.5 25a14.5 14.5 0 1 1-29 0c0-8.6 8-16 14.5-25z" fill="#fff"/>
            <circle cx="26.5" cy="38" r="4.5" fill="#818cf8" opacity="0.55"/>
          </svg>
        </div>
        <div class="brand-text">
          <span class="brand-name">PIMS</span>
          <span class="brand-sub">进销存管理系统</span>
        </div>
      </div>

      <!-- 菜单搜索 -->
      <div class="nav-search">
        <el-icon class="ns-ic"><Search /></el-icon>
        <input v-model="searchKeyword" class="ns-input" placeholder="搜索菜单（回车跳转）" @keydown="onSearchKeydown" />
        <button v-if="searching" class="ns-clear" @click="clearSearch" title="清空（Esc）">✕</button>
      </div>

      <nav class="sidebar-nav">
        <!-- 工作台 -->
        <router-link to="/" class="nav-item top" :class="{ active: route.path === '/' }">
          <el-icon><HomeFilled /></el-icon>
          <span>工作台</span>
        </router-link>

        <!-- 分组（数据驱动；搜索中强制展开） -->
        <template v-for="g in visibleGroups" :key="g.key">
          <div class="nav-group" :class="{ 'is-collapsed': !searching && isGroupCollapsed(g.key) }">
            <div class="nav-group-title" @click="toggleGroup(g.key)">
              <el-icon><component :is="g.icon" /></el-icon><span class="gt-text">{{ g.title }}</span>
              <el-icon class="gt-arrow"><ArrowDown /></el-icon>
            </div>
            <div class="nav-group-body"><div class="ngb-inner">
              <router-link v-for="i in g.items" :key="i.path" :to="i.path" class="nav-item sub" :class="{ active: route.path === i.path }">
                <el-icon class="sub-ic"><component :is="i.icon" /></el-icon>{{ i.title }}
              </router-link>
            </div></div>
          </div>
        </template>

        <div v-if="searching && !matchedItems.length" class="nav-empty">无匹配菜单</div>
      </nav>

      <div class="sidebar-footer">
        <div class="user-info" @click="logout">
          <span class="user-avatar">{{ (user?.realName||'?').charAt(0) }}</span>
          <div>
            <div class="user-name">{{ user?.realName || '未登录' }}</div>
            <div class="user-role">{{ user?.role }}</div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-area">
      <header class="topbar">
        <button class="mobile-menu-btn" @click="sidebarOpen = !sidebarOpen">
          <el-icon size="20"><component :is="'expand'" /></el-icon>
        </button>
        <div class="topbar-breadcrumb">
          <span v-if="route.path !== '/'">{{ pageTitle }}</span>
          <span v-else>工作台</span>
        </div>
        <div class="topbar-right">
          <el-dropdown trigger="click" @command="setTheme" @visible-change="onThemeMenu">
            <button class="theme-btn" title="切换风格" aria-label="切换风格">
              <el-icon><Brush /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu class="theme-menu">
                <el-dropdown-item v-for="t in themes" :key="t.id" :command="t.id" @mouseenter="previewTheme(t.id)">
                  <span class="theme-option" :class="{ active: current === t.id }">
                    <span class="theme-swatches">
                      <i v-if="t.id === 'glass'" class="glass-chip"></i>
                      <i v-else-if="t.id === 'skeuo'" class="skeuo-chip"></i>
                      <i v-else-if="t.id === 'clay'" class="clay-chip"></i>
                      <i v-else-if="t.id === 'pixel'" class="pixel-chip"></i>
                      <template v-else>
                        <i :style="{ background: t.light }"></i>
                        <i :style="{ background: t.primary }"></i>
                        <i :style="{ background: t.dark }"></i>
                      </template>
                    </span>
                    <span class="theme-name">{{ t.name }}</span>
                    <el-icon v-if="current === t.id" class="theme-check"><Check /></el-icon>
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <div class="topbar-time">{{ now }}</div>
        </div>
      </header>
      <!-- v5.51 多页签：已打开页面在顶部标签栏，点击切换，可关闭 -->
      <div class="tab-bar" v-if="tabs.length">
        <div class="tab-scroll">
          <div v-for="t in tabs" :key="t.path" class="tab-item" :class="{ active: t.path === route.path }"
            @click="router.push(t.path)" @mousedown="onTabMouseDown($event, t.path)">
            <span class="tab-title">{{ t.title }}</span>
            <span class="tab-close" v-if="t.path !== '/'" @click.stop="closeTab(t.path)" title="关闭">×</span>
          </div>
        </div>
        <div class="tab-ops" v-if="tabs.length > 1">
          <button class="tab-op" @click="closeOthers" title="除当前页外全部关闭">收起其他</button>
          <button class="tab-op" @click="closeAll" title="关闭全部页签，回到工作台">全部关闭</button>
        </div>
      </div>
      <div class="page-content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { HomeFilled, Operation, List, Box, TrendCharts, Connection, User, Key, Collection, Notebook, Folder, ShoppingCart, SetUp, Setting, ArrowDown, DataAnalysis, Checked, Money, Van, Avatar, Ticket, House, MagicStick, Document, Upload, Download, ShoppingTrolley, Position, RefreshLeft, Search, Memo, Coin, WalletFilled, Wallet, Odometer, PieChart, Histogram, DataLine, Files, Link, Bell, AlarmClock, CircleCheck, Stopwatch, CreditCard, Aim, Brush, Warning, Check, Calendar, Tickets, Stamp, Grid, Suitcase, OfficeBuilding, PriceTag } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useTheme } from '../composables/useTheme'

const { themes, current, setTheme, previewTheme, cancelPreview } = useTheme()
// 下拉关闭且未选择时还原预览
function onThemeMenu(visible) {
  if (!visible) cancelPreview()
}

const router = useRouter()
const route = useRoute()
const user = ref(null)
const perms = ref([])
const sidebarOpen = ref(false)

// 分组收缩/展开状态（持久化）
const groupCollapsed = ref(JSON.parse(localStorage.getItem('nav-group-collapsed') || '{}'))
function isGroupCollapsed(key) { return !!groupCollapsed.value[key] }
function toggleGroup(key) {
  groupCollapsed.value[key] = !groupCollapsed.value[key]
  localStorage.setItem('nav-group-collapsed', JSON.stringify(groupCollapsed.value))
}

// ==================== 导航菜单数据（搜索/权限/渲染共用） ====================
// icon 为 @element-plus/icons-vue 组件对象（模板中用 <component :is="..."> 动态渲染）
const NAV_GROUPS = [
  { key: 'basic', title: '基础数据', icon: Folder,
    permAny: ['supplier:read', 'customer:read', 'material:read', 'warehouse:read'],
    items: [
      { path: '/supplier', title: '供应商', icon: Van, perm: 'supplier:read' },
      { path: '/customer', title: '客户', icon: Avatar, perm: 'customer:read' },
      { path: '/material', title: '物料', icon: Box, perm: 'material:read' },
      { path: '/coding-rule', title: '编码规则', icon: Ticket, perm: 'material:read' },
      { path: '/packaging-standard', title: '包装标准', icon: Box, perm: 'material:read' },
      { path: '/warehouse', title: '仓库', icon: House, perm: 'warehouse:read' }
    ] },
  { key: 'production', title: '生产管理', icon: SetUp,
    permAny: ['production:read', 'recipe:read', 'process:read', 'qc:read'],
    items: [
      { path: '/recipe', title: '配方管理', icon: MagicStick, perm: 'recipe:read' },
      { path: '/process', title: '工艺路线', icon: Operation, perm: 'process:read' },
      { path: '/schedule', title: '排产中心', icon: Calendar, perm: 'production:write' },
      { path: '/production-order', title: '生产订单', icon: Document, perm: 'production:read' },
      { path: '/abnormal-order', title: '异常订单处理', icon: Warning, perm: 'production:read' },
      { path: '/production-outbound', title: '生产领料', icon: List, perm: 'production:read' },
      { path: '/production-inbound', title: '生产入库', icon: Download, perm: 'production:write' },
      { path: '/quality-inspection', title: '质检管理', icon: Checked, perm: 'qc:read' },
      { path: '/quality-statistics', title: '质量统计', icon: DataAnalysis, perm: 'qc:read' },
      { path: '/qc-template', title: '质检模板', icon: Files, perm: 'qc:read' }
    ] },
  { key: 'purchase', title: '采购管理', icon: ShoppingCart,
    permAny: ['purchase:read', 'strace:read'],
    items: [
      { path: '/raw-material-purchase', title: '原料采购', icon: ShoppingCart, perm: 'purchase:read' },
      { path: '/finished-product-purchase', title: '成品采购', icon: ShoppingTrolley, perm: 'purchase:read' },
      { path: '/purchase-arrival', title: '采购到货', icon: Position, perm: 'purchase:read' },
      { path: '/mrp-suggest', title: '采购建议 MRP', icon: ShoppingCart, perm: 'purchase:read' },
      { path: '/return-order', title: '采购退货单', icon: RefreshLeft, perm: 'purchase:read' },
      { path: '/supplier-quality-trace', title: '质量追溯', icon: Warning, perm: 'strace:read' }
    ] },
  { key: 'inventory', title: '库存中心', icon: Box,
    permAny: ['inventory:read'],
    items: [
      { path: '/inventory', title: '库存查询', icon: Search, perm: 'inventory:read' },
      { path: '/stock-check', title: '盘库管理', icon: Memo, perm: 'inventory:write' },
      { path: '/other-outbound', title: '其他出库', icon: Upload, perm: 'inventory:write' },
      { path: '/other-inbound', title: '其他入库', icon: Download, perm: 'inventory:write' }
    ] },
  { key: 'sales', title: '销售管理', icon: TrendCharts,
    permAny: ['sales:read', 'crm:read', 'sample:read', 'complaint:read'],
    items: [
      { path: '/crm-pipeline', title: '商机管道', icon: MagicStick, perm: 'crm:read' },
      { path: '/crm-contact', title: '联系人', icon: Avatar, perm: 'crm:read' },
      { path: '/quotation', title: '报价单', icon: Ticket, perm: 'sales:read' },
      { path: '/price-policy', title: '价格政策', icon: PriceTag, perm: 'sales:read' },
      { path: '/sample', title: '打样样品', icon: Brush, perm: 'sample:read' },
      { path: '/sales', title: '销售订单', icon: Notebook, perm: 'sales:read' },
      { path: '/sales-outbound', title: '销售出库', icon: Upload, perm: 'sales:write' },
      { path: '/shipping', title: '物流运费', icon: Van, perm: 'sales:read' },
      { path: '/sales-return', title: '销售退货', icon: RefreshLeft, perm: 'sales:write' },
      { path: '/tailing-return', title: '油尾退回', icon: RefreshLeft, perm: 'sales:write' },
      { path: '/complaint', title: '客户投诉', icon: Warning, perm: 'complaint:read' }
    ] },
  { key: 'outsource', title: '委外管理', icon: Connection,
    permAny: ['outsource:read'],
    items: [
      { path: '/outsource', title: '委外订单', icon: Connection, perm: 'outsource:read' },
      { path: '/outsource-outbound', title: '委外出库', icon: Upload, perm: 'outsource:write' },
      { path: '/outsource-inbound', title: '委外入库', icon: Download, perm: 'outsource:write' }
    ] },
  { key: 'finance', title: '财务管理', icon: Money,
    permAny: ['finance:read'],
    items: [
      { path: '/voucher', title: '会计凭证', icon: Tickets, perm: 'finance:read' },
      { path: '/period-close', title: '期末结账', icon: Stopwatch, perm: 'finance:read' },
      { path: '/salary', title: '工资管理', icon: Suitcase, perm: 'finance:read' },
      { path: '/asset', title: '固定资产', icon: OfficeBuilding, perm: 'finance:read' },
      { path: '/employee', title: '员工档案', icon: User, perm: 'finance:read' },
      { path: '/payment-receipt', title: '收款单', icon: Coin, perm: 'finance:read' },
      { path: '/payment-disbursement', title: '付款单', icon: Wallet, perm: 'finance:read' },
      { path: '/invoice', title: '发票管理', icon: Ticket, perm: 'finance:read' },
      { path: '/expense', title: '费用管理', icon: CreditCard, perm: 'finance:read' },
      { path: '/advance', title: '预收预付', icon: WalletFilled, perm: 'finance:read' },
      { path: '/cost-accounting', title: '成本核算', icon: DataAnalysis, perm: 'finance:read' },
      { path: '/costing-settings', title: '计价设置', icon: Coin, perm: 'finance:read' },
      { path: '/account-subject', title: '科目设置', icon: Notebook, perm: 'finance:read' },
      { path: '/opening-balance', title: '期初建账', icon: Stamp, perm: 'finance:read' }
    ] },
  { key: 'report', title: '报表中心', icon: DataAnalysis,
    permAny: ['purchase:read', 'inventory:read', 'finance:read', 'sales:read', 'qc:read', 'outsource:read', 'production:read'],
    items: [
      // v5.50.1 报表聚堆：经营 → 销售 → 采购 → 库存 → 生产（含委外/质检）→ 财务
      { path: '/report-overview', title: '经营看板', icon: Odometer, perm: 'finance:read' },
      { path: '/report-sales', title: '销售报表', icon: TrendCharts, perm: 'sales:read' },
      { path: '/report-purchase', title: '采购报表', icon: PieChart, perm: 'purchase:read' },
      { path: '/report-purchase-analysis', title: '采购分析', icon: DataAnalysis, perm: 'purchase:read' },
      { path: '/report-inventory', title: '库存报表', icon: Histogram, perm: 'inventory:read' },
      { path: '/report-stock-analysis', title: '库存分析', icon: DataLine, perm: 'inventory:read' },
      { path: '/report-turnover', title: '库存周转率', icon: TrendCharts, perm: 'inventory:read' },
      { path: '/report-low-stock', title: '低库存预警', icon: Bell, perm: 'inventory:read' },
      { path: '/report-expiry', title: '过期预警', icon: AlarmClock, perm: 'inventory:read' },
      { path: '/report-production', title: '生产报表', icon: Files, perm: 'inventory:read' },
      { path: '/report-material-variance', title: '领料差异分析', icon: Histogram, perm: 'production:read' },
      { path: '/production-progress', title: '生产进度表', icon: Odometer, perm: 'inventory:read' },
      { path: '/report-outsource', title: '委外报表', icon: Link, perm: 'outsource:read' },
      { path: '/report-qc', title: '质检报表', icon: CircleCheck, perm: 'qc:read' },
      { path: '/report-balance-sheet', title: '资产负债表', icon: DataAnalysis, perm: 'finance:read' },
      { path: '/report-income-statement', title: '利润表', icon: TrendCharts, perm: 'finance:read' },
      { path: '/report-cash-flow', title: '现金流量表', icon: Money, perm: 'finance:read' },
      { path: '/report-account-balance', title: '科目余额表', icon: Grid, perm: 'finance:read' },
      { path: '/report-account-detail', title: '明细账', icon: List, perm: 'finance:read' },
      { path: '/report-aging', title: '账龄分析', icon: Stopwatch, perm: 'finance:read' },
      { path: '/report-profit-trial', title: '利润试算', icon: TrendCharts, perm: 'finance:read' },
      { path: '/customer-statement', title: '客户对账单', icon: Document, perm: 'finance:read' },
      { path: '/supplier-statement', title: '供应商对账单', icon: Document, perm: 'finance:read' },
      { path: '/report-ar', title: '应收明细', icon: Money, perm: 'finance:read' },
      { path: '/report-ap', title: '应付明细', icon: CreditCard, perm: 'finance:read' },
      { path: '/report-ar-total', title: '应收总表', icon: Coin, perm: 'finance:read' },
      { path: '/report-ap-total', title: '应付总表', icon: Wallet, perm: 'finance:read' },
      { path: '/report-finance-trend', title: '趋势分析', icon: Aim, perm: 'finance:read' }
    ] },
  { key: 'collab', title: '协同办公', icon: Memo,
    permAny: ['meeting:read', 'ai:read', 'log:read', 'task:read'],
    items: [
      { path: '/task', title: '任务督办', icon: Aim, perm: 'task:read' },
      { path: '/weekly-topic', title: '每周议题', icon: Notebook, perm: 'meeting:read' },
      { path: '/rd-progress', title: '研发进度', icon: MagicStick, perm: 'meeting:read' },
      { path: '/ai', title: 'AI 智能助手', icon: MagicStick, perm: 'ai:read' },
      { path: '/logs', title: '操作日志', icon: Document, perm: 'log:read' }
    ] },
  { key: 'system', title: '系统设置', icon: Setting,
    permAny: ['user:read', 'dict:read', 'ai:write'],
    items: [
      { path: '/users', title: '用户管理', icon: User, perm: 'user:read' },
      { path: '/roles', title: '角色权限', icon: Key, perm: 'user:read' },
      { path: '/dict', title: '数据字典', icon: Collection, perm: 'dict:read' },
      { path: '/ai-settings', title: 'AI 设置', icon: Connection, perm: 'ai:write' }
    ] }
]

// 菜单搜索：就地过滤（匹配菜单名或分组名），回车跳第一个，Esc/✕ 清空恢复
const searchKeyword = ref('')
const searching = computed(() => searchKeyword.value.trim() !== '')

const visibleGroups = computed(() => {
  if (!searching.value) return NAV_GROUPS.filter(g => g.permAny.some(p => hasPerm(p)))
  const kw = searchKeyword.value.trim().toLowerCase()
  return NAV_GROUPS
    .filter(g => g.permAny.some(p => hasPerm(p)))
    .map(g => {
      const items = g.items.filter(i => hasPerm(i.perm) && i.title.toLowerCase().includes(kw))
      const groupHit = g.title.toLowerCase().includes(kw)
      return { ...g, items, groupHit }
    })
    .filter(g => g.groupHit || g.items.length > 0)
})

const matchedItems = computed(() => visibleGroups.value.flatMap(g => g.items.map(i => ({ ...i, group: g.title }))))

function onSearchKeydown(e) {
  if (e.key === 'Enter') {
    const first = matchedItems.value[0]
    if (first) { router.push(first.path); searchKeyword.value = '' }
  } else if (e.key === 'Escape') {
    searchKeyword.value = ''
  }
}
function clearSearch() { searchKeyword.value = '' }

// v5.55.1：标题以菜单数据（NAV_GROUPS）为单一数据源——新增菜单项自动带中文名，
// 不再需要同步维护两张表（此前 /recipe、/process、/qc-template 漏配导致页签显示英文路径）。
// 旧映射表降级为非菜单路由的兜底。
const MENU_TITLE_BY_PATH = NAV_GROUPS.reduce((m, g) => {
  for (const it of (g.items || [])) m[it.path] = it.title
  return m
}, {})

const pageTitle = computed(() => {
  const map = {
    '/': '工作台', '/supplier': '供应商', '/customer': '客户', '/material': '物料',
    '/warehouse': '仓库', '/inventory': '库存查询', '/purchase': '采购管理',
    '/raw-material-purchase': '原料采购', '/finished-product-purchase': '成品采购',
    '/purchase-arrival': '采购到货', '/mrp-suggest': '采购建议 MRP', '/sales': '销售管理', '/outsource': '委外管理',
    '/report-ar': '应收明细', '/report-ap': '应付明细', '/report-ar-total': '应收总表', '/report-ap-total': '应付总表', '/report-finance-trend': '趋势分析', '/payment-receipt': '收款单', '/payment-disbursement': '付款单', '/crm-pipeline': '商机管道', '/crm-contact': '联系人', '/quotation': '报价单', '/price-policy': '价格政策', '/sample': '打样样品', '/complaint': '客户投诉', '/weekly-topic': '每周议题', '/rd-progress': '研发进度', '/invoice': '发票管理', '/expense': '费用管理', '/advance': '预收预付', '/cost-accounting': '成本核算', '/report-profit-trial': '利润试算', '/customer-statement': '客户对账单', '/supplier-statement': '供应商对账单', '/users': '用户管理', '/roles': '角色权限', '/dict': '数据字典', '/ai': 'AI 智能助手', '/ai-settings': 'AI 设置', '/logs': '操作日志', '/coding-rule': '编码规则',
    '/stock-check': '盘库管理', '/schedule': '排产中心', '/production-order': '生产订单', '/abnormal-order': '异常订单处理', '/production-outbound': '生产出库', '/production-inbound': '生产入库', '/sales-outbound': '销售出库',
    '/outsource-outbound': '委外出库', '/outsource-inbound': '委外入库', '/other-outbound': '其他出库', '/other-inbound': '其他入库', '/return-order': '采购退货单', '/supplier-quality-trace': '质量追溯（供应商）', '/sales-return': '销售退货', '/tailing-return': '油尾退回',
    '/report-purchase': '采购报表', '/report-inventory': '库存报表', '/report-production': '生产报表', '/production-progress': '生产进度表',
    '/report-sales': '销售报表', '/report-qc': '质检报表', '/report-aging': '账龄分析',
    '/report-stock-analysis': '库存分析', '/report-turnover': '库存周转率', '/report-purchase-analysis': '采购分析', '/report-low-stock': '低库存预警', '/report-expiry': '过期预警',
    '/report-outsource': '委外报表', '/report-overview': '经营看板',
    '/quality-inspection': '质检管理', '/quality-statistics': '质量统计'
  }
  return MENU_TITLE_BY_PATH[route.path] || map[route.path] || route.path.replace('/','')
})
const now = ref('')

// ===== v5.51 多页签 =====
const tabs = ref([])
try { tabs.value = JSON.parse(localStorage.getItem('pims-tabs') || '[]').filter(t => t && t.path) } catch { tabs.value = [] }
function saveTabs() { localStorage.setItem('pims-tabs', JSON.stringify(tabs.value)) }
function addCurrentTab() {
  if (route.path === '/login') return
  const title = route.path === '/' ? '工作台' : pageTitle.value
  const exist = tabs.value.find(t => t.path === route.path)
  if (!exist) {
    tabs.value.push({ path: route.path, title })
    saveTabs()
  } else if (exist.title !== title) {
    exist.title = title  // v5.55.1：自愈历史页签的过期标题（如曾显示英文路径的 recipe）
    saveTabs()
  }
}
function closeTab(path) {
  const idx = tabs.value.findIndex(t => t.path === path)
  if (idx < 0) return
  tabs.value.splice(idx, 1)
  saveTabs()
  if (route.path === path) {
    const next = tabs.value[Math.min(idx, tabs.value.length - 1)]
    if (next) router.push(next.path)
  }
}
function closeOthers() {
  tabs.value = tabs.value.filter(t => t.path === route.path || t.path === '/')
  saveTabs()
}
function closeAll() {
  tabs.value = [{ path: '/', title: '工作台' }]
  saveTabs()
  if (route.path !== '/') router.push('/')
}
function onTabMouseDown(e, path) {
  if (e.button === 1 && path !== '/') { e.preventDefault(); closeTab(path) }
}
import { watch } from 'vue'
watch(() => route.path, () => addCurrentTab(), { immediate: true })
const ready = ref(false)

function hasPerm(code) { return perms.value.includes(code) }

let timer
onMounted(async () => {
  // v6.1 安全：首次登录/重置后强制改密
  // v6.1.1 修复：不再把明文密码存 localStorage（login-pwd，XSS 可直接窃取）——改为改密时现场输入原密码验证
  try {
    const me = JSON.parse(localStorage.getItem('user') || '{}')
    if (me.mustChangePwd === true) {
      let done = false
      while (!done) {
        const { value: oldPwd } = await ElMessageBox.prompt('请输入当前密码以验证身份', '强制修改密码（1/2）', {
          confirmButtonText: '下一步', showCancelButton: false, closeOnClickModal: false, closeOnPressEscape: false,
          inputType: 'password', inputPattern: /^.+$/, inputErrorMessage: '请输入当前密码'
        }).catch(() => ({ value: null }))
        if (!oldPwd) break   // 取消则下次进入再弹（mustChangePwd 仍为 true）
        const { value: newPwd } = await ElMessageBox.prompt('请设置新密码（至少 6 位）', '强制修改密码（2/2）', {
          confirmButtonText: '修改', showCancelButton: false, closeOnClickModal: false, closeOnPressEscape: false,
          inputType: 'password', inputPattern: /^.{6,}$/, inputErrorMessage: '密码至少 6 位'
        }).catch(() => ({ value: null }))
        if (!newPwd) continue
        try {
          await api.post('/auth/change-password', { oldPassword: oldPwd, newPassword: newPwd })
          ElMessage.success('密码已修改')
          done = true
          const me2 = JSON.parse(localStorage.getItem('user') || '{}')
          me2.mustChangePwd = false
          localStorage.setItem('user', JSON.stringify(me2))
        } catch (e) {
          ElMessage.error((e && e.response && e.response.data && e.response.data.msg) || '原密码不正确，请重试')
        }
      }
    }
  } catch {}
  try {
    user.value = await api.get('/auth/info')
    perms.value = user.value.permissions || []
    localStorage.setItem('user', JSON.stringify(user.value))
    ready.value = true
  } catch (e) {
    localStorage.removeItem('pims-token')
    localStorage.removeItem('user')
    router.replace('/login')
    return
  }
  updateTime()
  timer = setInterval(updateTime, 30000)
})

onUnmounted(() => clearInterval(timer))

function updateTime() {
  const d = new Date()
  now.value = d.toLocaleDateString('zh-CN', { year:'numeric', month:'2-digit', day:'2-digit', weekday:'short' }) +
    ' ' + d.toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' })
}

function logout() {
  localStorage.removeItem('pims-token')
  localStorage.removeItem('user')
  router.replace('/login')
}
</script>

<style scoped>
/* ===== 整体布局 ===== */
.app-layout {
  display: flex; height: 100vh;
  /* 平色底上叠加极淡主题色渐变，增加空间纵深 */
  background:
    radial-gradient(1200px 420px at 85% -10%, rgba(var(--pims-primary-rgb),0.06), transparent 60%),
    radial-gradient(900px 360px at 15% -15%, rgba(var(--pims-primary-light-rgb),0.05), transparent 55%),
    var(--pims-layout-bg);
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: var(--pims-sidebar-width);
  background: linear-gradient(180deg, var(--pims-sidebar-start) 0%, var(--pims-sidebar-mid) 50%, var(--pims-sidebar-start) 100%);
  transition: background 0.3s ease;
  display: flex; flex-direction: column;
  box-shadow: 2px 0 24px rgba(0,0,0,0.22);
  position: relative; z-index: 10;
  flex-shrink: 0;
}

.sidebar-brand {
  height: 68px;
  display: flex; align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  gap: 12px;
}

.brand-mark {
  width: 36px; height: 36px;
  background: linear-gradient(135deg, var(--pims-primary-light), var(--pims-primary));
  border-radius: 10px;
  box-shadow: 0 4px 14px rgba(var(--pims-primary-rgb),0.4);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 800; font-size: 16px;
  flex-shrink: 0;
}

.brand-text { display: flex; flex-direction: column; }
.brand-name { font-size: 16px; font-weight: 700; color: #fff; letter-spacing: 1px; line-height: 1.2; }
.brand-sub { font-size: 10px; color: rgba(255,255,255,0.4); letter-spacing: 0.5px; }

/* ===== 菜单搜索 ===== */
.nav-search {
  display: flex; align-items: center; gap: 6px;
  margin: 0 10px 6px;
  padding: 7px 10px;
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 9px;
  transition: background 0.18s ease, border-color 0.18s ease;
}
.nav-search:focus-within {
  background: rgba(255,255,255,0.12);
  border-color: rgba(255,255,255,0.25);
}
.ns-ic { font-size: 14px; color: rgba(255,255,255,0.45); flex-shrink: 0; }
.ns-input {
  flex: 1; min-width: 0;
  background: transparent; border: none; outline: none;
  color: #fff; font-size: 12.5px;
}
.ns-input::placeholder { color: rgba(255,255,255,0.35); }
.ns-clear {
  border: none; background: none; cursor: pointer;
  color: rgba(255,255,255,0.45); font-size: 12px;
  padding: 0 2px; line-height: 1;
}
.ns-clear:hover { color: #fff; }
.nav-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 12.5px;
  color: rgba(255,255,255,0.3);
}

/* ===== 导航 ===== */
.sidebar-nav {
  flex: 1; overflow-y: auto;
  padding: 14px 10px;
}

.sidebar-nav::-webkit-scrollbar { width: 3px; }
.sidebar-nav::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.12); border-radius: 2px; }

/* 分组标题（可点击收缩/展开） */
.nav-group { margin-bottom: 4px; }
.nav-group-title {
  display: flex; align-items: center; gap: 7px;
  font-size: 11px; font-weight: 600;
  color: rgba(255,255,255,0.38);
  letter-spacing: 1px;
  padding: 16px 12px 6px;
  text-transform: uppercase;
  cursor: pointer;
  user-select: none;
  border-radius: 8px;
  transition: color 0.18s ease, background 0.18s ease;
}
.nav-group-title:hover { color: rgba(255,255,255,0.75); background: rgba(255,255,255,0.04); }
.nav-group-title .el-icon { font-size: 13px; opacity: 0.7; }
.nav-group-title .gt-text { flex: 1; }
.nav-group-title .gt-arrow {
  font-size: 11px; opacity: 0.55;
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.nav-group.is-collapsed .gt-arrow { transform: rotate(-90deg); }

/* 分组子菜单收缩动画（grid 单层包裹，确保整体折叠无空隙） */
.nav-group-body {
  display: grid;
  grid-template-rows: 1fr;
  transition: grid-template-rows 0.28s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.22s ease;
  opacity: 1;
}
.ngb-inner { min-height: 0; overflow: hidden; }
.nav-group.is-collapsed .nav-group-body {
  grid-template-rows: 0fr;
  opacity: 0;
}

/* 导航项通用 */
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 14px;
  margin-bottom: 2px;
  border-radius: 9px;
  font-size: 13.5px; color: rgba(255,255,255,0.7);
  text-decoration: none;
  transition: all 0.18s ease;
  position: relative;
}

.nav-item:hover {
  background: rgba(255,255,255,0.07);
  color: #fff;
  transform: translateX(2px);
}

.nav-item.active {
  background: linear-gradient(135deg, rgba(99,102,241,0.3), rgba(129,140,248,0.18));
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 10px rgba(99,102,241,0.15);
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0; top: 50%;
  transform: translateY(-50%);
  width: 3px; height: 55%;
  background: linear-gradient(180deg, var(--pims-primary-light), var(--pims-primary));
  border-radius: 0 3px 3px 0;
}

/* 顶级导航项 */
.nav-item.top { margin-bottom: 3px; }
.nav-item.top .el-icon { font-size: 17px; flex-shrink: 0; opacity: 0.85; }

/* 子级导航项 */
.nav-item.sub {
  padding: 8px 14px 8px 20px;
  font-size: 13px;
  gap: 9px;
}

/* 子菜单 SVG 图标 */
.nav-item.sub .sub-ic {
  font-size: 15px;
  opacity: 0.65;
  flex-shrink: 0;
  transition: all 0.18s ease;
}
.nav-item.sub:hover .sub-ic { opacity: 1; }
.nav-item.sub.active .sub-ic {
  opacity: 1;
  color: var(--pims-primary-light);
  filter: drop-shadow(0 0 4px rgba(var(--pims-primary-light-rgb),0.5));
}

/* ===== 侧边栏底部 ===== */
.sidebar-footer {
  padding: 14px 16px;
  border-top: 1px solid rgba(255,255,255,0.06);
}

.user-info {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 10px; border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
}

.user-info:hover { background: rgba(255,255,255,0.07); }

.user-avatar {
  width: 34px; height: 34px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, var(--pims-primary), var(--pims-primary-light));
  border-radius: 10px;
  color: #fff; font-weight: 700; font-size: 14px;
  flex-shrink: 0;
}

.user-name { font-size: 13px; color: #fff; font-weight: 500; }
.user-role { font-size: 11px; color: rgba(255,255,255,0.4); margin-top: 1px; }

/* ===== 主内容区 ===== */
.main-area {
  flex: 1; display: flex; flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* ===== 顶部栏 ===== */
.topbar {
  height: 56px;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 28px;
  background: var(--pims-topbar-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--pims-topbar-border);
  box-shadow: 0 1px 4px rgba(0,0,0,0.03);
  flex-shrink: 0;
}

.topbar-breadcrumb {
  font-size: 15px; font-weight: 600;
  color: var(--pims-text); letter-spacing: 0.3px;
}

.topbar-time {
  font-size: 13px; color: var(--pims-text-secondary);
  font-variant-numeric: tabular-nums;
}

.topbar-right {
  display: flex; align-items: center; gap: 14px;
}

/* 配色切换按钮 */
.theme-btn {
  display: flex; align-items: center; justify-content: center;
  width: 34px; height: 34px;
  border: none; border-radius: 10px;
  background: rgba(var(--pims-primary-rgb),0.1);
  color: var(--pims-primary);
  font-size: 17px;
  cursor: pointer;
  transition: all 0.2s;
}
.theme-btn:hover {
  background: rgba(var(--pims-primary-rgb),0.18);
  transform: translateY(-1px);
}

/* ===== 页面内容 ===== */
.page-content {
  flex: 1; overflow-y: auto;
  padding: var(--pims-page-padding-y) var(--pims-page-padding-x);
  -webkit-overflow-scrolling: touch;
}

/* ===== 移动端菜单按钮 ===== */
.mobile-menu-btn {
  display: none;
  background: none;
  border: none;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  color: var(--pims-text);
  transition: background var(--pims-transition-fast);
}
.mobile-menu-btn:hover {
  background: rgba(0,0,0,0.05);
}

/* 遮罩层 */
.sidebar-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.45);
  z-index: 9;
  opacity: 0;
  transition: opacity var(--pims-transition-base);
  backdrop-filter: blur(2px);
}
.sidebar-overlay.visible {
  display: block;
  opacity: 1;
}

/* 平板断点 */
@media (max-width: 1024px) {
  .sidebar {
    width: 210px;
  }
  .topbar {
    padding: 0 20px;
  }
}

/* 手机断点 */
@media (max-width: 768px) {
  .mobile-menu-btn {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 10;
    transform: translateX(-100%);
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    width: var(--pims-sidebar-width);
  }

  .sidebar.open {
    transform: translateX(0);
  }

  .main-area {
    margin-left: 0;
  }

  .topbar {
    padding: 0 16px;
  }

  .topbar-time {
    display: none;
  }

  .page-content {
    padding: var(--pims-page-padding-y) var(--pims-page-padding-x);
  }
}
/* v5.51.1 经典标签页样式（浏览器风）：标签嵌在灰条里、激活页与内容区连通+主题色顶条，与页面按钮彻底区分 */
.tab-bar { display: flex; align-items: flex-end; gap: 8px; padding: 8px 12px 0; background: #e2e5eb; border-bottom: 1px solid #d3d8e0; }
:root[data-theme] .tab-bar, [class*=dark] .tab-bar { background: rgba(100, 116, 139, .18); border-bottom-color: rgba(100, 116, 139, .3); }
.tab-scroll { display: flex; align-items: flex-end; gap: 2px; overflow-x: auto; flex: 1; scrollbar-width: thin; }
.tab-scroll::-webkit-scrollbar { height: 3px; }
.tab-scroll::-webkit-scrollbar-thumb { background: #b6bec9; border-radius: 2px; }
.tab-item { position: relative; display: inline-flex; align-items: center; justify-content: center; gap: 7px; flex: 0 1 160px; width: 160px; min-width: 84px; padding: 7px 10px; border-radius: 9px 9px 0 0; font-size: 13px; color: #5b6675; background: rgba(255, 255, 255, .45); cursor: pointer; user-select: none; border: none; transition: background .15s; overflow: hidden; }
.tab-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tab-item:hover { background: rgba(255, 255, 255, .75); color: #1e293b; }
.tab-item.active { background: var(--pims-card-bg, #fff); color: #1e293b; font-weight: 600; box-shadow: 0 -1px 4px rgba(15, 23, 42, .06); }
.tab-item.active::before { content: ''; position: absolute; top: 0; left: 10px; right: 10px; height: 3px; border-radius: 0 0 3px 3px; background: var(--pims-primary, #2563eb); }
.tab-close { display: inline-flex; align-items: center; justify-content: center; width: 17px; height: 17px; border-radius: 4px; font-size: 14px; color: #94a3b8; line-height: 1; }
.tab-close:hover { background: #ef4444; color: #fff; }
.tab-item.active .tab-close { color: #64748b; }
.tab-ops { flex-shrink: 0; display: flex; gap: 2px; margin-bottom: 5px; }
.tab-op { border: none; background: transparent; color: #64748b; font-size: 12px; padding: 3px 8px; border-radius: 4px; cursor: pointer; }
.tab-op:hover { color: var(--pims-primary, #2563eb); background: rgba(255, 255, 255, .6); }
</style>
