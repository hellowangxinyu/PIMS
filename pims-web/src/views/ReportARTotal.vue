<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">应收总表</h2>
      <span class="report-sub">按客户维度汇总，不分订单</span>
    </div>

    <!-- 汇总卡片 -->
    <div class="summary-cards" v-if="hasAmountPerm('finance-ar')">
      <div class="summary-card">
        <div class="sc-label">应收总额</div>
        <div class="sc-value">¥{{ fmt(totalAmount) }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">已收总额</div>
        <div class="sc-value" style="color:#16a34a">¥{{ fmt(totalReceived) }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">剩余未收</div>
        <div class="sc-value" style="color:#b56a5c">¥{{ fmt(totalRemaining) }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">整体回款率</div>
        <div class="sc-value">{{ receivedRate }}%</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">应收周转率（{{ periodLabel }}）</div>
        <div class="sc-value" :style="turnoverStyle(overallTurnover)">{{ overallTurnover != null ? overallTurnover.toFixed(2) + ' 次' : '—' }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">应收周转天数</div>
        <div class="sc-value" :style="turnoverStyle(overallTurnover)">{{ overallDays != null ? overallDays.toFixed(1) + ' 天' : '—' }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="tab-toolbar">
        <!-- v11.7 客户搜索：本地过滤（同应付总表 v11.5 供应商搜索） -->
        <el-input v-model="customerKeyword" placeholder="搜索客户" clearable size="small" style="width:200px" />
        <span class="tab-count">共 {{ filteredList.length }} 个客户{{ customerKeyword ? `（筛选自 ${list.length} 家）` : '' }}</span>
        <!-- v11.7 单日期筛选：选"截至日"看当天累计应收（对账口径）；清空看全部（同应付总表 v11.4） -->
        <el-date-picker v-model="endDate" type="date" size="small" value-format="YYYY-MM-DD"
          placeholder="统计截至（默认全部）" :shortcuts="dateShortcuts" style="width:190px" clearable @change="load" />
      </div>
      <!-- v11.7 默认按超期应收从少到多（同应付总表 v11.3） -->
      <p-table :data="filteredList" stripe border style="width:100%" show-summary
                :summary-method="getSummary" :default-sort="{ prop: 'overdueAmount', order: 'ascending' }">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="customerName" label="客户" min-width="220" show-overflow-tooltip />
        <el-table-column prop="docCount" label="单据数" width="90" align="center" />
        <el-table-column label="单据状态分布" width="220" align="center">
          <template #default="{ row }">
            <span class="status-chip unpaid">未收 {{ row.unpaidCount }}</span>
            <span class="status-chip partial">部分 {{ row.partialCount }}</span>
            <span class="status-chip paid">已结清 {{ row.paidCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="应收总额" width="130" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">¥{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="receivedAmount" label="已收金额" width="130" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">¥{{ fmt(row.receivedAmount) }}</template>
        </el-table-column>
        <el-table-column prop="remainingAmount" label="剩余未收" width="130" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">
            <span :style="{ color: row.remainingAmount > 0 ? '#b56a5c' : '#16a34a' }">¥{{ fmt(row.remainingAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="overdueAmount" label="超期应收" width="130" align="right" sortable v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">
            <span v-if="Number(row.overdueAmount) > 0" style="color:#b05a4e;font-weight:700">¥{{ fmt(row.overdueAmount) }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="回款率" width="180" align="center" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.receivedRate || 0)" :stroke-width="10"
                         :color="progressColor(row.receivedRate)" />
          </template>
        </el-table-column>
        <!-- v7.6 应收周转：周转率=期间立账÷平均余额（(期初+期末)/2），天数=365÷周转率；期间无立账或平均余额≤0 显示 — -->
        <el-table-column prop="turnover" label="周转率" width="90" align="center" v-if="hasAmountPerm('finance-ar')">
          <template #header>
            <el-tooltip placement="top" content="周转率 = 期间立账金额 ÷ 平均应收余额（期初+期末)/2，所选区间口径">
              <span>周转率<i style="font-style:normal;color:#94a3b8"> ?</i></span>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <span v-if="row.turnover != null" :style="turnoverStyle(row.turnover)">{{ Number(row.turnover).toFixed(2) }}</span>
            <span v-else class="td-null">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="turnoverDays" label="周转天数" width="95" align="center" v-if="hasAmountPerm('finance-ar')">
          <template #header>
            <el-tooltip placement="top" content="周转天数 = 365 ÷ 周转率，表示一笔应收平均多少天收回（越小回款越快）">
              <span>周转天数<i style="font-style:normal;color:#94a3b8"> ?</i></span>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <span v-if="row.turnoverDays != null" :style="turnoverStyle(row.turnover)">{{ Number(row.turnoverDays).toFixed(1) }}</span>
            <span v-else class="td-null">—</span>
          </template>
        </el-table-column>
      </p-table>
    </div>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import api from '../api'

const list = ref([])
// v11.7 客户搜索（本地过滤，同应付总表）
const customerKeyword = ref('')
const filteredList = computed(() => {
  const kw = customerKeyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter(r => (r.customerName || '').toLowerCase().includes(kw))
})
const perms = ref([])

function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function progressColor(rate) {
  const r = Number(rate || 0)
  if (r >= 100) return '#16a34a'
  if (r >= 50) return '#c2a069'
  return '#b56a5c'
}

// 汇总计算（随客户搜索联动，与表格口径一致；同应付总表 v11.6）
const totalAmount = computed(() => filteredList.value.reduce((s, r) => s + Number(r.totalAmount || 0), 0))
const totalReceived = computed(() => filteredList.value.reduce((s, r) => s + Number(r.receivedAmount || 0), 0))
const totalRemaining = computed(() => totalAmount.value - totalReceived.value)
const receivedRate = computed(() => {
  if (totalAmount.value <= 0) return '0.00'
  return (totalReceived.value * 100 / totalAmount.value).toFixed(2)
})

// v7.6 应收周转：Σ行立账 ÷ ((Σ期初+Σ期末)/2)，不可对行周转率求和/平均；随筛选子集重算（同应付总表 v11.6）
const endDate = ref('')
const periodLabel = computed(() => endDate.value ? `截至 ${endDate.value.slice(5)}` : '')
const dateShortcuts = [
  { text: '今天', value: () => new Date() },
  { text: '本月初', value: () => { const d = new Date(); d.setDate(1); return d } },
  { text: '上月末', value: () => { const d = new Date(); d.setDate(0); return d } }
]
const overallTurnover = computed(() => {
  const b = filteredList.value.reduce((s, r) => s + Number(r.billedAmount || 0), 0)
  const op = filteredList.value.reduce((s, r) => s + Number(r.openingBalance || 0), 0)
  const cl = filteredList.value.reduce((s, r) => s + Number(r.closingBalance || 0), 0)
  const avg = (op + cl) / 2
  if (avg <= 0 || b <= 0) return null
  return b / avg
})
const overallDays = computed(() => overallTurnover.value != null ? 365 / overallTurnover.value : null)
// 天数≤45 绿（回款快）、≥120 红（回款慢，行业参考线）、中间默认色
function turnoverStyle(turnover) {
  if (turnover == null) return ''
  const days = 365 / Number(turnover)
  if (days <= 45) return 'color:#16a34a'
  if (days >= 120) return 'color:#b56a5c'
  return ''
}

// 表尾合计
function getSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    const prop = col.property
    if (prop === 'customerName') { sums[idx] = `${data.length} 个客户`; return }
    if (prop === 'docCount') { sums[idx] = data.reduce((s, r) => s + Number(r.docCount || 0), 0); return }
    if (prop === 'totalAmount' || prop === 'receivedAmount' || prop === 'remainingAmount') {
      sums[idx] = '¥' + fmt(data.reduce((s, r) => s + Number(r[prop] || 0), 0))
      return
    }
    // 周转合计 = Σ立账 ÷ 平均Σ余额（随筛选子集重算，非行值加总）
    if (prop === 'turnover' || prop === 'turnoverDays') {
      if (overallTurnover.value == null) { sums[idx] = '—'; return }
      sums[idx] = prop === 'turnover' ? overallTurnover.value.toFixed(2) : overallDays.value.toFixed(1)
      return
    }
    sums[idx] = ''
  })
  return sums
}

async function load() {
  try {
    const params = {}
    if (endDate.value) { params.end = endDate.value }
    list.value = await api.get('/finance/ar/total', { params })
  } catch {}
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  load()   // v11.7 默认全部（单日期筛选由用户自选截至日，同应付总表口径）
})
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.report-sub { font-size: 13px; color: #64748b; }
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.sc-label { font-size: 13px; color: #64748b; margin-bottom: 6px; }
.sc-value { font-size: 22px; font-weight: 800; color: var(--pims-text); }
.table-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.tab-count { font-size: 13px; color: #64748b; }
.status-chip { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 12px; margin-right: 4px; }
.status-chip.unpaid { background: #f7ecec; color: #b91c1c; }
.status-chip.partial { background: #f4efe0; color: #b45309; }
.status-chip.paid { background: #e6efe7; color: #15803d; }
.td-null { color: #9ca3af; }
</style>
