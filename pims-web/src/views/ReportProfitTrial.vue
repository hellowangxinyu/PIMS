<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">月度利润试算表</h2>
      <span class="report-sub">管理口径 · 收入按销售出库立账归月 · 费用按登记时间归月</span>
    </div>

    <div class="toolbar">
      <el-date-picker v-model="month" type="month" value-format="YYYY-MM" :clearable="false" @change="fetch" style="width:140px" />
      <el-radio-group v-model="month" @change="fetch" size="default">
        <el-radio-button v-for="m in quickMonths" :key="m" :value="m">{{ m.slice(5) }}月</el-radio-button>
      </el-radio-group>
      <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
    </div>

    <el-skeleton :rows="6" animated v-if="loading" />

    <template v-if="data && !loading">
      <div class="kpi-row" v-if="hasAmountPerm('finance-report')">
        <div class="kpi-card"><div class="kpi-label">营业收入<span title="已扣除当月销售退货">（净）</span></div><div class="kpi-value">¥{{ fmt(data.revenue) }}</div></div>
        <div class="kpi-card" v-if="Number(data.salesReturn) > 0"><div class="kpi-label">销售退货</div><div class="kpi-value kpi-red">¥{{ fmt(data.salesReturn) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">营业成本</div><div class="kpi-value">¥{{ fmt(data.cogs) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">毛利润</div><div class="kpi-value" :class="data.grossProfit >= 0 ? '' : 'kpi-red'">¥{{ fmt(data.grossProfit) }}<small v-if="data.grossRate !== null && data.grossRate !== undefined">（{{ data.grossRate }}%）</small></div></div>
        <div class="kpi-card"><div class="kpi-label">期间费用</div><div class="kpi-value kpi-red">¥{{ fmt(data.expenseTotal) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">其他收入</div><div class="kpi-value">¥{{ fmt(data.otherIncome) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">利润净额</div><div class="kpi-value" :class="data.netProfit >= 0 ? '' : 'kpi-red'"><b>¥{{ fmt(data.netProfit) }}</b></div></div>
      </div>

      <div class="grid-2col">
        <div class="table-card">
          <div class="card-title">利润表（{{ data.month }}）</div>
          <div v-if="data.note" class="pl-note">{{ data.note }}</div>
          <table class="pl-table" v-if="hasAmountPerm('finance-report')">
            <tr><td class="pl-item">一、营业收入（净额）</td><td class="pl-val">{{ fmt(data.revenue) }}</td></tr>
            <tr v-if="Number(data.salesReturn) > 0"><td class="pl-item">　其中：销售退货冲减</td><td class="pl-val kpi-red">-¥{{ fmt(data.salesReturn) }}</td></tr>
            <tr><td class="pl-item">　减：营业成本</td><td class="pl-val">{{ fmt(data.cogs) }}</td></tr>
            <tr class="pl-strong"><td class="pl-item">二、毛利润</td><td class="pl-val">{{ fmt(data.grossProfit) }}<span class="pl-rate" v-if="data.grossRate !== null && data.grossRate !== undefined">毛利率 {{ data.grossRate }}%</span></td></tr>
            <tr><td class="pl-item">　加：其他收入</td><td class="pl-val">{{ fmt(data.otherIncome) }}</td></tr>
            <tr><td class="pl-item">　减：期间费用合计</td><td class="pl-val">{{ fmt(data.expenseTotal) }}</td></tr>
            <tr v-for="d in data.expenseDetail" :key="'e' + d.type"><td class="pl-item pl-sub">　　{{ dictLabel('expense_type', d.type) || d.type }}</td><td class="pl-val">{{ fmt(d.amount) }}</td></tr>
            <tr class="pl-strong"><td class="pl-item">三、利润净额</td><td class="pl-val" :class="data.netProfit < 0 ? 'neg' : ''"><b>{{ fmt(data.netProfit) }}</b></td></tr>
          </table>
          <div class="no-perm" v-else>金额明细需「查看金额」权限</div>
        </div>

        <div class="table-card">
          <div class="card-title">当月资金占用参考（不进损益）</div>
          <table class="pl-table" v-if="hasAmountPerm('finance-report')">
            <tr><td class="pl-item">生产领料成本</td><td class="pl-val">{{ fmt(data.materialUsed) }}</td></tr>
            <tr><td class="pl-item">委外加工费立账</td><td class="pl-val">{{ fmt(data.outsourceFee) }}</td></tr>
            <tr><td class="pl-item">采购应付立账</td><td class="pl-val">{{ fmt(data.purchaseAp) }}</td></tr>
          </table>
          <div class="no-perm" v-else>金额明细需「查看金额」权限</div>
          <div class="card-title" style="margin-top:16px">近 12 个月走势</div>
          <SvgLineChart v-if="data.trend && data.trend.length" :labels="trendLabels" :series="trendSeries" :height="220" />
          <div v-else class="no-perm">暂无走势数据</div>
        </div>
      </div>

      <div class="table-card" v-if="data.incomeDetail && data.incomeDetail.length">
        <div class="card-title">其他收入构成</div>
        <table class="pl-table" v-if="hasAmountPerm('finance-report')">
          <tr v-for="d in data.incomeDetail" :key="'i' + d.type"><td class="pl-item">{{ dictLabel('other_income_type', d.type) || d.type }}</td><td class="pl-val">{{ fmt(d.amount) }}</td></tr>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { monthLocal } from '../utils/date'
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import api from '../api'
import { downloadFile } from '../utils/download'
import SvgLineChart from '../components/charts/SvgLineChart.vue'

const data = ref(null)
const dicts = ref({})
const perms = ref([])
const loading = ref(false)
const exporting = ref(false)
const month = ref(monthLocal())
const trendLabels = computed(() => (data.value?.trend || []).map(t => t.month))
const trendSeries = computed(() => [
  { name: '收入', values: (data.value?.trend || []).map(t => Number(t.revenue || 0)), color: '#2563eb' },
  { name: '成本', values: (data.value?.trend || []).map(t => Number(t.cogs || 0)), color: '#f59e0b' },
  { name: '净利', values: (data.value?.trend || []).map(t => Number(t.net || 0)), color: '#16a34a' }
])

const quickMonths = computed(() => {
  const arr = []
  const d = new Date()
  for (let i = 2; i >= 0; i--) {
    const t = new Date(d.getFullYear(), d.getMonth() - i, 1)
    arr.push(`${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}`)
  }
  return arr
})

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function dictItems(type) { return dicts.value[type] || [] }
function dictLabel(type, v) { return dictItems(type).find(d => d.value === v)?.label }

async function fetch() {
  loading.value = true
  try { data.value = await api.get('/finance-report/profit-trial', { params: { month: month.value } }) } catch {}
  finally { loading.value = false }
}

async function fetchDicts() {
  try {
    const all = await api.get('/dict')
    const map = {}
    for (const item of all) {
      if (!map[item.type]) map[item.type] = []
      map[item.type].push(item)
    }
    dicts.value = map
  } catch {}
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/finance-report/profit-trial/export', { month: month.value }, `利润试算表-${month.value}.xlsx`)
  } catch {} finally { exporting.value = false }
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
  fetchDicts()
})
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.report-title { margin: 0; font-size: 20px; }
.report-sub { font-size: 12px; color: #94a3b8; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-bottom: 16px; }
.kpi-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 14px 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.kpi-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.kpi-value { font-size: 18px; font-weight: 700; }
.kpi-value small { font-size: 12px; font-weight: 400; color: #64748b; margin-left: 4px; }
.kpi-red { color: #ef4444; }
.grid-2col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 1000px) { .grid-2col { grid-template-columns: 1fr; } }
.table-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); margin-bottom: 16px; }
.card-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
.pl-table { width: 100%; border-collapse: collapse; }
.pl-table td { padding: 8px 4px; border-bottom: 1px solid #f1f5f9; font-size: 13px; }
.pl-item { color: #475569; }
.pl-item.pl-sub { color: #94a3b8; font-size: 12px; }
.pl-val { text-align: right; font-variant-numeric: tabular-nums; color: #0f172a; }
.pl-strong td { border-top: 1px solid #cbd5e1; font-weight: 700; background: #f8fafc; }
.pl-rate { font-size: 12px; color: #64748b; font-weight: 400; margin-left: 8px; }
.pl-val.neg { color: #ef4444; }
.no-perm { font-size: 13px; color: #94a3b8; padding: 12px 0; }
.pl-note { font-size: 12px; color: #94a3b8; margin: 4px 0 10px; }
</style>
