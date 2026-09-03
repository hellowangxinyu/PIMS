# 侧边栏菜单搜索实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 侧边栏品牌区下方新增搜索框，输入关键字就地过滤菜单（匹配菜单名或分组名），回车跳转，Esc/✕ 清空恢复。

**Architecture:** 把 Layout.vue 模板里硬编码的 45 个菜单项抽成数据驱动的 NAV_GROUPS 数组（路径/名称/图标/权限码），模板循环渲染；搜索过滤与权限过滤都在该数组上完成，分组折叠逻辑保留。纯前端，无后端改动。

**Tech Stack:** Vue3 + Element Plus（现有 Layout.vue 内改造）

**项目约定（必读）：**
- 无单测、非 git 仓库——验证方式为「前端构建 → 打包重启 → 浏览器/产物断言」
- 构建部署：`cd pims-web && npm run build` → 杀 8080 → `cd pims-server && mvn clean package -q -DskipTests` → 后台启动 jar → 验证
- 抽取菜单数据时**名称/路径/权限条件必须与现状逐一对应**，不许顺手改名；pageTitle map 保持独立不动

**Spec:** `docs/superpowers/specs/2026-08-12-sidebar-menu-search-design.md`

---

### Task 1: Layout.vue——菜单数据化 + 搜索框 + 就地过滤

**Files:**
- Modify: `pims-web/src/views/Layout.vue`

- [ ] **Step 1: script 区新增 NAV_GROUPS 数据与搜索状态**

在 `const pageTitle = computed(...)` 之前插入。**注意：icon 直接存组件对象**（`<script setup>` 下 `<component :is="'字符串'">` 无法解析，必须引用已 import 的组件变量）。现有 import 行缺 `Calendar`（排产中心图标一直未生效），本次补上：

```js
// 图标 import 行追加 Calendar：
// import { ..., Warning, Check, Calendar } from '@element-plus/icons-vue'

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
      { path: '/warehouse', title: '仓库', icon: House, perm: 'warehouse:read' }
    ] },
  { key: 'production', title: '生产管理', icon: SetUp,
    permAny: ['production:read', 'recipe:read', 'process:read'],
    items: [
      { path: '/recipe', title: '配方管理', icon: MagicStick, perm: 'recipe:read' },
      { path: '/process', title: '工艺路线', icon: Operation, perm: 'process:read' },
      { path: '/schedule', title: '排产中心', icon: Calendar, perm: 'production:write' },
      { path: '/production-order', title: '生产订单', icon: Document, perm: 'production:read' },
      { path: '/abnormal-order', title: '异常订单处理', icon: Warning, perm: 'production:read' },
      { path: '/production-outbound', title: '出库明细', icon: List, perm: 'production:read' },
      { path: '/production-inbound', title: '生产入库', icon: Download, perm: 'production:write' }
    ] },
  { key: 'purchase', title: '采购管理', icon: ShoppingCart,
    permAny: ['purchase:read'],
    items: [
      { path: '/raw-material-purchase', title: '原料采购', icon: ShoppingCart, perm: 'purchase:read' },
      { path: '/finished-product-purchase', title: '成品采购', icon: ShoppingTrolley, perm: 'purchase:read' },
      { path: '/purchase-arrival', title: '采购到货', icon: Position, perm: 'purchase:read' },
      { path: '/return-order', title: '采购退货单', icon: RefreshLeft, perm: 'purchase:read' }
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
    permAny: ['sales:read'],
    items: [
      { path: '/sales', title: '销售订单', icon: Notebook, perm: 'sales:read' },
      { path: '/sales-outbound', title: '销售出库', icon: Upload, perm: 'sales:write' },
      { path: '/sales-return', title: '销售退货', icon: RefreshLeft, perm: 'sales:write' }
    ] },
  { key: 'outsource', title: '委外管理', icon: Connection,
    permAny: ['outsource:read'],
    items: [
      { path: '/outsource', title: '委外订单', icon: Connection, perm: 'outsource:read' },
      { path: '/outsource-outbound', title: '委外出库', icon: Upload, perm: 'outsource:write' },
      { path: '/outsource-inbound', title: '委外入库', icon: Download, perm: 'outsource:write' }
    ] },
  { key: 'quality', title: '质量管理', icon: Checked,
    permAny: ['qc:read'],
    items: [
      { path: '/quality-inspection', title: '质检管理', icon: Checked, perm: 'qc:read' }
    ] },
  { key: 'finance', title: '财务管理', icon: Money,
    permAny: ['finance:read'],
    items: [
      { path: '/payment-receipt', title: '收款单', icon: Coin, perm: 'finance:read' },
      { path: '/payment-disbursement', title: '付款单', icon: Wallet, perm: 'finance:read' }
    ] },
  { key: 'report', title: '报表中心', icon: DataAnalysis,
    permAny: ['purchase:read', 'inventory:read', 'finance:read', 'sales:read', 'qc:read', 'outsource:read'],
    items: [
      { path: '/report-overview', title: '经营看板', icon: Odometer, perm: 'finance:read' },
      { path: '/report-sales', title: '销售报表', icon: TrendCharts, perm: 'sales:read' },
      { path: '/report-purchase', title: '采购报表', icon: PieChart, perm: 'purchase:read' },
      { path: '/report-purchase-analysis', title: '采购分析', icon: DataAnalysis, perm: 'purchase:read' },
      { path: '/report-inventory', title: '库存报表', icon: Histogram, perm: 'inventory:read' },
      { path: '/report-stock-analysis', title: '库存分析', icon: DataLine, perm: 'inventory:read' },
      { path: '/report-production', title: '生产报表', icon: Files, perm: 'inventory:read' },
      { path: '/production-progress', title: '生产进度表', icon: Odometer, perm: 'inventory:read' },
      { path: '/report-outsource', title: '委外报表', icon: Link, perm: 'outsource:read' },
      { path: '/report-low-stock', title: '低库存预警', icon: Bell, perm: 'inventory:read' },
      { path: '/report-expiry', title: '过期预警', icon: AlarmClock, perm: 'inventory:read' },
      { path: '/report-qc', title: '质检报表', icon: CircleCheck, perm: 'qc:read' },
      { path: '/report-aging', title: '账龄分析', icon: Stopwatch, perm: 'finance:read' },
      { path: '/report-ar', title: '应收明细', icon: Money, perm: 'finance:read' },
      { path: '/report-ap', title: '应付明细', icon: CreditCard, perm: 'finance:read' },
      { path: '/report-ar-total', title: '应收总表', icon: Coin, perm: 'finance:read' },
      { path: '/report-ap-total', title: '应付总表', icon: Wallet, perm: 'finance:read' },
      { path: '/report-finance-trend', title: '趋势分析', icon: Aim, perm: 'finance:read' }
    ] },
  { key: 'system', title: '系统设置', icon: Setting,
    permAny: ['user:read', 'dict:read', 'ai:read', 'log:read'],
    items: [
      { path: '/users', title: '用户管理', icon: User, perm: 'user:read' },
      { path: '/roles', title: '角色权限', icon: Key, perm: 'user:read' },
      { path: '/dict', title: '数据字典', icon: Collection, perm: 'dict:read' },
      { path: '/ai', title: 'AI 智能助手', icon: MagicStick, perm: 'ai:read' },
      { path: '/ai-settings', title: 'AI 设置', icon: Connection, perm: 'ai:write' },
      { path: '/logs', title: '操作日志', icon: Document, perm: 'log:read' }
    ] }
]

// 搜索状态
const searchKeyword = ref('')
const searching = computed(() => searchKeyword.value.trim() !== '')

// 搜索过滤：菜单名或分组名包含关键字；组内无匹配则整组隐藏
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

// 回车跳第一个匹配并清空；Esc 清空
function onSearchKeydown(e) {
  if (e.key === 'Enter') {
    const first = matchedItems.value[0]
    if (first) { router.push(first.path); searchKeyword.value = '' }
  } else if (e.key === 'Escape') {
    searchKeyword.value = ''
  }
}
function clearSearch() { searchKeyword.value = '' }
```

- [ ] **Step 2: 模板——品牌区下加搜索框**

`<nav class="sidebar-nav">` 之前插入：

```vue
      <!-- 菜单搜索 -->
      <div class="nav-search">
        <el-icon class="ns-ic"><Search /></el-icon>
        <input
          v-model="searchKeyword"
          class="ns-input"
          placeholder="搜索菜单（回车跳转）"
          @keydown="onSearchKeydown"
        />
        <button v-if="searching" class="ns-clear" @click="clearSearch" title="清空（Esc）">✕</button>
      </div>
```

- [ ] **Step 3: 模板——导航区改为数据驱动渲染**

把 `<nav class="sidebar-nav">` 内全部硬编码分组替换为：

```vue
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
```

- [ ] **Step 4: 样式——搜索框 + 空提示**

`/* ===== 导航 ===== */` 区块前插入：

```css
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
```

- [ ] **Step 5: 前端构建 + 整包部署 + 产物验证**

```bash
cd D:/开发/PIMS/pims-web && npm run build
# 杀 8080 → cd pims-server && mvn clean package -q -DskipTests → 后台启动 jar
# 产物验证：
grep -l "搜索菜单" pims-server/src/main/resources/static/assets/Layout-*.js   # 应有命中
grep -c "无匹配菜单" pims-server/src/main/resources/static/assets/Layout-*.js
```

- [ ] **Step 6: 浏览器验证（提醒用户 Ctrl+F5）**

1. 侧边栏品牌区下方出现搜索框
2. 输入"质检" → 出现「质量管理/质检管理」「报表中心/质检报表」两组且组自动展开
3. 输入"库存" → 库存查询/盘库管理/其他出库/其他入库/库存报表/库存分析/低库存预警/过期预警
4. 输入"aaaa" → 显示「无匹配菜单」
5. 回车跳转第一个匹配并清空；Esc/✕ 恢复原样（含折叠状态）
6. 无权限菜单不出现（用非 admin 账号抽查）

---

### Task 2: 部署测试服务器 + 记忆更新

- [ ] **Step 1: 部署测试服务器**

```bash
scp D:/开发/PIMS/pims-server/target/pims-server-1.0.0.jar yttuliao@192.168.11.241:/opt/pims/
ssh yttuliao@192.168.11.241 "echo 'yttuliao' | sudo -S systemctl restart pims && sleep 15 && curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/"
# 预期 302
```

- [ ] **Step 2: 更新记忆**

`installed-skills.md` 无关；新建 `sidebar-menu-search.md`：Layout.vue 菜单数据化（NAV_GROUPS）/搜索框就地过滤/回车跳转/Esc 清空/纯前端；MEMORY.md 加索引行。
