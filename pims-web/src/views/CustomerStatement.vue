<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">客户对账单</h2>
      <span class="report-sub">期间往来明细 + 期末余额 · 可打印盖章确认</span>
    </div>

    <div class="toolbar">
      <el-select v-model="customerId" filterable placeholder="选择客户" style="width:220px">
        <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
        start-placeholder="开始日期" end-placeholder="结束日期" style="width:260px" />
      <el-button type="primary" @click="fetch" :loading="loading" :disabled="!customerId || !range">查询</el-button>
      <el-button @click="doPrint" :disabled="!data" v-if="hasPerm('finance:read')">打印对账单</el-button>
    </div>

    <el-skeleton :rows="8" animated v-if="loading" />

    <template v-if="data && !loading">
      <div class="kpi-row" v-if="hasAmountPerm('finance-report')">
        <div class="kpi-card"><div class="kpi-label">期初余额</div><div class="kpi-value">¥{{ fmt(data.opening) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">本期应收增加</div><div class="kpi-value">¥{{ fmt(data.debit) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">本期收款</div><div class="kpi-value green">¥{{ fmt(receiptTotal) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">本期退货</div><div class="kpi-value">¥{{ fmt(returnTotal) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">期末余额（客户应付）</div><div class="kpi-value" :class="data.closing > 0 ? 'red' : 'green'"><b>¥{{ fmt(data.closing) }}</b></div></div>
      </div>

      <div class="table-card">
        <div class="card-title">
          {{ data.customer?.name }}（{{ data.from }} 至 {{ data.to }}）{{ data.customer?.taxNo ? ' · 税号 ' + data.customer.taxNo : '' }}
        </div>
        <p-table :data="rowsWithBalance" stripe border style="width:100%">
          <el-table-column prop="date" label="日期" width="110" />
          <el-table-column prop="docNo" label="单据号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="type" label="业务类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="{ '销售立账': 'primary', '收款': 'success', '销售退货': 'warning' }[row.type] || 'info'" size="small">{{ row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="摘要" min-width="170" show-overflow-tooltip />
          <el-table-column prop="debit" label="应收增加" width="120" align="right" v-if="hasAmountPerm('finance-report')">
            <template #default="{ row }">{{ row.debit ? '¥' + fmt(row.debit) : '' }}</template>
          </el-table-column>
          <el-table-column prop="credit" label="收款/退货" width="120" align="right" v-if="hasAmountPerm('finance-report')">
            <template #default="{ row }">{{ row.credit ? '¥' + fmt(row.credit) : '' }}</template>
          </el-table-column>
          <el-table-column prop="balance" label="结转余额" width="130" align="right" v-if="hasAmountPerm('finance-report')">
            <template #default="{ row }">
              <span :style="row.balance < 0 ? 'color:#b56a5c' : ''">¥{{ fmt(row.balance) }}</span>
            </template>
          </el-table-column>
        </p-table>
      </div>
    </template>

    <div class="empty-tip" v-if="!data && !loading">选择客户和对账期间后查询</div>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { printStatement } from '../utils/statementPrint'

const customerList = ref([])
const customerId = ref(null)
const range = ref(firstDayOfQuarter())
const data = ref(null)
const loading = ref(false)
const perms = ref([])

function firstDayOfQuarter() {
  const d = new Date()
  const from = new Date(d.getFullYear(), Math.floor(d.getMonth() / 3) * 3, 1)
  const f2 = `${from.getFullYear()}-${String(from.getMonth()+1).padStart(2,'0')}-${String(from.getDate()).padStart(2,'0')}`; const d2 = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; return [f2, d2]
}

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
const receiptTotal = computed(() => (data.value?.lines || []).filter(l => l.type === '收款').reduce((s, l) => s + Number(l.credit || 0), 0))
const returnTotal = computed(() => (data.value?.lines || []).filter(l => l.type === '销售退货').reduce((s, l) => s + Number(l.credit || 0), 0))

const rowsWithBalance = computed(() => {
  let balance = Math.round(Number(data.value?.opening || 0) * 100)   // v9.3：分单位
  return (data.value?.lines || []).map(l => {
    // v9.3（P2-8 审计）：滚动余额以"分"为整数单位累计——浮点直加会有 0.1+0.2 类长尾，行数多时分位漂移可见
    balance += Math.round(Number(l.debit || 0) * 100) - Math.round(Number(l.credit || 0) * 100)
    return { ...l, balance: balance / 100 }
  })
})

async function fetch() {
  if (!customerId.value || !range.value || range.value.length !== 2) { ElMessage.warning('请选择客户和对账期间'); return }
  loading.value = true
  try {
    data.value = await api.get('/finance-report/statement', {
      params: { customerId: customerId.value, from: range.value[0], to: range.value[1] }
    })
  } catch {} finally { loading.value = false }
}

function doPrint() {
  const ok = printStatement(data.value)
  if (!ok) ElMessage.warning('浏览器拦截了弹窗，请允许本站弹出窗口')
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  try { customerList.value = await api.get('/customer', { params: { enabled: true } }) } catch {}
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
.kpi-value.red { color: #b56a5c; }
.kpi-value.green { color: #16a34a; }
.table-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.card-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
.empty-tip { text-align: center; color: #94a3b8; padding: 60px 0; font-size: 14px; }
</style>
