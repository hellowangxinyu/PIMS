<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">库存分析</h2>
      <span class="hint">库龄 = 批次入库至今；呆滞按 库龄天数 × 库存金额 排序</span>
    </div>

    <div v-if="loading" class="report-loading"><el-skeleton :rows="8" animated /></div>
    <template v-else>
      <div class="kpi-row">
        <div class="kpi-card"><div class="kpi-label">库存总金额</div><div class="kpi-value">¥{{ fmt(data.stockAmountTotal) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">领用 vs 标准对比行数</div><div class="kpi-value">{{ (data.usageCompare || []).length }}</div></div>
        <div class="kpi-card"><div class="kpi-label">呆滞批次（&gt;30天）</div><div class="kpi-value kpi-orange">{{ (data.dormantRank || []).length }}</div></div>
      </div>

      <div class="chart-grid" style="margin-top:16px">
        <div class="chart-card">
          <div class="chart-card-title">批次库龄分布（金额）</div>
          <SvgBarChart :data="ageAmountData" :horizontal="false" height="240" color="#9a8bb8" />
        </div>
        <div class="chart-card">
          <div class="chart-card-title">库存金额按物料大类</div>
          <SvgDonutChart :data="categoryAmountData" center-text="库存金额" />
        </div>
      </div>

      <div class="table-card" style="margin-top:16px">
        <div class="tab-toolbar"><span class="tab-count">呆滞批次 TOP20（按 库龄×金额）</span></div>
        <p-table :data="data.dormantRank || []" stripe border size="small" style="width:100%">
          <el-table-column prop="materialName" label="品名" min-width="150" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="编码" min-width="110" show-overflow-tooltip />
          <el-table-column prop="batchNo" label="批次" width="130" show-overflow-tooltip />
          <el-table-column prop="days" label="库龄(天)" width="100" align="right">
            <template #default="{ row }"><span :class="row.days >= 180 ? 'num-red' : (row.days >= 90 ? 'num-orange' : '')">{{ row.days }}</span></template>
          </el-table-column>
          <el-table-column prop="qty" label="库存量" width="110" align="right" />
          <el-table-column prop="amount" label="金额" width="130" align="right"><template #default="{ row }">¥{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column prop="warehouseName" label="仓库" min-width="120" show-overflow-tooltip />
        </p-table>
      </div>

      <div class="table-card" style="margin-top:16px">
        <div class="tab-toolbar">
          <el-input v-model="usageSearch" placeholder="搜索订单号/品名/编码" size="small" clearable style="width:220px" @input="resetUsagePage" />
          <span class="tab-count">实际领用 vs 配方标准用量（近12个月，差异 = 实际 − 标准）共 {{ usageFiltered.length }} 条</span>
        </div>
        <p-table :data="usagePagedRows" stripe border size="small" style="width:100%">
          <el-table-column prop="orderNo" label="生产订单" min-width="140" show-overflow-tooltip />
          <el-table-column prop="productName" label="产品" min-width="130" show-overflow-tooltip />
          <el-table-column prop="materialName" label="物料" min-width="130" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="编码" min-width="110" show-overflow-tooltip />
          <el-table-column prop="actual" label="实际领用" width="110" align="right" />
          <el-table-column prop="standard" label="配方标准" width="110" align="right" />
          <el-table-column prop="diff" label="差异" width="110" align="right">
            <template #default="{ row }"><span :class="Number(row.diff) > 0.001 ? 'num-red' : (Number(row.diff) < -0.001 ? 'num-green' : '')">{{ fmtQty(row.diff) }}</span></template>
          </el-table-column>
        </p-table>
        <div class="pagination-bar">
          <el-pagination v-model:current-page="usagePage" v-model:page-size="usagePageSize" :page-sizes="[25, 50, 100]"
            :total="usageFiltered.length" layout="total, sizes, prev, pager, next" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, reactive, computed, onMounted } from 'vue'
import api from '../api'
import SvgBarChart from '../components/charts/SvgBarChart.vue'
import SvgDonutChart from '../components/charts/SvgDonutChart.vue'
import { usePaging } from '../composables/usePaging'

const loading = ref(false)
const data = reactive({})
const usageSearch = ref('')

// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function fmtQty(v) { return Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 3 }) }

const ageAmountData = computed(() => (data.ageDist || []).map(b => ({ name: b.name, value: b.amount })))
const categoryAmountData = computed(() => (data.categoryAmount || []).map(c => ({ name: c.name, value: c.amount })))

const usageFiltered = computed(() => {
  const kw = usageSearch.value.trim().toLowerCase()
  if (!kw) return data.usageCompare || []
  return (data.usageCompare || []).filter(u => [u.orderNo, u.productName, u.materialName, u.materialCode].some(v => v && String(v).toLowerCase().includes(kw)))
})
const { page: usagePage, pageSize: usagePageSize, pagedRows: usagePagedRows, resetPage: resetUsagePage } = usePaging(usageFiltered)

async function loadData() {
  loading.value = true
  try {
    const res = await api.get('/report/stock-analysis')
    Object.assign(data, res)
    resetUsagePage()
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
.kpi-orange { color: #c2a069; }
.num-red { color: #b56a5c; font-weight: 700; } .num-orange { color: #c2a069; font-weight: 700; } .num-green { color: #6f9a86; font-weight: 700; }
.table-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
.tab-count { font-size: 13px; color: #64748b; }
.pagination-bar { margin-top: 12px; display: flex; justify-content: flex-end; }
@media (max-width: 768px) { .chart-grid { grid-template-columns: 1fr; } }
</style>
