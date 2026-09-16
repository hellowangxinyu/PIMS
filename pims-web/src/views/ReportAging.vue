<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">账龄分析</h2>
      <span class="hint">口径：未结清金额按到期日距今分层（无到期日视为未到期）</span>
    </div>

    <div v-if="loading" class="report-loading"><el-skeleton :rows="8" animated /></div>
    <template v-else>
      <div class="kpi-row">
        <div class="kpi-card"><div class="kpi-label">应收未收合计</div><div class="kpi-value">¥{{ fmt(data.summary?.arTotal) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">应收已逾期</div><div class="kpi-value kpi-red">¥{{ fmt(data.summary?.arOverdue) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">应付未付合计</div><div class="kpi-value">¥{{ fmt(data.summary?.apTotal) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">应付已逾期</div><div class="kpi-value kpi-red">¥{{ fmt(data.summary?.apOverdue) }}</div></div>
      </div>

      <div class="chart-grid" style="margin-top:16px">
        <div class="chart-card">
          <div class="chart-card-title">应收账龄分层</div>
          <SvgBarChart :data="data.arBuckets || []" :horizontal="true" color="#b56a5c" />
        </div>
        <div class="chart-card">
          <div class="chart-card-title">应付账龄分层</div>
          <SvgBarChart :data="data.apBuckets || []" :horizontal="true" color="#c2a069" />
        </div>
      </div>

      <div class="table-card" style="margin-top:16px">
        <div class="tab-toolbar">
          <el-radio-group v-model="arTab" size="small">
            <el-radio-button :value="'AR'">应收明细</el-radio-button>
            <el-radio-button :value="'AP'">应付明细</el-radio-button>
          </el-radio-group>
          <span class="tab-count">共 {{ currentDetail.length }} 条</span>
        </div>
        <p-table :data="pagedRows" stripe border size="small" style="width:100%">
          <el-table-column prop="docNo" label="单号" min-width="140" show-overflow-tooltip />
          <el-table-column :prop="arTab === 'AR' ? 'partnerName' : 'partnerName'" label="客户/供应商" min-width="160" show-overflow-tooltip />
          <el-table-column :prop="arTab === 'AR' ? 'salesOrderNo' : 'purchaseOrderNo'" :label="arTab === 'AR' ? '销售订单' : '采购/委外单'" min-width="130" show-overflow-tooltip />
          <el-table-column :prop="arTab === 'AR' ? 'amount' : 'amount'" label="原金额" width="120" align="right"><template #default="{ row }">¥{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column :prop="arTab === 'AR' ? 'receivedAmount' : 'paidAmount'" :label="arTab === 'AR' ? '已收' : '已付'" width="120" align="right"><template #default="{ row }">¥{{ fmt(row.receivedAmount ?? row.paidAmount) }}</template></el-table-column>
          <el-table-column prop="remain" label="剩余" width="120" align="right"><template #default="{ row }"><span :class="isOverdue(row) ? 'num-red' : ''">¥{{ fmt(row.remain) }}</span></template></el-table-column>
          <el-table-column prop="dueDate" label="到期日" width="110" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </p-table>
        <div class="pagination-bar">
          <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[25, 50, 100]"
            :total="currentDetail.length" layout="total, sizes, prev, pager, next" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { statusType as globalStatusType } from '../utils/statusTag'
import { fmt } from '../utils/fmt'
import { ref, reactive, computed, onMounted } from 'vue'
import api from '../api'
import SvgBarChart from '../components/charts/SvgBarChart.vue'
import { usePaging } from '../composables/usePaging'

const loading = ref(false)
const data = reactive({})
const arTab = ref('AR')

// v6.4 金额格式统一（utils/fmt 千分位 2 位）
const statusTagType = (s) => globalStatusType(s, {UNPAID: 'danger', PARTIAL: 'warning', PAID: 'success'})   // v6.6 收口：全局 + 域局部
function statusLabel(s) { return { UNPAID: '未结清', PARTIAL: '部分结清', PAID: '已结清' }[s] || s }
function isOverdue(row) {
  if (!row.dueDate) return false
  const days = (Date.now() - new Date(row.dueDate + 'T00:00:00').getTime()) / 86400000
  return days > 0
}

const currentDetail = computed(() => arTab.value === 'AR' ? (data.arDetail || []) : (data.apDetail || []))
const { page, pageSize, pagedRows, resetPage } = usePaging(currentDetail)

async function loadData() {
  loading.value = true
  try {
    const res = await api.get('/report/aging')
    Object.assign(data, res)
    resetPage()
  } catch (err) { console.error(err) }
  loading.value = false
}

onMounted(loadData)
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.hint { font-size: 12px; color: #94a3b8; }
.report-loading { padding: 40px 20px; }
.chart-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.chart-card { background: var(--pims-card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.chart-card-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 16px; }
.kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
.kpi-card { background: var(--pims-card-bg); border-radius: 16px; padding: 18px 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.kpi-label { font-size: 13px; color: #64748b; margin-bottom: 8px; }
.kpi-value { font-size: 22px; font-weight: 800; color: var(--pims-text); }
.kpi-red { color: #b56a5c; }
.num-red { color: #b56a5c; font-weight: 700; }
.table-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.tab-count { font-size: 13px; color: #64748b; }
.pagination-bar { margin-top: 12px; display: flex; justify-content: flex-end; }
@media (max-width: 768px) { .chart-grid { grid-template-columns: 1fr; } }
</style>
