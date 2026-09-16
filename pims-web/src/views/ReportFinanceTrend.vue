<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">趋势分析</h2>
      <el-radio-group v-model="months" size="small" @change="loadChart">
        <el-radio-button :value="3">近3月</el-radio-button>
        <el-radio-button :value="6">近6月</el-radio-button>
        <el-radio-button :value="12">近12月</el-radio-button>
      </el-radio-group>
    </div>
    <div v-if="loading" class="report-loading">
      <el-skeleton :rows="8" animated />
    </div>
    <template v-else>
      <div class="summary-cards">
        <div class="summary-card">
          <div class="summary-label">应收总额</div>
          <div class="summary-value">¥{{ fmt(data.summary?.arTotal) }}</div>
          <div class="progress-wrap">
            <div class="progress-bar green" :style="{ width: (data.summary?.arRate || 0) + '%' }"></div>
          </div>
          <div class="summary-sub">已回款 ¥{{ fmt(data.summary?.arReceived) }}（{{ data.summary?.arRate || 0 }}%）</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">应付总额</div>
          <div class="summary-value">¥{{ fmt(data.summary?.apTotal) }}</div>
          <div class="progress-wrap">
            <div class="progress-bar amber" :style="{ width: (data.summary?.apRate || 0) + '%' }"></div>
          </div>
          <div class="summary-sub">已付款 ¥{{ fmt(data.summary?.apPaid) }}（{{ data.summary?.apRate || 0 }}%）</div>
        </div>
      </div>
      <div class="chart-grid">
        <div class="chart-card full">
          <div class="chart-card-title">月度应收应付趋势</div>
          <SvgLineChart :labels="data.monthlyTrend?.labels || []" :series="[
            { name: '应收', values: data.monthlyTrend?.arAmount || [], color: '#7288a5' },
            { name: '应付', values: data.monthlyTrend?.apAmount || [], color: '#c2a069' }
          ]" :height="260" />
        </div>
        <div class="chart-card full">
          <div class="chart-card-title">月度收支对比</div>
          <SvgBarChart :data="compareData" :horizontal="false" :height="220" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, reactive, computed, onMounted } from 'vue'
import api from '../api'
import SvgLineChart from '../components/charts/SvgLineChart.vue'
import SvgBarChart from '../components/charts/SvgBarChart.vue'

const months = ref(6)
const loading = ref(false)
const data = reactive({})

// v6.4 金额格式统一（utils/fmt 千分位 2 位）
const compareData = computed(() => {
  const labels = data.compare?.labels || []
  const income = data.compare?.income || []
  const expense = data.compare?.expense || []
  const items = []
  labels.forEach((lb, i) => {
    items.push({ name: lb.slice(5) + '收', value: income[i] || 0 })
    items.push({ name: lb.slice(5) + '支', value: expense[i] || 0 })
  })
  return items
})

async function loadChart() {
  loading.value = true
  try {
    const res = await api.get(`/report/finance?months=${months.value}`)
    Object.assign(data, res)
  } catch {} finally { loading.value = false }
}

onMounted(() => { loadChart() })
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.report-loading { padding: 40px 20px; }
.summary-cards { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.summary-label { font-size: 13px; color: var(--pims-text-secondary); margin-bottom: 6px; }
.summary-value { font-size: 24px; font-weight: 800; color: var(--pims-text); margin-bottom: 12px; }
.progress-wrap { height: 8px; background: var(--pims-border-light, #e5e7eb); border-radius: 4px; overflow: hidden; margin-bottom: 8px; }
.progress-bar { height: 100%; border-radius: 4px; transition: width 0.6s ease; }
.progress-bar.green { background: linear-gradient(90deg, #6f9a86, #5f8776); }
.progress-bar.amber { background: linear-gradient(90deg, #c2a069, #d97706); }
.summary-sub { font-size: 12px; color: var(--pims-text-secondary); }
.chart-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.chart-card { background: var(--pims-card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.chart-card.full { grid-column: 1 / -1; }
.chart-card-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 16px; }
@media (max-width: 768px) {
  .chart-grid { grid-template-columns: 1fr; }
  .summary-cards { grid-template-columns: 1fr; }
}
</style>
