<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">经营看板</h2>
      <el-radio-group v-model="months" size="small" @change="loadData">
        <el-radio-button :value="3">近3月</el-radio-button>
        <el-radio-button :value="6">近6月</el-radio-button>
        <el-radio-button :value="12">近12月</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="loading" class="report-loading"><el-skeleton :rows="8" animated /></div>
    <template v-else>
      <div class="kpi-row">
        <div class="kpi-card"><div class="kpi-label">当月销售额（出库口径）</div><div class="kpi-value">¥{{ fmt(data.kpi?.sales) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">当月采购金额</div><div class="kpi-value kpi-orange">¥{{ fmt(data.kpi?.purchase) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">当月委外加工费</div><div class="kpi-value">¥{{ fmt(data.kpi?.fee) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">当月收款</div><div class="kpi-value kpi-green">¥{{ fmt(data.kpi?.receipt) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">当月付款</div><div class="kpi-value kpi-red">¥{{ fmt(data.kpi?.payment) }}</div></div>
      </div>

      <div class="kpi-row" style="margin-top:16px">
        <div class="kpi-card"><div class="kpi-label">区间累计销售</div><div class="kpi-value">¥{{ fmt(data.summary?.salesTotal) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">区间累计成本</div><div class="kpi-value">¥{{ fmt(data.summary?.costTotal) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">区间累计毛利</div><div class="kpi-value" :class="Number(data.summary?.margin) < 0 ? 'kpi-red' : 'kpi-green'">¥{{ fmt(data.summary?.margin) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">区间累计采购</div><div class="kpi-value kpi-orange">¥{{ fmt(data.summary?.purchaseTotal) }}</div></div>
      </div>

      <div class="chart-grid" style="margin-top:16px">
        <div class="chart-card full">
          <div class="chart-card-title">月度经营对比（销售 / 采购 / 收款 / 付款 / 加工费）</div>
          <SvgLineChart :labels="data.monthlyTrend?.labels || []" :series="[
            { name: '销售', values: data.monthlyTrend?.sales || [], color: '#7288a5' },
            { name: '采购', values: data.monthlyTrend?.purchase || [], color: '#c2a069' },
            { name: '收款', values: data.monthlyTrend?.receipt || [], color: '#6f9a86' },
            { name: '付款', values: data.monthlyTrend?.payment || [], color: '#b56a5c' },
            { name: '加工费', values: data.monthlyTrend?.fee || [], color: '#9a8bb8' }
          ]" :height="280" />
        </div>
      </div>

      <!-- ===== 经营驾驶舱（v5.24）：排行 / 库存预警 / 订单执行，月份随上方切换联动 ===== -->
      <div class="cockpit">
        <div class="cockpit-header">
          <h3 class="cockpit-title">
            <el-icon class="cockpit-ic"><Odometer /></el-icon>经营驾驶舱
          </h3>
          <span class="cockpit-sub">近 {{ months }} 月 · 销售/毛利排行、库存预警、订单执行全景</span>
        </div>
        <div class="cockpit-grid">
          <!-- 销售排行 -->
          <div class="cockpit-card">
            <div class="cockpit-card-title">
              <span>销售排行 TOP8</span>
              <el-radio-group v-model="salesTab" size="small">
                <el-radio-button value="product">产品</el-radio-button>
                <el-radio-button value="customer">客户</el-radio-button>
              </el-radio-group>
            </div>
            <SvgBarChart :data="salesRank" horizontal />
          </div>
          <!-- 毛利排行 -->
          <div class="cockpit-card">
            <div class="cockpit-card-title">
              <span>毛利排行 TOP8</span>
              <span class="cockpit-card-note">综合毛利率 {{ marginRate }}%</span>
              <el-radio-group v-model="marginTab" size="small" style="margin-left:auto">
                <el-radio-button value="product">产品</el-radio-button>
                <el-radio-button value="customer">客户</el-radio-button>
              </el-radio-group>
            </div>
            <SvgBarChart :data="marginRank" horizontal :color="'#6f9a86'" :colors="['#6f9a86','#93b5a4','#5f8776','#a8c4b6','#6f9a86','#93b5a4','#5f8776','#a8c4b6','#6f9a86','#93b5a4']" />
          </div>
          <!-- 库存预警 -->
          <div class="cockpit-card">
            <div class="cockpit-card-title">
              <span>库存预警</span>
              <el-radio-group v-model="warnTab" size="small" style="margin-left:auto">
                <el-radio-button value="low">低库存</el-radio-button>
                <el-radio-button value="expiry">临期批次</el-radio-button>
              </el-radio-group>
            </div>
            <div v-if="warnTab === 'low'" class="warn-list">
              <div v-for="w in lowStock.slice(0, 8)" :key="'l' + w.materialCode" class="warn-row">
                <span class="warn-dot" :class="w.level === 'RED' ? 'dot-red' : 'dot-orange'"></span>
                <span class="warn-name" :title="w.materialCode + ' ' + w.materialName">{{ w.materialName }}</span>
                <span class="warn-meta">{{ w.availableDays }} 天可用 · 库存 {{ w.currentQty }} {{ w.unit }}</span>
              </div>
              <el-empty v-if="!lowStock.length" description="暂无低库存预警" :image-size="56" />
            </div>
            <div v-else class="warn-list">
              <div v-for="w in expiryRows.slice(0, 8)" :key="'e' + w.batchNo" class="warn-row">
                <span class="warn-dot" :class="w.level === 'EXPIRED' ? 'dot-red' : 'dot-orange'"></span>
                <span class="warn-name" :title="w.materialName + ' ' + w.batchNo">{{ w.materialName }}</span>
                <span class="warn-meta">{{ w.batchNo }} · {{ w.remainDays < 0 ? '已过期 ' + (-w.remainDays) + ' 天' : '剩 ' + w.remainDays + ' 天' }} · {{ w.qty }}{{ w.unit }}</span>
              </div>
              <el-empty v-if="!expiryRows.length" description="暂无临期批次" :image-size="56" />
            </div>
          </div>
          <!-- 销售订单执行 -->
          <div class="cockpit-card">
            <div class="cockpit-card-title"><span>销售订单执行</span></div>
            <div class="exec-body">
              <SvgDonutChart :data="execDonut" :size="150" center-text="订单明细" />
              <div class="exec-stats">
                <div class="exec-stat good"><b>{{ execSummary.completed }}</b><span>已发完</span></div>
                <div class="exec-stat warn"><b>{{ execSummary.partial }}</b><span>部分发货</span></div>
                <div class="exec-stat bad"><b>{{ execSummary.none }}</b><span>未发货</span></div>
                <div class="exec-stat total"><b>{{ execSummary.total }}</b><span>明细总数</span></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, reactive, computed, onMounted } from 'vue'
import { Odometer } from '@element-plus/icons-vue'
import api from '../api'
import SvgLineChart from '../components/charts/SvgLineChart.vue'
import SvgBarChart from '../components/charts/SvgBarChart.vue'
import SvgDonutChart from '../components/charts/SvgDonutChart.vue'

const months = ref(6)
const loading = ref(false)
const data = reactive({})
// 驾驶舱状态
const salesTab = ref('product')
const marginTab = ref('product')
const warnTab = ref('low')
const cockpit = reactive({ sales: {}, margin: {}, lowStock: [], expiry: [], exec: {} })

// v6.4 金额格式统一（utils/fmt 千分位 2 位）
const salesRank = computed(() => {
  const src = salesTab.value === 'product' ? cockpit.sales.materialRank : cockpit.sales.customerRank
  return (src || []).slice(0, 8)
})
const marginRank = computed(() => {
  const src = marginTab.value === 'product' ? cockpit.margin.productMargin : cockpit.margin.customerMargin
  return (src || []).slice(0, 8).map(r => ({ name: r.name, value: r.margin }))
})
const marginRate = computed(() => cockpit.margin.summary?.marginRate ?? '—')
const lowStock = computed(() => cockpit.lowStock || [])
const expiryRows = computed(() => cockpit.expiry || [])
const execSummary = computed(() => cockpit.exec.summary || { total: 0, completed: 0, partial: 0, none: 0 })
const execDonut = computed(() => [
  { name: '已发完', value: execSummary.value.completed },
  { name: '部分发货', value: execSummary.value.partial },
  { name: '未发货', value: execSummary.value.none }
])

async function loadData() {
  loading.value = true
  try {
    const [ov, sales, margin, low, exp, exec] = await Promise.all([
      api.get(`/report/overview?months=${months.value}`),
      api.get(`/report/sales?months=${months.value}`),
      api.get(`/report/margin?months=${months.value}`),
      api.get('/report/low-stock'),
      api.get('/report/expiry'),
      api.get('/report/order-exec')
    ])
    Object.assign(data, ov)
    Object.assign(cockpit, { sales, margin, lowStock: low.rows || [], expiry: exp.rows || [], exec })
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
.kpi-green { color: #6f9a86; } .kpi-orange { color: #c2a069; } .kpi-red { color: #b56a5c; }
@media (max-width: 768px) { .chart-grid { grid-template-columns: 1fr; } }

/* ===== 经营驾驶舱 ===== */
.cockpit { margin-top: 24px; }
.cockpit-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.cockpit-title { display: flex; align-items: center; gap: 8px; font-size: 18px; font-weight: 800; color: var(--pims-text); margin: 0; letter-spacing: -0.3px; }
.cockpit-ic { font-size: 19px; color: var(--pims-primary); }
.cockpit-sub { font-size: 12px; color: #94a3b8; }

.cockpit-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.cockpit-card {
  background: var(--pims-card-bg);
  border-radius: 16px;
  padding: 18px 20px 20px;
  box-shadow: var(--pims-card-shadow);
  border: var(--pims-card-border);
  position: relative;
  overflow: hidden;
  transition: box-shadow var(--pims-transition-base);
}
/* 驾驶舱卡片顶部品牌渐变条 */
.cockpit-card::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, var(--pims-primary), var(--pims-primary-light), var(--pims-accent));
  opacity: 0.85;
}
.cockpit-card:hover { box-shadow: var(--pims-card-shadow-hover); }
.cockpit-card-title { display: flex; align-items: center; gap: 10px; font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 12px; }
.cockpit-card-note { font-size: 12px; font-weight: 500; color: #6f9a86; }

/* 库存预警列表 */
.warn-list { display: flex; flex-direction: column; }
.warn-row {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 4px;
  border-bottom: 1px dashed var(--pims-border-light);
  font-size: 13px;
}
.warn-row:last-child { border-bottom: none; }
.warn-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot-red { background: #b56a5c; box-shadow: 0 0 0 3px rgba(239,68,68,0.12); }
.dot-orange { background: #c2a069; box-shadow: 0 0 0 3px rgba(245,158,11,0.12); }
.warn-name { font-weight: 500; color: var(--pims-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 40%; flex-shrink: 0; }
.warn-meta { margin-left: auto; font-size: 12px; color: #94a3b8; font-variant-numeric: tabular-nums; white-space: nowrap; }

/* 订单执行 */
.exec-body { display: flex; align-items: center; justify-content: space-around; gap: 16px; padding: 6px 0; flex-wrap: wrap; }
.exec-stats { display: grid; grid-template-columns: repeat(2, 96px); gap: 10px; }
.exec-stat {
  background: #f8fafc;
  border: 1px solid var(--pims-border-light);
  border-radius: 12px;
  padding: 12px 8px;
  text-align: center;
}
.exec-stat b { display: block; font-size: 20px; font-weight: 800; line-height: 1.2; }
.exec-stat span { font-size: 12px; color: #64748b; }
.exec-stat.good b { color: #6f9a86; }
.exec-stat.warn b { color: #c2a069; }
.exec-stat.bad b { color: #b56a5c; }
.exec-stat.total { background: linear-gradient(135deg, var(--pims-primary), var(--pims-primary-dark)); border: none; }
.exec-stat.total b, .exec-stat.total span { color: #fff; }

@media (max-width: 1024px) { .cockpit-grid { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .warn-name { max-width: 55%; } }
</style>
