<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">应付总表</h2>
      <span class="report-sub">按供应商维度汇总，不分订单</span>
    </div>

    <!-- 汇总卡片 -->
    <div class="summary-cards" v-if="hasAmountPerm('finance-ar')">
      <div class="summary-card">
        <div class="sc-label">应付总额</div>
        <div class="sc-value">¥{{ fmt(totalAmount) }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">已付总额</div>
        <div class="sc-value" style="color:#16a34a">¥{{ fmt(totalPaid) }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">剩余未付</div>
        <div class="sc-value" style="color:#ef4444">¥{{ fmt(totalRemaining) }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">整体付款率</div>
        <div class="sc-value">{{ paidRate }}%</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">应付周转率（{{ periodLabel }}）</div>
        <div class="sc-value" :style="turnoverStyle(overallTurnover)">{{ overallTurnover != null ? overallTurnover.toFixed(2) + ' 次' : '—' }}</div>
      </div>
      <div class="summary-card">
        <div class="sc-label">应付周转天数</div>
        <div class="sc-value" :style="turnoverStyle(overallTurnover)">{{ overallDays != null ? overallDays.toFixed(1) + ' 天' : '—' }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="tab-toolbar">
        <span class="tab-count">共 {{ list.length }} 个供应商</span>
        <el-date-picker v-model="range" type="daterange" size="small" value-format="YYYY-MM-DD"
          range-separator="至" start-placeholder="开始日" end-placeholder="结束日"
          :shortcuts="rangeShortcuts" style="width:260px" @change="load" />
      </div>
      <p-table :data="list" stripe border style="width:100%" show-summary
                :summary-method="getSummary">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="supplierName" label="供应商" min-width="220" show-overflow-tooltip />
        <el-table-column prop="docCount" label="单据数" width="90" align="center" />
        <el-table-column label="单据状态分布" width="220" align="center">
          <template #default="{ row }">
            <span class="status-chip unpaid">未付 {{ row.unpaidCount }}</span>
            <span class="status-chip partial">部分 {{ row.partialCount }}</span>
            <span class="status-chip paid">已结清 {{ row.paidCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="应付总额" width="130" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">¥{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="paidAmount" label="已付金额" width="130" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">¥{{ fmt(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column prop="remainingAmount" label="剩余未付" width="130" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">
            <span :style="{ color: row.remainingAmount > 0 ? '#ef4444' : '#16a34a' }">¥{{ fmt(row.remainingAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="付款率" width="180" align="center" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.paidRate || 0)" :stroke-width="10"
                         :color="progressColor(row.paidRate)" />
          </template>
        </el-table-column>
        <!-- v7.6 应付周转：周转率=期间立账÷平均余额（(期初+期末)/2），天数=365÷周转率；期间无立账或平均余额≤0 显示 — -->
        <el-table-column prop="turnover" label="周转率" width="90" align="center" v-if="hasAmountPerm('finance-ar')">
          <template #header>
            <el-tooltip placement="top" content="周转率 = 期间立账金额 ÷ 平均应付余额（期初+期末)/2，所选区间口径">
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
            <el-tooltip placement="top" content="周转天数 = 365 ÷ 周转率，表示一笔应付平均多少天付清（天数越长占用供应商资金越久）">
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
const perms = ref([])

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function progressColor(rate) {
  const r = Number(rate || 0)
  if (r >= 100) return '#16a34a'
  if (r >= 50) return '#f59e0b'
  return '#ef4444'
}

// 汇总计算
const totalAmount = computed(() => list.value.reduce((s, r) => s + Number(r.totalAmount || 0), 0))
const totalPaid = computed(() => list.value.reduce((s, r) => s + Number(r.paidAmount || 0), 0))
const totalRemaining = computed(() => totalAmount.value - totalPaid.value)
const paidRate = computed(() => {
  if (totalAmount.value <= 0) return '0.00'
  return (totalPaid.value * 100 / totalAmount.value).toFixed(2)
})

// v7.6 应付周转：期间筛选（默认本年初~今天），整体值=Σ行立账 ÷ ((Σ期初+Σ期末)/2)（不可对行周转率求和/平均）
const range = ref([])
const periodLabel = computed(() => range.value && range.value.length === 2
  ? `${range.value[0].slice(5)}~${range.value[1].slice(5)}` : '')
const rangeShortcuts = [
  { text: '今年', value: () => { const y = new Date().getFullYear(); return [new Date(y, 0, 1), new Date()] } },
  { text: '近一年', value: () => { const d = new Date(); d.setFullYear(d.getFullYear() - 1); return [d, new Date()] } },
  { text: '近90天', value: () => { const d = new Date(); d.setDate(d.getDate() - 90); return [d, new Date()] } }
]
const overallTurnover = computed(() => {
  const b = list.value.reduce((s, r) => s + Number(r.billedAmount || 0), 0)
  const op = list.value.reduce((s, r) => s + Number(r.openingBalance || 0), 0)
  const cl = list.value.reduce((s, r) => s + Number(r.closingBalance || 0), 0)
  const avg = (op + cl) / 2
  if (avg <= 0 || b <= 0) return null
  return b / avg
})
const overallDays = computed(() => overallTurnover.value != null ? 365 / overallTurnover.value : null)
// 天数≤45 绿（付款快）、≥120 红（长账期占用），中间默认色
function turnoverStyle(turnover) {
  if (turnover == null) return ''
  const days = 365 / Number(turnover)
  if (days <= 45) return 'color:#16a34a'
  if (days >= 120) return 'color:#ef4444'
  return ''
}

// 表尾合计
function getSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    const prop = col.property
    if (prop === 'supplierName') { sums[idx] = `${data.length} 个供应商`; return }
    if (prop === 'docCount') { sums[idx] = data.reduce((s, r) => s + Number(r.docCount || 0), 0); return }
    if (prop === 'totalAmount' || prop === 'paidAmount' || prop === 'remainingAmount') {
      sums[idx] = '¥' + fmt(data.reduce((s, r) => s + Number(r[prop] || 0), 0))
      return
    }
    // v7.6 周转合计 = 整体口径（Σ立账 ÷ 平均Σ余额），非行值加总
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
    if (range.value && range.value.length === 2) { params.start = range.value[0]; params.end = range.value[1] }
    list.value = await api.get('/finance/ap/total', { params })
  } catch {}
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  // 默认本年初~今天
  const y = new Date().getFullYear()
  const n = new Date()
  const today = `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`
  range.value = [`${y}-01-01`, today]
  load()
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
.status-chip.unpaid { background: #fee2e2; color: #b91c1c; }
.status-chip.partial { background: #fef3c7; color: #b45309; }
.status-chip.paid { background: #dcfce7; color: #15803d; }
.td-null { color: #9ca3af; }
</style>
