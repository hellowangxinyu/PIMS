<template>
  <div class="dashboard">
    <!-- 欢迎区 -->
    <div class="welcome-bar">
      <div>
        <h1 class="welcome-title">{{ greeting }}，{{ realName }}</h1>
        <p class="welcome-sub">芃远新材料综合管理系统 · 进销存</p>
      </div>
      <div class="date-badge">{{ today }}</div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div class="stat-card purple">
        <div class="stat-icon"><el-icon><OfficeBuilding /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ data.warehouseCount }}</div>
          <div class="stat-label">仓库</div>
        </div>
      </div>
      <div class="stat-card amber">
        <div class="stat-icon"><el-icon><Box /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ data.materialCount }}</div>
          <div class="stat-label">物料</div>
        </div>
        <div class="stat-spark">{{ data.materialCategoryCount }} 个大类</div>
      </div>
      <div class="stat-card emerald">
        <div class="stat-icon"><el-icon><Van /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ data.supplierCount }}</div>
          <div class="stat-label">供应商</div>
        </div>
      </div>
      <div class="stat-card rose">
        <div class="stat-icon"><el-icon><User /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ data.customerCount }}</div>
          <div class="stat-label">客户</div>
        </div>
      </div>
    </div>

    <!-- v5.47 待办聚合（管理层一眼看全） -->
    <div v-if="hasAnyTodo" class="todo-bar">
      <div class="todo-title"><el-icon><Bell /></el-icon> 待办事项</div>
      <div class="todo-items">
        <router-link v-if="todos.pendingQc" to="/quality-inspection" class="todo-chip warn">
          质检待判定 <b>{{ todos.pendingQc }}</b><span v-if="todos.pendingReinspect" class="todo-sub">（含复检 {{ todos.pendingReinspect }}）</span>
        </router-link>
        <router-link v-if="todos.pendingReturn" to="/return-order" class="todo-chip warn">退货单待审核 <b>{{ todos.pendingReturn }}</b></router-link>
        <router-link v-if="todos.pendingTailing" to="/tailing-return" class="todo-chip warn">油尾退回待确认 <b>{{ todos.pendingTailing }}</b></router-link>
        <router-link v-if="todos.dueArCount" to="/report-ar" class="todo-chip danger">到期应收 <b>{{ todos.dueArCount }}</b> 笔 / ¥{{ fmt(todos.dueArAmount) }}</router-link>
        <router-link v-if="todos.overdueTopics" to="/weekly-topic" class="todo-chip danger">会议议题逾期 <b>{{ todos.overdueTopics }}</b></router-link>
        <router-link v-if="todos.dueFollowUp" to="/crm-pipeline" class="todo-chip warn">今日待跟进 <b>{{ todos.dueFollowUp }}</b></router-link>
        <router-link v-if="todos.myOpenTasks" to="/task" class="todo-chip warn">我的任务 <b>{{ todos.myOpenTasks }}</b></router-link>
        <router-link v-if="todos.overdueTasks" to="/task" class="todo-chip danger">任务逾期 <b>{{ todos.overdueTasks }}</b></router-link>
        <span v-if="todos.backupStale" class="todo-chip danger" title="每日 04:30 自动备份；长时间未备份请检查服务运行">数据库备份异常（上次：{{ todos.lastBackup ? todos.lastBackup.substring(0,16) : '从未' }}）</span>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="section-row">
      <div class="section-title">快捷操作</div>
      <el-button link type="primary" @click="editingQuick = !editingQuick" size="small">
        {{ editingQuick ? '完成' : '编辑' }}
      </el-button>
    </div>
    <div class="quick-actions">
      <template v-for="(item, idx) in quickActions" :key="idx">
        <router-link :to="item.path" class="quick-card" v-if="!editingQuick">
          <span class="qc-icon" :style="{ color: item.color }">
            <el-icon v-if="quickIconComp(item.icon)"><component :is="quickIconComp(item.icon)" /></el-icon>
            <template v-else>{{ item.icon }}</template>
          </span>
          <span>{{ item.label }}</span>
        </router-link>
        <div class="quick-card editing" v-else>
          <span class="qc-icon" :style="{ color: item.color }">
            <el-icon v-if="quickIconComp(item.icon)"><component :is="quickIconComp(item.icon)" /></el-icon>
            <template v-else>{{ item.icon }}</template>
          </span>
          <span>{{ item.label }}</span>
          <el-button class="qc-del" circle size="small" @click="removeQuick(idx)" :icon="Close">×</el-button>
        </div>
      </template>
      <el-button v-if="editingQuick" class="quick-card add-btn" @click="showAddQuick">
        <span class="qc-icon">＋</span>
        <span>添加</span>
      </el-button>
    </div>

    <!-- 数据表格区 -->
    <div class="data-grid">
      <div class="data-card">
        <div class="data-card-title">最新采购订单 <span class="badge">{{ data.recentPurchases?.length || 0 }}</span></div>
        <p-table :data="data.recentPurchases" size="small" border empty-text="暂无数据" @header-dragend="onHeaderDragendP">
          <el-table-column prop="orderNo" label="单号" :width="cwP('单号') || 130" />
          <el-table-column prop="supplierName" label="供应商" :width="cwP('供应商') || 100" show-overflow-tooltip />
          <el-table-column prop="totalAmount" label="金额" :width="cwP('金额') || 100" v-if="hasPerm('purchase:price')" />
          <el-table-column prop="status" label="状态" :width="cwP('状态') || 90">
            <template #default="{row}"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
        </p-table>
      </div>

      <div class="data-card">
        <div class="data-card-title">最新销售订单 <span class="badge">{{ data.recentSales?.length || 0 }}</span></div>
        <p-table :data="data.recentSales" size="small" border empty-text="暂无数据" @header-dragend="onHeaderDragendS">
          <el-table-column prop="orderNo" label="单号" :width="cwS('单号') || 130" />
          <el-table-column prop="customerName" label="客户" :width="cwS('客户') || 100" show-overflow-tooltip />
          <el-table-column prop="totalAmount" label="金额" :width="cwS('金额') || 100" v-if="hasPerm('purchase:price')" />
          <el-table-column prop="status" label="状态" :width="cwS('状态') || 90">
            <template #default="{row}"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
        </p-table>
      </div>

      <div class="data-card">
        <div class="data-card-title">委外工单 <span class="badge">{{ data.recentOutsource?.length || 0 }}</span></div>
        <p-table :data="data.recentOutsource" size="small" border empty-text="暂无数据" @header-dragend="onHeaderDragendO">
          <el-table-column prop="orderNo" label="单号" :width="cwO('单号') || 130" />
          <el-table-column prop="processor" label="代工厂" :width="cwO('代工厂') || 120" show-overflow-tooltip />
          <el-table-column prop="batchQty" label="计划量" :width="cwO('计划量') || 80" />
          <el-table-column prop="status" label="状态" :width="cwO('状态') || 90">
            <template #default="{row}"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
        </p-table>
      </div>

      <div class="data-card">
        <div class="data-card-title">低库存预警 <span class="badge warn">{{ data.lowStock?.length || 0 }}</span></div>
        <p-table :data="data.lowStock" size="small" border empty-text="暂无预警" @header-dragend="onHeaderDragendL">
          <el-table-column prop="materialName" label="品名" :width="cwL('品名') || 140" show-overflow-tooltip />
          <!-- v5.34：预警按仓库计算，显示预警所在仓库 -->
          <el-table-column prop="warehouseName" label="仓库" :width="cwL('仓库') || 110" show-overflow-tooltip>
            <template #default="{row}"><span v-if="row.warehouseName">{{ row.warehouseName }}</span><span v-else class="ls-muted">-</span></template>
          </el-table-column>
          <el-table-column prop="currentQty" label="当前库存" :width="cwL('当前库存') || 90" />
          <el-table-column prop="availableDays" label="可用天数" :width="cwL('可用天数') || 90">
            <template #default="{row}"><span :class="row.level === 'RED' ? 'ls-red' : 'ls-orange'">{{ row.availableDays }} 天</span></template>
          </el-table-column>
          <el-table-column label="级别" :width="cwL('级别') || 70">
            <template #default="{row}"><el-tag :type="row.level === 'RED' ? 'danger' : 'warning'" size="small">{{ row.level === 'RED' ? '严重' : '预警' }}</el-tag></template>
          </el-table-column>
        </p-table>
      </div>
    </div>

    <!-- 应收应付汇总 -->
    <div class="data-grid bottom">
      <div class="data-card" v-if="hasPerm('finance:amount')">
        <div class="data-card-title">应收汇总</div>
        <div class="summary-row"><span>应收总额</span><strong>¥{{ fmt(data.arTotal) }}</strong></div>
        <div class="summary-row"><span>已收</span><strong class="green">¥{{ fmt(data.arReceived) }}</strong></div>
        <div class="summary-row"><span>待收</span><strong class="red">¥{{ fmt(data.arPending) }}</strong></div>
      </div>
      <div class="data-card" v-if="hasPerm('finance:amount')">
        <div class="data-card-title">应付汇总</div>
        <div class="summary-row"><span>应付总额</span><strong>¥{{ fmt(data.apTotal) }}</strong></div>
        <div class="summary-row"><span>已付</span><strong class="green">¥{{ fmt(data.apPaid) }}</strong></div>
        <div class="summary-row"><span>待付</span><strong class="red">¥{{ fmt(data.apPending) }}</strong></div>
      </div>
    </div>

    <!-- 添加快捷操作弹窗 -->
    <el-dialog v-model="addQuickVisible" title="添加快捷操作" width="400px">
      <el-select v-model="newQuick" placeholder="选择页面" style="width:100%">
        <el-option v-for="opt in availableRoutes" :key="opt.path" :label="opt.label" :value="opt.path" />
      </el-select>
      <template #footer>
        <el-button @click="addQuickVisible=false">取消</el-button>
        <el-button type="primary" @click="confirmAddQuick" :disabled="!newQuick">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// v6.4 标题去硬编码人名（动态登录人 + 分时问候）
const realName = (() => { try { return JSON.parse(localStorage.getItem('user') || '{}').realName || '同事' } catch { return '同事' } })()
const greeting = (() => { const h = new Date().getHours(); return h < 6 ? '夜深了' : h < 9 ? '早上好' : h < 12 ? '上午好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好' })()
import { fmt } from '../utils/fmt'
import { statusType as globalStatusType } from '../utils/statusTag'
import { ref, reactive, computed, onMounted } from 'vue'
import { Close, OfficeBuilding, Box, Van, User, Avatar, Grid, ShoppingCart, TrendCharts, Connection, DataAnalysis, Key, House, Bell} from '@element-plus/icons-vue'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

// 快捷入口图标映射：历史数据中的字符图标 → SVG 图标组件（未知字符保持原样显示）
const QUICK_ICON_MAP = {
  '▦': Grid, '⇲': ShoppingCart, '↗': TrendCharts, '◎': Connection, '¥': DataAnalysis,
  '🏭': OfficeBuilding, '🏢': OfficeBuilding, '👥': Avatar, '👤': User,
  '📦': Box, '🏗': House, '🔑': Key
}
function quickIconComp(icon) { return QUICK_ICON_MAP[icon] || null }

const today = new Date().toLocaleDateString('zh-CN', { year:'numeric', month:'long', day:'numeric', weekday:'long' })
const todos = ref({})
const hasAnyTodo = computed(() => {
  const t = todos.value || {}
  return (t.pendingQc || 0) + (t.pendingReturn || 0) + (t.pendingTailing || 0) + (t.dueArCount || 0) + (t.overdueTopics || 0) > 0
})

const data = reactive({ warehouseCount:0, materialCount:0, materialCategoryCount:0, supplierCount:0, customerCount:0, recentPurchases:[], recentSales:[], recentOutsource:[], lowStock:[], arTotal:0, arReceived:0, arPending:0, apTotal:0, apPaid:0, apPending:0 })
const perms = ref([])
function hasPerm(c) { return perms.value.includes(c) }

const { cw: cwP, onHeaderDragend: onHeaderDragendP } = useColumnResize('dashboard_purchase')
const { cw: cwS, onHeaderDragend: onHeaderDragendS } = useColumnResize('dashboard_sales')
const { cw: cwO, onHeaderDragend: onHeaderDragendO } = useColumnResize('dashboard_outsource')
const { cw: cwL, onHeaderDragend: onHeaderDragendL } = useColumnResize('dashboard_lowstock')

const editingQuick = ref(false)
const addQuickVisible = ref(false)
const newQuick = ref('')

const availableRoutes = [
  { path:'/inventory', label:'库存管理', icon:'▦', color:'#6366f1' },
  { path:'/purchase', label:'采购管理', icon:'⇲', color:'#f59e0b' },
  { path:'/sales', label:'销售管理', icon:'↗', color:'#10b981' },
  { path:'/outsource', label:'委外管理', icon:'◎', color:'#8b5cf6' },
  { path:'/report-finance-trend', label:'趋势分析', icon:'¥', color:'#ef4444' },
  { path:'/supplier', label:'供应商', icon:'🏭', color:'#3b82f6' },
  { path:'/customer', label:'客户', icon:'👥', color:'#ec4899' },
  { path:'/material', label:'物料', icon:'📦', color:'#f97316' },
  { path:'/warehouse', label:'仓库', icon:'🏗', color:'#14b8a6' },
  { path:'/users', label:'用户管理', icon:'👤', color:'#06b6d4' },
  { path:'/roles', label:'角色权限', icon:'🔑', color:'#e11d48' },
]

const defaultActions = [
  { path:'/inventory', label:'库存总览', icon:'▦', color:'#6366f1' },
  { path:'/purchase', label:'采购下单', icon:'⇲', color:'#f59e0b' },
  { path:'/sales', label:'销售开单', icon:'↗', color:'#10b981' },
  { path:'/outsource', label:'委外工单', icon:'◎', color:'#8b5cf6' },
  { path:'/report-finance-trend', label:'趋势分析', icon:'¥', color:'#ef4444' },
  { path:'/supplier', label:'供应商', icon:'🏭', color:'#3b82f6' },
]

const quickActions = ref([])

function loadQuickActions() {
  try {
    const saved = localStorage.getItem('pims-quick-actions')
    if (saved) {
      // 兼容历史快捷入口：旧 /finance 已废弃，迁移为报表中心趋势分析
      const list = JSON.parse(saved)
      list.forEach(item => {
        if (item && item.path === '/finance') {
          item.path = '/report-finance-trend'
          item.label = '趋势分析'
        }
      })
      quickActions.value = list
      saveQuickActions()
    } else {
      quickActions.value = [...defaultActions]
    }
  } catch {
    quickActions.value = [...defaultActions]
  }
}

function saveQuickActions() {
  localStorage.setItem('pims-quick-actions', JSON.stringify(quickActions.value))
}

function removeQuick(idx) {
  quickActions.value.splice(idx, 1)
  saveQuickActions()
}

function showAddQuick() {
  newQuick.value = ''
  addQuickVisible.value = true
}

function confirmAddQuick() {
  const route = availableRoutes.find(r => r.path === newQuick.value)
  if (route) {
    quickActions.value.push({ path: route.path, label: route.label, icon: route.icon, color: route.color })
    saveQuickActions()
  }
  addQuickVisible.value = false
}

// v6.4 状态色统一（复合状态串按关键词归一）
function statusType(s) {
  if (!s) return 'info'
  if (s.includes('CANCELLED')) return 'danger'
  return globalStatusType(s.split('_')[0])
}

// 状态统一映射为中文，避免显示英文
const STATUS_MAP = {
  DRAFT: '草稿', APPROVED: '已审核', CONFIRMED: '已确认', RECEIVED: '已到货',
  SHIPPED: '已发货', CLOSED: '已关闭', COMPLETED: '已完成', FINISHED: '已完成',
  CANCELLED: '已取消', PENDING: '待检', DONE: '已入库', REJECTED: '质检不合格'
}
function statusLabel(s) { return s ? (STATUS_MAP[s] || '未知') : '-' }

// v6.4 金额格式统一（utils/fmt 千分位 2 位）
onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  loadQuickActions()
  try {
    const res = await api.get('/dashboard')
    Object.assign(data, res)
  todos.value = res.todos || {}
  } catch (e) {}
})
</script>

<style scoped>
.dashboard { width: 100%; }

/* 欢迎区 */
.welcome-bar { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; gap: 16px; }
.welcome-title { font-size: 24px; font-weight: 800; color: var(--pims-text); margin: 0 0 4px; letter-spacing: -0.5px; }
.welcome-sub { margin: 0; font-size: 13px; color: var(--pims-text-secondary); }
.date-badge { padding: 8px 16px; background: var(--pims-card-bg); border-radius: 12px; font-size: 13px; color: var(--pims-text-secondary); box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); white-space: nowrap; }

/* 统计卡片 */
.stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 28px; }
.stat-card { padding: 22px; border-radius: 16px; color: #fff; position: relative; overflow: hidden; transition: transform 0.25s, box-shadow 0.25s; }
.stat-card:hover { transform: translateY(-3px); box-shadow: 0 12px 30px rgba(0,0,0,0.15); }
.stat-card.purple { background: linear-gradient(135deg, #6366f1, #4f46e5); }   /* 主色最深阶 */
.stat-card.amber  { background: linear-gradient(135deg, #7c81f2, #6366f1); }  /* v6.4 同色系二阶（原琥珀彩） */
.stat-card.emerald{ background: linear-gradient(135deg, #968ef5, #7c81f2); }  /* v6.4 同色系三阶（原翡翠彩） */
.stat-card.rose   { background: linear-gradient(135deg, #b3aef8, #968ef5); }  /* v6.4 同色系四阶（原玫瑰彩） */
.stat-card::after { content:''; position:absolute; width:100px; height:100px; border-radius:50%; background:rgba(255,255,255,0.08); top:-20px; right:-20px; }
.stat-card::before { content:''; position:absolute; width:60px; height:60px; border-radius:50%; background:rgba(255,255,255,0.06); bottom:-10px; left:30px; }
.stat-icon {
  width: 44px; height: 44px;
  margin-bottom: 14px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.16);
  border-radius: 12px;
  position: relative; z-index: 1;
}
.stat-icon .el-icon { font-size: 22px; color: #fff; }
.stat-value { font-size: 32px; font-weight: 800; line-height:1; letter-spacing:-1px; }
.stat-label { font-size:13px; opacity:.85; margin-top:4px; font-weight:500; }
.stat-spark { font-size:11px; opacity:.7; position:relative; z-index:1; margin-top: 8px; }

/* 快捷操作 */
.section-row { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.section-title { font-size: 15px; font-weight: 700; color: var(--pims-text); letter-spacing: 0.3px; }
.quick-actions { display: grid; grid-template-columns: repeat(auto-fit, minmax(100px, 1fr)); gap: 12px; margin-bottom: 28px; }
.quick-card { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 20px 12px; background: var(--pims-card-bg); border-radius: 14px; text-decoration: none; font-size: 13px; font-weight: 500; color: var(--pims-text); box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); transition: all 0.2s; position: relative; cursor: pointer; }
.quick-card:hover { transform: translateY(-2px); box-shadow: var(--pims-card-shadow-hover); color: var(--pims-primary); }
.quick-card.editing { border: 2px dashed var(--pims-primary); }
.quick-card.add-btn { border: 2px dashed #d0d5dd; background: transparent; color: var(--pims-text-secondary); }
.qc-icon { font-size: 24px; }
.qc-del { position: absolute; top: -6px; right: -6px; width: 20px; height: 20px; font-size: 12px; padding: 0; }

/* 数据表格区 */
.data-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(350px, 100%), 1fr)); gap: 16px; margin-bottom: 16px; }
.data-grid.bottom { grid-template-columns: repeat(auto-fit, minmax(min(300px, 100%), 1fr)); }
.data-card { background: var(--pims-card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.data-card-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 14px; display: flex; align-items: center; gap: 8px; }
.badge { display:inline-block; background:#f1f5f9; color:var(--pims-text-secondary); font-size:11px; padding:2px 8px; border-radius:10px; font-weight:600; }
.badge.warn { background:#fef3c7; color:#d97706; }

/* 汇总行 */
.summary-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid var(--pims-border-light); font-size: 14px; color: var(--pims-text-secondary); }
.summary-row:last-child { border-bottom: none; }
.summary-row strong { font-size: 16px; color: var(--pims-text); }
.summary-row .green { color: #10b981; }
.summary-row .red { color: #ef4444; }

/* 响应式 */
@media (max-width: 768px) {
  .welcome-bar { flex-direction: column; gap: 12px; }
  .welcome-title { font-size: 20px; }
  .stat-grid { grid-template-columns: repeat(2, 1fr); gap: 12px; }
  .stat-value { font-size: 26px; }
  .stat-card { padding: 16px; }
  .quick-actions { grid-template-columns: repeat(3, 1fr); gap: 10px; }
  .quick-card { padding: 14px 8px; font-size: 12px; }
  .data-grid { grid-template-columns: 1fr; }
}

@media (max-width: 480px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); gap: 10px; }
  .stat-value { font-size: 22px; }
  .stat-icon { width: 38px; height: 38px; margin-bottom: 8px; border-radius: 10px; }
  .stat-icon .el-icon { font-size: 19px; }
  .quick-actions { grid-template-columns: repeat(2, 1fr); }
}
.ls-red { color: #ef4444; font-weight: 700; }
.ls-orange { color: #f59e0b; font-weight: 700; }
.ls-muted { color: #94a3b8; }
.todo-bar { display: flex; align-items: center; gap: 16px; background: linear-gradient(135deg, #fffbeb, #fef3c7); border: 1px solid #fde68a; border-radius: 14px; padding: 14px 20px; margin-bottom: 20px; flex-wrap: wrap; }
.todo-title { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 700; color: #b45309; white-space: nowrap; }
.todo-items { display: flex; gap: 10px; flex-wrap: wrap; }
.todo-chip { display: inline-flex; align-items: center; gap: 4px; padding: 6px 14px; border-radius: 999px; font-size: 13px; text-decoration: none; background: #fff; border: 1px solid #f59e0b; color: #92400e; }
.todo-chip b { font-size: 15px; }
.todo-chip.danger { border-color: #ef4444; color: #b91c1c; background: #fef2f2; }
.todo-chip:hover { box-shadow: 0 2px 8px rgba(245, 158, 11, .25); }
.todo-sub { font-size: 11px; opacity: .75; }
</style>
