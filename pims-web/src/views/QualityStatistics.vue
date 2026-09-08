<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">质量统计分析</h2>
      <span class="report-sub">不良率总览 · 月度趋势 · 物料/供应商不良排行</span>
    </div>

    <div class="toolbar">
      <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
        start-placeholder="开始日期" end-placeholder="结束日期" style="width:260px" />
      <el-button type="primary" @click="fetch" :loading="loading" :disabled="!range">查询</el-button>
    </div>

    <el-skeleton :rows="8" animated v-if="loading" />

    <template v-if="data && !loading">
      <div class="kpi-row">
        <div class="kpi-card"><div class="kpi-label">检验批次</div><div class="kpi-value">{{ data.overall?.total || 0 }}</div></div>
        <div class="kpi-card"><div class="kpi-label">合格批次</div><div class="kpi-value green">{{ data.overall?.pass || 0 }}</div></div>
        <div class="kpi-card"><div class="kpi-label">不合格批次</div><div class="kpi-value red">{{ data.overall?.reject || 0 }}</div></div>
        <div class="kpi-card"><div class="kpi-label">其中让步接收</div><div class="kpi-value">{{ data.overall?.concession || 0 }}</div></div>
        <div class="kpi-card"><div class="kpi-label">综合不良率</div><div class="kpi-value" :class="Number(data.overall?.rate || 0) > 5 ? 'red' : ''"><b>{{ data.overall?.rate || 0 }}%</b></div></div>
      </div>

      <div class="chart-grid">
        <div class="table-card">
          <div class="card-title">月度不良率趋势</div>
          <SvgLineChart v-if="(data.monthly?.months || []).length"
            :labels="data.monthly.months"
            :series="[
              { name: '检验批次', data: data.monthly.total, color: '#6366f1' },
              { name: '不合格', data: data.monthly.reject, color: '#ef4444' }
            ]" :height="240" />
          <div v-else class="empty-tip">期间无判定记录</div>
          <div class="rate-row" v-if="(data.monthly?.months || []).length">
            <span v-for="(m, i) in data.monthly.months" :key="m" class="rate-chip" :class="Number(data.monthly.rate[i]) > 5 ? 'high' : ''">
              {{ m }}：<b>{{ data.monthly.rate[i] }}%</b>
            </span>
          </div>
        </div>
        <div class="table-card">
          <div class="card-title">按物料大类</div>
          <SvgDonutChart v-if="(data.byCategory || []).length"
            :data="data.byCategory.map(c => ({ label: catLabel(c.category), value: Number(c.reject) || 0.0001 }))" :size="200" />
          <div v-else class="empty-tip">无判定记录</div>
        </div>
      </div>

      <div class="chart-grid">
        <div class="table-card">
          <div class="card-title">不良物料 TOP20（按不合格批数）</div>
          <p-table :data="data.byMaterial || []" stripe border style="width:100%" max-height="360">
            <el-table-column prop="materialCode" label="物料编码" min-width="120" show-overflow-tooltip />
            <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="total" label="检验批数" width="100" align="center" />
            <el-table-column prop="reject" label="不合格" width="90" align="center" />
            <el-table-column prop="rate" label="不良率" width="100" align="center">
              <template #default="{ row }">
                <span :style="Number(row.rate) > 10 ? 'color:#ef4444;font-weight:600' : ''">{{ row.rate }}%</span>
              </template>
            </el-table-column>
          </p-table>
        </div>
        <div class="table-card">
          <div class="card-title">供应商不良 TOP20（按到货关联）</div>
          <p-table :data="data.bySupplier || []" stripe border style="width:100%" max-height="360">
            <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
            <el-table-column prop="total" label="检验批数" width="100" align="center" />
            <el-table-column prop="reject" label="不合格" width="90" align="center" />
            <el-table-column prop="rate" label="不良率" width="100" align="center">
              <template #default="{ row }">
                <span :style="Number(row.rate) > 10 ? 'color:#ef4444;font-weight:600' : ''">{{ row.rate }}%</span>
              </template>
            </el-table-column>
          </p-table>
        </div>
      </div>
    </template>

    <div class="empty-tip" v-if="!data && !loading">选择统计期间后查询</div>
  </div>
</template>

<script setup>
import { todayLocal } from '../utils/date'
// v6.3 质量统计分析：不良率总览/月趋势/物料 TOP/大类分布/供应商不良（经到货关联）
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import SvgLineChart from '../components/charts/SvgLineChart.vue'
import SvgDonutChart from '../components/charts/SvgDonutChart.vue'

const range = ref([halfYearAgo(), today()])
const data = ref(null)
const loading = ref(false)

function today() { return todayLocal() }
function halfYearAgo() {
  const d = new Date(); d.setMonth(d.getMonth() - 6)
  return d.toISOString().slice(0, 10)
}
function catLabel(c) {
  return { A: '原料', B: '半成品', C: '成品', P: '包装', F: '辅料', R: '五金', S: '其他' }[c] || c
}

async function fetch() {
  if (!range.value || range.value.length !== 2) { ElMessage.warning('请选择统计期间'); return }
  loading.value = true
  try {
    data.value = await api.get('/qc/statistics', { params: { from: range.value[0], to: range.value[1] } })
  } catch {} finally { loading.value = false }
}

onMounted(fetch)
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.report-title { margin: 0; font-size: 20px; }
.report-sub { font-size: 12px; color: #94a3b8; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-bottom: 16px; }
.kpi-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 14px 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.kpi-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.kpi-value { font-size: 20px; font-weight: 700; }
.kpi-value.red { color: #ef4444; }
.kpi-value.green { color: #16a34a; }
.chart-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 14px; }
@media (max-width: 1100px) { .chart-grid { grid-template-columns: 1fr } }
.table-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.card-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
.rate-row { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.rate-chip { font-size: 12px; background: #f1f5f9; border-radius: 6px; padding: 3px 8px; color: #475569; }
.rate-chip.high { background: #fef2f2; color: #ef4444; }
.empty-tip { text-align: center; color: #94a3b8; padding: 60px 0; font-size: 14px; }
</style>
