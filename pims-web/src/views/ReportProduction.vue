<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">生产报表</h2>
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
          <div class="chart-card-title">月度订单趋势（生产 vs 委外）</div>
          <SvgLineChart :labels="data.monthlyTrend?.labels || []" :series="[
            { name: '生产订单', values: data.monthlyTrend?.production || [], color: '#6366f1' },
            { name: '委外订单', values: data.monthlyTrend?.outsource || [], color: '#f59e0b' }
          ]" :height="260" />
        </div>
        <div class="chart-card">
          <div class="chart-card-title">完工率统计</div>
          <SvgDonutChart :data="completionData" center-text="订单总数" />
          <div class="rate-info">
            <div class="rate-row">
              <span>生产完工率</span>
              <strong>{{ prodRate }}%</strong>
            </div>
            <div class="rate-row">
              <span>委外完工率</span>
              <strong>{{ outRate }}%</strong>
            </div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-card-title">月度产量对比</div>
          <SvgBarChart :data="qtyData" :horizontal="false" :height="220" />
        </div>
        <div class="chart-card full">
          <div class="chart-card-title">批次得率趋势（近 {{ data.yieldTrend?.length || 0 }} 个批次，得率 = 实际产出 ÷ 理论产出）</div>
          <SvgLineChart v-if="data.yieldTrend && data.yieldTrend.length" :labels="yieldLabels" :series="[
            { name: '得率(%)', values: yieldValues, color: '#16a34a' }
          ]" :height="260" />
          <el-empty v-else description="暂无已确认入库的得率数据" :image-size="50" />
        </div>
        <div class="chart-card full">
          <div class="chart-card-title">损耗分析（按产品汇总：损耗量 = 理论产出 − 实际产出，负数为超产）</div>
          <p-table v-if="data.lossAnalysis && data.lossAnalysis.length" :data="data.lossAnalysis" size="small" border style="width:100%">
            <el-table-column prop="productName" label="产品" min-width="150" show-overflow-tooltip />
            <el-table-column prop="theoreticalQty" label="理论产出" width="110" align="right" />
            <el-table-column prop="actualQty" label="实际产出" width="110" align="right" />
            <el-table-column label="损耗量" width="110" align="right">
              <template #default="{ row }">
                <span :class="Number(row.lossQty) > 0 ? 'loss-bad' : 'loss-good'">{{ row.lossQty }}</span>
              </template>
            </el-table-column>
            <el-table-column label="损耗率" width="110" align="right">
              <template #default="{ row }">
                <span :class="Number(row.lossRate) > 0 ? 'loss-bad' : 'loss-good'">{{ Number(row.lossRate).toFixed(2) }}%</span>
              </template>
            </el-table-column>
          </p-table>
          <el-empty v-else description="暂无损耗数据" :image-size="50" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import api from '../api'
import SvgLineChart from '../components/charts/SvgLineChart.vue'
import SvgBarChart from '../components/charts/SvgBarChart.vue'
import SvgDonutChart from '../components/charts/SvgDonutChart.vue'

const months = ref(6)
const loading = ref(false)
const data = reactive({})

const completionData = computed(() => {
  const cr = data.completionRate || {}
  return [
    { name: '生产已完工', value: cr.prodCompleted || 0 },
    { name: '生产未完工', value: Math.max((cr.prodTotal || 0) - (cr.prodCompleted || 0), 0) },
    { name: '委外已完工', value: cr.outCompleted || 0 },
    { name: '委外未完工', value: Math.max((cr.outTotal || 0) - (cr.outCompleted || 0), 0) }
  ]
})

const prodRate = computed(() => {
  const cr = data.completionRate || {}
  if (!cr.prodTotal) return 0
  return Math.round((cr.prodCompleted / cr.prodTotal) * 100)
})

const outRate = computed(() => {
  const cr = data.completionRate || {}
  if (!cr.outTotal) return 0
  return Math.round((cr.outCompleted / cr.outTotal) * 100)
})

const qtyData = computed(() => {
  const labels = data.monthlyQty?.labels || []
  const prod = data.monthlyQty?.production || []
  const out = data.monthlyQty?.outsource || []
  const items = []
  labels.forEach((lb, i) => {
    items.push({ name: lb.slice(5) + '产', value: prod[i] || 0 })
    items.push({ name: lb.slice(5) + '委', value: out[i] || 0 })
  })
  return items
})

// 批次得率趋势：横轴为批号（附类型），纵轴为得率%
const yieldLabels = computed(() => (data.yieldTrend || []).map(p => (p.type === '委外' ? '委·' : '') + p.batchNo))
const yieldValues = computed(() => (data.yieldTrend || []).map(p => Number(p.yieldRate)))

async function loadData() {
  loading.value = true
  try {
    const res = await api.get(`/report/production?months=${months.value}`)
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
.rate-info { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--pims-border-light, #e5e7eb); }
.rate-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 13px; color: var(--pims-text-secondary); }
.rate-row strong { color: var(--pims-text); font-size: 15px; }
.loss-bad { color: #dc2626; font-weight: 600; }
.loss-good { color: #16a34a; font-weight: 600; }
@media (max-width: 768px) {
  .chart-grid { grid-template-columns: 1fr; }
  .report-header { flex-direction: column; align-items: flex-start; }
}
</style>
