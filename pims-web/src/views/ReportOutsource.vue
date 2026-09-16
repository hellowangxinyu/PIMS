<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">委外报表</h2>
      <el-radio-group v-model="months" size="small" @change="loadData">
        <el-radio-button :value="3">近3月</el-radio-button>
        <el-radio-button :value="6">近6月</el-radio-button>
        <el-radio-button :value="12">近12月</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="loading" class="report-loading"><el-skeleton :rows="8" animated /></div>
    <template v-else>
      <div class="kpi-row">
        <div class="kpi-card"><div class="kpi-label">区间加工费合计</div><div class="kpi-value">¥{{ fmt(data.totalFee) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">代工厂数量</div><div class="kpi-value">{{ (data.processorRank || []).length }}</div></div>
      </div>

      <div class="chart-grid" style="margin-top:16px">
        <div class="chart-card full">
          <div class="chart-card-title">月度委外加工费（加工费单价 × 订单批量）</div>
          <SvgLineChart :labels="data.monthlyFee?.labels || []" :series="[{ name: '加工费', values: data.monthlyFee?.values || [], color: '#c2a069' }]" :height="240" />
        </div>
        <div class="chart-card">
          <div class="chart-card-title">代工厂加工费 TOP10</div>
          <SvgBarChart :data="data.processorRank || []" :horizontal="true" color="#c2a069" />
        </div>
        <div class="chart-card">
          <div class="chart-card-title">代工厂得率（已完成入库）</div>
          <p-table :data="data.processorYield || []" stripe border size="small" max-height="320">
            <el-table-column prop="processor" label="代工厂" min-width="160" show-overflow-tooltip />
            <el-table-column prop="batchCount" label="完成批次" width="90" align="right" />
            <el-table-column prop="avgYield" label="平均得率%" width="100" align="right">
              <template #default="{ row }"><span :class="Number(row.avgYield) >= 95 ? 'num-green' : 'num-orange'">{{ fmt(row.avgYield) }}</span></template>
            </el-table-column>
            <el-table-column prop="totalQty" label="入库总量" width="100" align="right" />
          </p-table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import SvgLineChart from '../components/charts/SvgLineChart.vue'
import SvgBarChart from '../components/charts/SvgBarChart.vue'

const months = ref(6)
const loading = ref(false)
const data = reactive({})

// v6.4 金额格式统一（utils/fmt 千分位 2 位）
async function loadData() {
  loading.value = true
  try {
    const res = await api.get(`/report/outsource?months=${months.value}`)
    Object.assign(data, res)
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
.chart-card.full { grid-column: 1 / -1; }
.chart-card-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 16px; }
.kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
.kpi-card { background: var(--pims-card-bg); border-radius: 16px; padding: 18px 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.kpi-label { font-size: 13px; color: #64748b; margin-bottom: 8px; }
.kpi-value { font-size: 22px; font-weight: 800; color: var(--pims-text); }
.num-green { color: #6f9a86; font-weight: 700; } .num-orange { color: #c2a069; font-weight: 700; }
@media (max-width: 768px) { .chart-grid { grid-template-columns: 1fr; } }
</style>
