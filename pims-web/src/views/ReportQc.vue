<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">质检报表</h2>
      <el-radio-group v-model="months" size="small" @change="loadData">
        <el-radio-button :value="3">近3月</el-radio-button>
        <el-radio-button :value="6">近6月</el-radio-button>
        <el-radio-button :value="12">近12月</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="loading" class="report-loading"><el-skeleton :rows="8" animated /></div>
    <template v-else>
      <div class="kpi-row">
        <div class="kpi-card"><div class="kpi-label">质检单总数</div><div class="kpi-value">{{ totalCount }}</div></div>
        <div class="kpi-card"><div class="kpi-label">整体合格率（含让步）</div><div class="kpi-value kpi-green">{{ fmt(overallRate) }}%</div></div>
        <div class="kpi-card"><div class="kpi-label">不合格单数</div><div class="kpi-value kpi-red">{{ rejectCount }}</div></div>
      </div>

      <div class="chart-grid" style="margin-top:16px">
        <div class="chart-card">
          <div class="chart-card-title">月度质检单数</div>
          <SvgBarChart :data="monthlyCounts" :horizontal="false" height="240" color="#6366f1" />
        </div>
        <div class="chart-card">
          <div class="chart-card-title">月度合格率（%）</div>
          <SvgLineChart :labels="data.monthlyTrend?.labels || []" :series="[{ name: '合格率', values: data.monthlyTrend?.passRate || [], color: '#10b981' }]" :height="240" />
        </div>
        <div class="chart-card">
          <div class="chart-card-title">判定分布</div>
          <SvgDonutChart :data="data.resultDist || []" center-text="质检单" />
        </div>
        <div class="chart-card">
          <div class="chart-card-title">不合格（退货）物料 TOP10</div>
          <SvgBarChart :data="data.rejectMaterialRank || []" :horizontal="true" color="#ef4444" />
        </div>
      </div>

      <div class="table-card" style="margin-top:16px">
        <div class="tab-toolbar">
          <div>
            <el-radio-group v-model="statusFilter" size="small" @change="onFilter">
              <el-radio-button :value="'ALL'">全部</el-radio-button>
              <el-radio-button :value="'PASS'">合格</el-radio-button>
              <el-radio-button :value="'CONCESSION'">让步接收</el-radio-button>
              <el-radio-button :value="'REJECT'">不合格</el-radio-button>
              <el-radio-button :value="'PENDING'">待质检</el-radio-button>
            </el-radio-group>
            <el-input v-model="searchText" placeholder="搜索品名/编码/批号/质检单号" size="small" clearable style="width:220px;margin-left:12px" @input="onFilter" />
          </div>
          <span class="tab-count">共 {{ detailFiltered.length }} 条</span>
        </div>
        <p-table :data="pagedRows" stripe border size="small" style="width:100%">
          <el-table-column prop="inspectionNo" label="质检单号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="refDocNo" label="关联单号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="materialName" label="品名" min-width="140" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="编码" min-width="110" show-overflow-tooltip />
          <el-table-column prop="category" label="类别" width="80" align="center" />
          <el-table-column prop="batchNo" label="批次" width="120" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="100" align="right" />
          <el-table-column label="判定" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="resultRemark" label="判定说明" min-width="160" show-overflow-tooltip />
          <el-table-column prop="inspector" label="检验员" width="90" />
          <el-table-column prop="inspectDate" label="检验日期" width="110" />
        </p-table>
        <div class="pagination-bar">
          <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[25, 50, 100]"
            :total="detailFiltered.length" layout="total, sizes, prev, pager, next" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import api from '../api'
import SvgLineChart from '../components/charts/SvgLineChart.vue'
import SvgBarChart from '../components/charts/SvgBarChart.vue'
import SvgDonutChart from '../components/charts/SvgDonutChart.vue'
import { usePaging } from '../composables/usePaging'

const months = ref(6)
const loading = ref(false)
const data = reactive({})
const statusFilter = ref('ALL')
const searchText = ref('')

// v6.4 金额格式统一（utils/fmt 千分位 2 位）
const monthlyCounts = computed(() => (data.monthlyTrend?.labels || []).map((lb, i) => ({ name: lb.slice(5) + '月', value: data.monthlyTrend.total[i] })))
const totalCount = computed(() => (data.monthlyTrend?.total || []).reduce((a, b) => a + b, 0))
const rejectCount = computed(() => ((data.resultDist || []).find(d => d.name === '不合格')?.value) || 0)
const overallRate = computed(() => {
  const total = totalCount.value
  if (!total) return 0
  const pass = (data.resultDist || []).filter(d => d.name === '合格' || d.name === '让步接收').reduce((a, d) => a + d.value, 0)
  return pass * 100 / total
})

function statusTagType(s) { return { PASS: 'success', CONCESSION: 'warning', REJECT: 'danger', PENDING: 'info' }[s] || 'info' }
function statusLabel(s) { return { PASS: '合格', CONCESSION: '让步', REJECT: '不合格', PENDING: '待检' }[s] || s }

const detailFiltered = computed(() => {
  const kw = searchText.value.trim().toLowerCase()
  return (data.detail || []).filter(d => {
    if (statusFilter.value !== 'ALL' && d.status !== statusFilter.value) return false
    if (!kw) return true
    return [d.materialName, d.materialCode, d.batchNo, d.inspectionNo].some(v => v && String(v).toLowerCase().includes(kw))
  })
})
const { page, pageSize, pagedRows, resetPage } = usePaging(detailFiltered)
function onFilter() { resetPage() }
watch(statusFilter, onFilter)

async function loadData() {
  loading.value = true
  try {
    const res = await api.get(`/report/qc?months=${months.value}`)
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
.report-loading { padding: 40px 20px; }
.chart-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.chart-card { background: var(--pims-card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.chart-card-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 16px; }
.kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
.kpi-card { background: var(--pims-card-bg); border-radius: 16px; padding: 18px 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.kpi-label { font-size: 13px; color: #64748b; margin-bottom: 8px; }
.kpi-value { font-size: 22px; font-weight: 800; color: var(--pims-text); }
.kpi-green { color: #10b981; } .kpi-red { color: #ef4444; }
.table-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
.tab-count { font-size: 13px; color: #64748b; }
.pagination-bar { margin-top: 12px; display: flex; justify-content: flex-end; }
@media (max-width: 768px) { .chart-grid { grid-template-columns: 1fr; } }
</style>
