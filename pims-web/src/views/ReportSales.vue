<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">销售报表</h2>
      <el-radio-group v-model="months" size="small" @change="loadData">
        <el-radio-button :value="3">近3月</el-radio-button>
        <el-radio-button :value="6">近6月</el-radio-button>
        <el-radio-button :value="12">近12月</el-radio-button>
      </el-radio-group>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <!-- ============ Tab1 销售统计 ============ -->
      <el-tab-pane label="销售统计" name="stats">
        <div v-if="loading" class="report-loading"><el-skeleton :rows="8" animated /></div>
        <template v-else>
          <div class="chart-grid">
            <div class="chart-card full">
              <div class="chart-card-title">月度销售趋势（收入 = 实发数量 × 订单单价）</div>
              <SvgLineChart :labels="data.monthlyTrend?.labels || []" :series="[
                { name: '销售收入', values: data.monthlyTrend?.income || [], color: '#6366f1' },
                { name: '销售成本', values: data.monthlyTrend?.cost || [], color: '#f59e0b' }
              ]" :height="240" />
            </div>
            <div class="chart-card">
              <div class="chart-card-title">客户销售 TOP10</div>
              <SvgBarChart :data="data.customerRank || []" :horizontal="true" color="#8b5cf6" />
            </div>
            <div class="chart-card">
              <div class="chart-card-title">产品销售 TOP10</div>
              <SvgBarChart :data="data.materialRank || []" :horizontal="true" color="#06b6d4" />
            </div>
            <div class="chart-card">
              <div class="chart-card-title">订单状态分布</div>
              <SvgDonutChart :data="statusDist" center-text="订单数" />
            </div>
            <div class="chart-card">
              <div class="chart-card-title">制单人销售排行（系统暂无业务员字段）</div>
              <SvgBarChart :data="data.salesmanRank || []" :horizontal="true" color="#10b981" />
            </div>
          </div>
        </template>
      </el-tab-pane>

      <!-- ============ Tab2 毛利分析 ============ -->
      <el-tab-pane label="毛利分析" name="margin">
        <div v-if="loading" class="report-loading"><el-skeleton :rows="8" animated /></div>
        <template v-else>
          <div class="kpi-row">
            <div class="kpi-card"><div class="kpi-label">总收入</div><div class="kpi-value">¥{{ fmt(margin.summary?.totalIncome) }}</div></div>
            <div class="kpi-card"><div class="kpi-label">总成本</div><div class="kpi-value">¥{{ fmt(margin.summary?.totalCost) }}</div></div>
            <div class="kpi-card"><div class="kpi-label">总毛利</div><div class="kpi-value" :class="numColor(margin.summary?.totalMargin)">¥{{ fmt(margin.summary?.totalMargin) }}</div></div>
            <div class="kpi-card"><div class="kpi-label">毛利率</div><div class="kpi-value" :class="numColor(margin.summary?.marginRate)">{{ fmt(margin.summary?.marginRate) }}%</div></div>
          </div>
          <div class="chart-grid" style="margin-top:16px">
            <div class="chart-card full">
              <div class="chart-card-title">月度收入 / 成本 / 毛利</div>
              <SvgLineChart :labels="margin.monthlyTrend?.labels || []" :series="[
                { name: '收入', values: margin.monthlyTrend?.income || [], color: '#6366f1' },
                { name: '成本', values: margin.monthlyTrend?.cost || [], color: '#f59e0b' },
                { name: '毛利', values: margin.monthlyTrend?.margin || [], color: '#10b981' }
              ]" :height="240" />
            </div>
            <div class="chart-card">
              <div class="chart-card-title">产品毛利 TOP10</div>
              <p-table :data="margin.productMargin || []" stripe border size="small" max-height="320">
                <el-table-column prop="name" label="产品" min-width="120" show-overflow-tooltip />
                <el-table-column prop="income" label="收入" width="110" align="right"><template #default="{ row }">¥{{ fmt(row.income) }}</template></el-table-column>
                <el-table-column prop="cost" label="成本" width="110" align="right"><template #default="{ row }">¥{{ fmt(row.cost) }}</template></el-table-column>
                <el-table-column prop="margin" label="毛利" width="110" align="right"><template #default="{ row }"><span :class="numColor(row.margin)">¥{{ fmt(row.margin) }}</span></template></el-table-column>
                <el-table-column prop="marginRate" label="毛利率" width="90" align="right"><template #default="{ row }">{{ fmt(row.marginRate) }}%</template></el-table-column>
              </p-table>
            </div>
            <div class="chart-card">
              <div class="chart-card-title">客户毛利 TOP10</div>
              <p-table :data="margin.customerMargin || []" stripe border size="small" max-height="320">
                <el-table-column prop="name" label="客户" min-width="120" show-overflow-tooltip />
                <el-table-column prop="income" label="收入" width="110" align="right"><template #default="{ row }">¥{{ fmt(row.income) }}</template></el-table-column>
                <el-table-column prop="cost" label="成本" width="110" align="right"><template #default="{ row }">¥{{ fmt(row.cost) }}</template></el-table-column>
                <el-table-column prop="margin" label="毛利" width="110" align="right"><template #default="{ row }"><span :class="numColor(row.margin)">¥{{ fmt(row.margin) }}</span></template></el-table-column>
                <el-table-column prop="marginRate" label="毛利率" width="90" align="right"><template #default="{ row }">{{ fmt(row.marginRate) }}%</template></el-table-column>
              </p-table>
            </div>
          </div>
        </template>
      </el-tab-pane>

      <!-- ============ Tab3 订单执行 ============ -->
      <el-tab-pane label="订单执行" name="exec">
        <div v-if="loading" class="report-loading"><el-skeleton :rows="8" animated /></div>
        <template v-else>
          <div class="kpi-row">
            <div class="kpi-card"><div class="kpi-label">明细总数</div><div class="kpi-value">{{ exec.summary?.total || 0 }}</div></div>
            <div class="kpi-card"><div class="kpi-label">已发货完成</div><div class="kpi-value kpi-green">{{ exec.summary?.completed || 0 }}</div></div>
            <div class="kpi-card"><div class="kpi-label">部分发货</div><div class="kpi-value kpi-orange">{{ exec.summary?.partial || 0 }}</div></div>
            <div class="kpi-card"><div class="kpi-label">未发货</div><div class="kpi-value kpi-red">{{ exec.summary?.none || 0 }}</div></div>
          </div>
          <div class="table-card" style="margin-top:16px">
            <div class="tab-toolbar">
              <el-radio-group v-model="execFilter" size="small">
                <el-radio-button :value="'ALL'">全部</el-radio-button>
                <el-radio-button :value="'DONE'">已完成</el-radio-button>
                <el-radio-button :value="'PART'">部分发货</el-radio-button>
                <el-radio-button :value="'NONE'">未发货</el-radio-button>
              </el-radio-group>
              <span class="tab-count">共 {{ execFiltered.length }} 条</span>
            </div>
            <p-table :data="execPagedRows" stripe border size="small" style="width:100%">
              <el-table-column prop="orderNo" label="订单号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
              <el-table-column prop="materialName" label="品名" min-width="140" show-overflow-tooltip />
              <el-table-column label="订单数/已发/退货" min-width="150" align="right">
                <template #default="{ row }">{{ fmtQty(row.qty) }} / {{ fmtQty(row.shippedQty) }} / {{ fmtQty(row.returnQty) }}</template>
              </el-table-column>
              <el-table-column label="发货完成率" width="180">
                <template #default="{ row }">
                  <el-progress :percentage="Math.min(Number(row.rate || 0), 100)" :stroke-width="10" :color="rateColor(row.rate)" />
                </template>
              </el-table-column>
              <el-table-column label="订单状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="orderStatusType(row.status)" size="small">{{ orderStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="orderDate" label="下单日期" width="110" />
              <el-table-column prop="expectedShipDate" label="预计发货" width="110" />
            </p-table>
            <div class="pagination-bar">
              <el-pagination v-model:current-page="execPage" v-model:page-size="execPageSize" :page-sizes="[25, 50, 100]"
                :total="execFiltered.length" layout="total, sizes, prev, pager, next" />
            </div>
          </div>
        </template>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { statusType as globalStatusType } from '../utils/statusTag'
import { fmt } from '../utils/fmt'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import api from '../api'
import SvgLineChart from '../components/charts/SvgLineChart.vue'
import SvgBarChart from '../components/charts/SvgBarChart.vue'
import SvgDonutChart from '../components/charts/SvgDonutChart.vue'
import { usePaging } from '../composables/usePaging'

const months = ref(6)
const loading = ref(false)
const tab = ref('stats')
const data = reactive({})
const margin = reactive({})
const exec = reactive({})

// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function fmtQty(v) { return Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 3 }) }
function numColor(v) { return Number(v) < 0 ? 'num-red' : (Number(v) > 0 ? 'num-green' : '') }
function rateColor(r) { return Number(r) >= 100 ? '#10b981' : (Number(r) > 0 ? '#f59e0b' : '#ef4444') }

const statusDist = computed(() => {
  const map = { DRAFT: '草稿', CONFIRMED: '已确认', SHIPPED: '已发货' }
  return (data.statusDist || []).map(s => ({ name: map[s.status] || s.status, value: s.count }))
})

const orderStatusType = (s) => globalStatusType(s, {DRAFT: 'info', CONFIRMED: 'warning', SHIPPED: 'success'})   // v6.6 收口：全局 + 域局部
function orderStatusLabel(s) { return { DRAFT: '草稿', CONFIRMED: '已确认', SHIPPED: '已发货' }[s] || s }

// ---- 订单执行筛选 + 分页 ----
const execFilter = ref('ALL')
const execFiltered = computed(() => {
  const rows = exec.rows || []
  if (execFilter.value === 'ALL') return rows
  return rows.filter(r => {
    const rate = Number(r.rate || 0)
    if (execFilter.value === 'DONE') return rate >= 100
    if (execFilter.value === 'PART') return rate > 0 && rate < 100
    return rate === 0
  })
})
const { page: execPage, pageSize: execPageSize, pagedRows: execPagedRows, resetPage: resetExecPage } = usePaging(execFiltered)

async function loadData() {
  loading.value = true
  try {
    const [s, m, e] = await Promise.all([
      api.get(`/report/sales?months=${months.value}`),
      api.get(`/report/margin?months=${months.value}`),
      api.get('/report/order-exec')
    ])
    Object.assign(data, s)
    Object.assign(margin, m)
    Object.assign(exec, e)
    resetExecPage()
  } catch (err) { console.error(err) }
  loading.value = false
}

watch(execFilter, resetExecPage)
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
.kpi-green { color: #10b981; } .kpi-orange { color: #f59e0b; } .kpi-red { color: #ef4444; }
.num-red { color: #ef4444; font-weight: 700; } .num-green { color: #10b981; font-weight: 700; }
.table-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
.tab-count { font-size: 13px; color: #64748b; }
.pagination-bar { margin-top: 12px; display: flex; justify-content: flex-end; }
@media (max-width: 768px) { .chart-grid { grid-template-columns: 1fr; } }
</style>
