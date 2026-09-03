<template>
  <div class="page-container">
    <div class="page-header">
      <h2>排产中心</h2>
      <div class="header-actions">
        <span class="type-count">{{ tab === 'MO' ? '生产排产：确认后进入排产，领料=已投料，入库=已入库，关联销售单发货=已发货' : '委外排产：确认后进入委外，入库=已入库，关联销售单发货=已发货' }}</span>
      </div>
    </div>

    <div class="type-tabs">
      <button class="type-tab" :class="{ active: tab === 'MO' }" @click="tab = 'MO'">生产排产 <span class="tab-badge">{{ moCount }}</span></button>
      <button class="type-tab" :class="{ active: tab === 'OO' }" @click="tab = 'OO'">委外排产 <span class="tab-badge">{{ ooCount }}</span></button>
    </div>

    <!-- 生产排产 -->
    <div class="table-card" v-if="tab === 'MO'">
      <el-table ref="moTableRef" :data="sortedMoList" stripe border style="width:100%">
        <el-table-column label="顺序" width="70" align="center">
          <template #default="{ row }">
            <!-- v5.27：鼠标拖动把手调整排产顺序（SortableJS 整行拖拽，其他行平滑让位） -->
            <span v-if="row.scheduleSeq" draggable="true" class="drag-handle" title="按住拖动调整顺序">⠿ #{{ row.scheduleSeq }}</span>
            <span v-else class="seq-empty">待排</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="生产单号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="productCode" label="产品编码" min-width="110" />
        <el-table-column prop="batchQty" label="批量" width="90" align="right" />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="salesOrderNo" label="来源" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.salesOrderNo ? '销售单 ' + row.salesOrderNo : '备料生产' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="moStatusType(row.displayStatus || row.status)" size="small">{{ moStatusLabel(row.displayStatus || row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <button v-if="row.status === 'CONFIRMED'" class="op-btn op-btn-success" @click="scheduleMo(row)">排产</button>
            <button v-if="row.status === 'SCHEDULED'" class="op-btn op-btn-warn" @click="unscheduleMo(row)">取消排产</button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 委外排产 -->
    <div class="table-card" v-else>
      <el-table ref="ooTableRef" :data="sortedOoList" stripe border style="width:100%">
        <el-table-column label="顺序" width="70" align="center">
          <template #default="{ row }">
            <!-- v5.27：鼠标拖动把手调整排产顺序（SortableJS 整行拖拽，其他行平滑让位） -->
            <span v-if="row.scheduleSeq" draggable="true" class="drag-handle" title="按住拖动调整顺序">⠿ #{{ row.scheduleSeq }}</span>
            <span v-else class="seq-empty">待排</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="委外单号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="productCode" label="产品编码" min-width="110" />
        <el-table-column prop="batchQty" label="批量" width="90" align="right" />
        <el-table-column prop="processor" label="代工厂" min-width="130" show-overflow-tooltip />
        <el-table-column prop="salesOrderNo" label="来源销售单" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.salesOrderNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="ooStatusType(row.displayStatus || row.status)" size="small">{{ ooStatusLabel(row.displayStatus || row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <button v-if="row.status === 'CONFIRMED'" class="op-btn op-btn-success" @click="scheduleOo(row)">排产</button>
            <button v-if="row.status === 'OUTSOURCED'" class="op-btn op-btn-warn" @click="unscheduleOo(row)">取消排产</button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const tab = ref('MO')
const moList = ref([])
const ooList = ref([])

// v5.27：状态标签（推导状态：FEED=已投料 INBOUND=已入库 SHIPPED=已发货）
function moStatusType(s) {
  return { SCHEDULED: 'primary', FEED: 'warning', INBOUND: 'success', SHIPPED: 'info', COMPLETED: 'info' }[s] || 'info'
}
function moStatusLabel(s) {
  return { SCHEDULED: '已排产', FEED: '已投料', INBOUND: '已入库', SHIPPED: '已发货', COMPLETED: '已完工', CONFIRMED: '已确认', DRAFT: '草稿' }[s] || s
}
function ooStatusType(s) {
  return { OUTSOURCED: 'primary', INBOUND: 'success', SHIPPED: 'info', COMPLETED: 'info' }[s] || 'info'
}
function ooStatusLabel(s) {
  return { OUTSOURCED: '已委外', INBOUND: '已入库', SHIPPED: '已发货', COMPLETED: '已完工', CONFIRMED: '已确认', DRAFT: '草稿' }[s] || s
}

const moCount = computed(() => moList.value.length)
const ooCount = computed(() => ooList.value.length)

// v5.27：队列排序——已排产（有顺序号）按序号在前，待排产在后
const sortedMoList = computed(() => {
  return [...moList.value].sort((a, b) => {
    if (a.scheduleSeq && b.scheduleSeq) return a.scheduleSeq - b.scheduleSeq
    if (a.scheduleSeq) return -1
    if (b.scheduleSeq) return 1
    return String(b.createTime || '').localeCompare(String(a.createTime || ''))
  })
})
const sortedOoList = computed(() => {
  return [...ooList.value].sort((a, b) => {
    if (a.scheduleSeq && b.scheduleSeq) return a.scheduleSeq - b.scheduleSeq
    if (a.scheduleSeq) return -1
    if (b.scheduleSeq) return 1
    return String(b.createTime || '').localeCompare(String(a.createTime || ''))
  })
})

async function fetchMo() {
  try {
    const all = await api.get('/production-order')
    // v5.27：仅显示待排产/排产中的单子；已入库、已发货的从排产队列移除
    moList.value = all.filter(r => (r.status === 'CONFIRMED' || r.status === 'SCHEDULED')
      && r.displayStatus !== 'INBOUND' && r.displayStatus !== 'SHIPPED')
  } catch {}
}
async function fetchOo() {
  try {
    const all = await api.get('/outsource-order')
    // v5.27：仅显示待排产/委外中的单子；已入库、已发货的从排产队列移除
    ooList.value = all.filter(r => (r.status === 'CONFIRMED' || r.status === 'OUTSOURCED')
      && r.displayStatus !== 'INBOUND' && r.displayStatus !== 'SHIPPED')
  } catch {}
}

async function scheduleMo(row) {
  try {
    await ElMessageBox.confirm(`排产生产订单 ${row.orderNo}？\n排产后进入生产队列（排到最后），可上移/下移调整顺序。`, '排产', { type: 'warning' })
    await api.post(`/production-order/${row.id}/schedule`)
    ElMessage.success(`${row.orderNo} 已排产`)
    fetchMo()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}
// v5.27：SortableJS 整行拖拽排序（拖动 ⠿ 把手，整行跟随鼠标，其他行平滑让位）
import Sortable from 'sortablejs'

const moTableRef = ref(null)
const ooTableRef = ref(null)
let moSortable = null
let ooSortable = null

function initSortable(container, type) {
  if (!container) return
  // el-table 的 ref 是组件实例，取根 DOM 元素（$el）
  const root = container.$el || container
  if (!root) return
  const tbody = root.querySelector('tbody')
  if (!tbody) return
  const old = type === 'MO' ? moSortable : ooSortable
  if (old) old.destroy()
  const s = Sortable.create(tbody, {
    handle: '.drag-handle',
    animation: 150,           // 其他行平滑让位（挤开中间空隙）
    forceFallback: true,       // 自定义拖拽层：整行克隆跟随鼠标（原生拖拽层不可控）
    fallbackClass: 'drag-fallback',
    fallbackOnBody: true,
    ghostClass: 'drag-ghost',  // 原位置占位（半透明）
    chosenClass: 'drag-chosen',
    onEnd: (evt) => {
      // 以拖完后的 DOM 行顺序为准（第2列=订单号，与数据对照），不依赖 Sortable 索引
      const rows = Array.from(tbody.querySelectorAll('tr'))
      const domOrderNos = rows.map(tr => {
        const tds = tr.querySelectorAll('td')
        return tds.length >= 2 ? tds[1].textContent.trim() : ''
      }).filter(n => n)
      const all = type === 'MO' ? moList.value : ooList.value
      const arr = [...all].sort((a, b) => {
        const ia = domOrderNos.indexOf(a.orderNo)
        const ib = domOrderNos.indexOf(b.orderNo)
        if (ia === -1) return 1
        if (ib === -1) return -1
        return ia - ib
      })
      if (arr.length !== all.length) return
      // 本地立即反馈：重写已排产单的顺序号并更新列表
      let seq = 1
      for (const r of arr) if (r.scheduleSeq) r.scheduleSeq = seq++
      if (type === 'MO') moList.value = [...arr]
      else ooList.value = [...arr]
      // 提交后端整队列重排，随后以服务端结果刷新校准
      const ids = arr.filter(r => r.scheduleSeq).map(r => r.id)
      const url = type === 'MO' ? '/production-order/reorder-schedule' : '/outsource-order/reorder-schedule'
      api.post(url, { ids }).then(() => {
        type === 'MO' ? fetchMo() : fetchOo()
      }).catch(() => {})
    }
  })
  if (type === 'MO') moSortable = s
  else ooSortable = s
}

// 表格渲染完成后初始化拖拽（v-if 切换 tab 时表格重建，需要重新绑定）
watch([tab, moList, ooList], () => {
  nextTick(() => {
    if (tab.value === 'MO') initSortable(moTableRef.value, 'MO')
    else initSortable(ooTableRef.value, 'OO')
  })
})
async function unscheduleMo(row) {
  try {
    await ElMessageBox.confirm(`取消排产 ${row.orderNo}？`, '取消排产', { type: 'warning' })
    await api.post(`/production-order/${row.id}/unschedule`)
    ElMessage.success('已取消排产')
    fetchMo()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}
async function scheduleOo(row) {
  try {
    await ElMessageBox.confirm(`排产委外订单 ${row.orderNo}？\n排产后进入委外队列（排到最后），可上移/下移调整顺序。`, '排产', { type: 'warning' })
    await api.post(`/outsource-order/${row.id}/schedule`)
    ElMessage.success(`${row.orderNo} 已排产（委外）`)
    fetchOo()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}
async function unscheduleOo(row) {
  try {
    await ElMessageBox.confirm(`取消排产 ${row.orderNo}？`, '取消排产', { type: 'warning' })
    await api.post(`/outsource-order/${row.id}/unschedule`)
    ElMessage.success('已取消排产')
    fetchOo()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

onMounted(() => { fetchMo(); fetchOo() })
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.type-tabs { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-tab { padding: 6px 16px; font-size: 13px; font-weight: 600; border: 1px solid #e2e8f0; border-radius: 6px; cursor: pointer; background: #fff; color: #64748b; transition: all 0.2s; }
.type-tab:hover { color: #334155; border-color: #cbd5e1; }
.type-tab.active { background: #1d4ed8; color: #fff; border-color: #1d4ed8; }
.tab-badge { display: inline-block; min-width: 18px; padding: 0 5px; margin-left: 4px; font-size: 11px; line-height: 16px; border-radius: 9px; background: rgba(0,0,0,0.08); color: inherit; }
.type-tab.active .tab-badge { background: rgba(255,255,255,0.25); }
.type-count { font-size: 12px; color: #94a3b8; }
.seq-no { font-weight: 700; color: #1d4ed8; }
.seq-empty { color: #cbd5e1; font-size: 12px; }
.drag-handle { cursor: grab; font-weight: 700; color: #1d4ed8; padding: 2px 8px; border-radius: 4px; user-select: none; }
.drag-handle:hover { background: #eff6ff; }
.drag-handle:active { cursor: grabbing; }
/* v5.27：拖拽动画样式（SortableJS） */
.drag-ghost { opacity: 0.45; background: #eff6ff !important; }
.drag-chosen { background: #dbeafe !important; }
.drag-chosen td { border-bottom: 2px dashed #3b82f6 !important; }
.drag-fallback { background: #fff !important; box-shadow: 0 6px 16px rgba(0,0,0,0.18); opacity: 0.95; }
.drag-fallback td { background: #fff !important; }
</style>
