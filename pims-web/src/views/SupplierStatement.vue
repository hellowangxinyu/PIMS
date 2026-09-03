<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">供应商对账单</h2>
      <span class="report-sub">期间往来明细 + 期末余额 · 可打印盖章确认</span>
    </div>

    <div class="toolbar">
      <el-select v-model="supplierId" filterable placeholder="选择供应商" style="width:220px">
        <el-option v-for="s in supplierList" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
        start-placeholder="开始日期" end-placeholder="结束日期" style="width:260px" />
      <el-button type="primary" @click="fetch" :loading="loading" :disabled="!supplierId || !range">查询</el-button>
      <el-button @click="doPrint" :disabled="!data" v-if="hasPerm('finance:read')">打印对账单</el-button>
    </div>

    <el-skeleton :rows="8" animated v-if="loading" />

    <template v-if="data && !loading">
      <div class="kpi-row" v-if="hasAmountPerm('finance-report')">
        <div class="kpi-card"><div class="kpi-label">期初余额</div><div class="kpi-value">¥{{ fmt(data.opening) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">本期应付增加</div><div class="kpi-value">¥{{ fmt(data.debit) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">本期付款</div><div class="kpi-value green">¥{{ fmt(payTotal) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">本期退货冲减</div><div class="kpi-value">¥{{ fmt(returnTotal) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">期末余额（我司应付）</div><div class="kpi-value" :class="data.closing > 0 ? 'red' : 'green'"><b>¥{{ fmt(data.closing) }}</b></div></div>
      </div>

      <div class="table-card">
        <div class="card-title">{{ data.supplier?.name }}（{{ data.from }} 至 {{ data.to }}）</div>
        <p-table :data="rowsWithBalance" stripe border style="width:100%">
          <el-table-column prop="date" label="日期" width="110" />
          <el-table-column prop="docNo" label="单据号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="type" label="业务类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="{ '应付立账': 'primary', '付款': 'success', '退货冲减': 'warning' }[row.type] || 'info'" size="small">{{ row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="摘要" min-width="170" show-overflow-tooltip />
          <el-table-column prop="debit" label="应付增加" width="120" align="right" v-if="hasAmountPerm('finance-report')">
            <template #default="{ row }">{{ row.debit ? '¥' + fmt(row.debit) : '' }}</template>
          </el-table-column>
          <el-table-column prop="credit" label="付款/退货" width="120" align="right" v-if="hasAmountPerm('finance-report')">
            <template #default="{ row }">{{ row.credit ? '¥' + fmt(row.credit) : '' }}</template>
          </el-table-column>
          <el-table-column prop="balance" label="结转余额" width="130" align="right" v-if="hasAmountPerm('finance-report')">
            <template #default="{ row }">
              <span :style="row.balance < 0 ? 'color:#ef4444' : ''">¥{{ fmt(row.balance) }}</span>
            </template>
          </el-table-column>
        </p-table>
      </div>
    </template>

    <div class="empty-tip" v-if="!data && !loading">选择供应商和对账期间后查询</div>
  </div>
</template>

<script setup>
// v6.3 供应商对账单：与客户版同款时点三流口径（应付立账/付款/退货冲减），复用对账单打印骨架
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { printSupplierStatement } from '../utils/supplierStatementPrint'

const supplierList = ref([])
const supplierId = ref(null)
const range = ref(firstDayOfQuarter())
const data = ref(null)
const loading = ref(false)
const perms = ref([])

function firstDayOfQuarter() {
  const d = new Date()
  const from = new Date(d.getFullYear(), Math.floor(d.getMonth() / 3) * 3, 1)
  return [from.toISOString().slice(0, 10), d.toISOString().slice(0, 10)]
}

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }

const payTotal = computed(() => (data.value?.lines || []).filter(l => l.type === '付款').reduce((s, l) => s + Number(l.credit || 0), 0))
const returnTotal = computed(() => (data.value?.lines || []).filter(l => l.type === '退货冲减').reduce((s, l) => s + Number(l.credit || 0), 0))

const rowsWithBalance = computed(() => {
  let balance = Number(data.value?.opening || 0)
  return (data.value?.lines || []).map(l => {
    balance += Number(l.debit || 0) - Number(l.credit || 0)
    return { ...l, balance: Number(balance.toFixed(2)) }
  })
})

async function fetch() {
  if (!supplierId.value || !range.value || range.value.length !== 2) { ElMessage.warning('请选择供应商和对账期间'); return }
  loading.value = true
  try {
    data.value = await api.get('/finance-report/supplier-statement', {
      params: { supplierId: supplierId.value, from: range.value[0], to: range.value[1] }
    })
  } catch {} finally { loading.value = false }
}

function doPrint() {
  const ok = printSupplierStatement(data.value)
  if (!ok) ElMessage.warning('浏览器拦截了弹窗，请允许本站弹出窗口')
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  try { supplierList.value = await api.get('/supplier', { params: { enabled: true } }) } catch {}
})
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.report-title { margin: 0; font-size: 20px; }
.report-sub { font-size: 12px; color: #94a3b8; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin-bottom: 16px; }
.kpi-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 14px 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.kpi-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.kpi-value { font-size: 18px; font-weight: 700; }
.kpi-value.red { color: #ef4444; }
.kpi-value.green { color: #16a34a; }
.table-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.card-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
.empty-tip { text-align: center; color: #94a3b8; padding: 60px 0; font-size: 14px; }
</style>
