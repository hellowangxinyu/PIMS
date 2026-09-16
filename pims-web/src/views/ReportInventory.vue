<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">库存报表</h2>
      <el-radio-group v-model="months" size="small" @change="loadData">
        <el-radio-button :value="3">近3月</el-radio-button>
        <el-radio-button :value="6">近6月</el-radio-button>
        <el-radio-button :value="12">近12月</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="loading" class="report-loading">
      <el-skeleton :rows="8" animated />
    </div>

    <template v-else>
      <div class="chart-grid">
        <div class="chart-card full">
          <div class="chart-card-title">月度出入库趋势</div>
          <SvgLineChart :labels="data.monthlyTrend?.labels || []" :series="[
            { name: '入库量', values: data.monthlyTrend?.inQty || [], color: '#6f9a86' },
            { name: '出库量', values: data.monthlyTrend?.outQty || [], color: '#b56a5c' }
          ]" :height="260" />
        </div>
        <div class="chart-card">
          <div class="chart-card-title">物料吞吐 TOP10</div>
          <SvgBarChart :data="data.materialRank || []" :horizontal="true" color="#7aa39a" />
        </div>
        <div class="chart-card">
          <div class="chart-card-title">仓库库存分布</div>
          <SvgDonutChart :data="data.warehouseDist || []" center-text="库存总量" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import SvgLineChart from '../components/charts/SvgLineChart.vue'
import SvgBarChart from '../components/charts/SvgBarChart.vue'
import SvgDonutChart from '../components/charts/SvgDonutChart.vue'

const months = ref(6)
const loading = ref(false)
const data = reactive({})

async function loadData() {
  loading.value = true
  try {
    const res = await api.get(`/report/inventory?months=${months.value}`)
    Object.assign(data, res)
  } catch (e) { console.error(e) }
  loading.value = false
}

onMounted(loadData)
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.report-loading { padding: 40px 20px; }
.chart-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.chart-card { background: var(--pims-card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.chart-card.full { grid-column: 1 / -1; }
.chart-card-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 16px; }
@media (max-width: 768px) {
  .chart-grid { grid-template-columns: 1fr; }
  .report-header { flex-direction: column; align-items: flex-start; }
}
</style>
