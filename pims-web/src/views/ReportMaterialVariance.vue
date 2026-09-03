<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">领料差异分析</h2>
      <span class="report-sub">实际净领料 vs 配方计划用量 · 持续定向偏差 = 配方用量与实际工艺不符的信号</span>
    </div>

    <div class="toolbar">
      <el-radio-group v-model="view">
        <el-radio-button value="product">按配方</el-radio-button>
        <el-radio-button value="material">按原料</el-radio-button>
        <el-radio-button value="detail">订单明细</el-radio-button>
      </el-radio-group>
      <el-select v-model="onlyWarn" style="width:150px">
        <el-option label="全部" value="" />
        <el-option label="仅偏差≥5%（配方预警）" value="warn" />
      </el-select>
      <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
    </div>

    <el-skeleton :rows="6" animated v-if="loading" />

    <template v-if="data && !loading">
      <div class="kpi-row" v-if="hasPerm('finance:amount')">
        <div class="kpi-card"><div class="kpi-label">统计配方数</div><div class="kpi-value">{{ (data.byProduct || []).length }}</div></div>
        <div class="kpi-card"><div class="kpi-label">偏差预警配方（≥5%）</div><div class="kpi-value red">{{ data.productWarnCount }}</div></div>
        <div class="kpi-card"><div class="kpi-label">预警原料（≥5%）</div><div class="kpi-value orange">{{ data.materialWarnCount }}</div></div>
        <div class="kpi-card"><div class="kpi-label">多领行 / 少领行</div>
          <div class="kpi-value" style="font-size:16px">{{ totalOver }} / {{ totalUnder }}</div></div>
      </div>

      <el-alert v-if="view === 'product' && data.productWarnCount > 0" type="warning" :closable="false" style="margin-bottom:12px"
        :title="`${data.productWarnCount} 个配方实际投料持续偏差≥5%——多领说明配方用量偏低（补损耗率），少领说明用量偏高（可降本），建议结合工艺复核修订配方`" />

      <!-- 按配方 -->
      <div class="table-card" v-if="view === 'product'">
        <table class="mv-table">
          <thead><tr>
            <th>产品（配方）</th><th class="num">订单数</th><th class="num">领料行</th>
            <th class="num">多领</th><th class="num">少领</th><th class="num">相符</th>
            <th class="num">加权差异率</th><th class="num" v-if="hasPerm('finance:amount')">差异金额</th><th>信号</th>
          </tr></thead>
          <tbody>
            <tr v-for="r in filteredProducts" :key="r.key">
              <td>{{ r.name }}</td>
              <td class="num">{{ r.orderCount }}</td>
              <td class="num">{{ r.lineCount }}</td>
              <td class="num red">{{ r.overCount || '' }}</td>
              <td class="num blue">{{ r.underCount || '' }}</td>
              <td class="num muted">{{ r.matchCount || '' }}</td>
              <td class="num" :class="rateClass(r.weightedRate)">{{ pct(r.weightedRate) }}</td>
              <td class="num" v-if="hasPerm('finance:amount')" :class="rateClass(r.weightedRate)">{{ fmt(r.diffAmount) }}</td>
              <td><span :class="signal(r.weightedRate)">{{ signalText(r.weightedRate) }}</span></td>
            </tr>
            <tr v-if="!filteredProducts.length"><td colspan="9" class="empty">暂无领料差异数据（需要有领料记录的生产订单）</td></tr>
          </tbody>
        </table>
      </div>

      <!-- 按原料 -->
      <div class="table-card" v-if="view === 'material'">
        <table class="mv-table">
          <thead><tr>
            <th>原料</th><th class="num">涉及订单</th><th class="num">多领次</th><th class="num">少领次</th>
            <th class="num">加权差异率</th><th class="num" v-if="hasPerm('finance:amount')">差异金额</th><th>信号</th>
          </tr></thead>
          <tbody>
            <tr v-for="r in data.byMaterial" :key="r.key">
              <td>{{ r.key }} {{ r.name }}</td>
              <td class="num">{{ r.orderCount }}</td>
              <td class="num red">{{ r.overCount || '' }}</td>
              <td class="num blue">{{ r.underCount || '' }}</td>
              <td class="num" :class="rateClass(r.weightedRate)">{{ pct(r.weightedRate) }}</td>
              <td class="num" v-if="hasPerm('finance:amount')" :class="rateClass(r.weightedRate)">{{ fmt(r.diffAmount) }}</td>
              <td><span :class="signal(r.weightedRate)">{{ signalText(r.weightedRate) }}</span></td>
            </tr>
            <tr v-if="!(data.byMaterial || []).length"><td colspan="7" class="empty">暂无数据</td></tr>
          </tbody>
        </table>
      </div>

      <!-- 明细 -->
      <div class="table-card" v-if="view === 'detail'">
        <table class="mv-table">
          <thead><tr>
            <th>订单</th><th>产品</th><th>物料</th>
            <th class="num">计划用量</th><th class="num">实际净领</th><th class="num">差异量</th><th>单位</th>
            <th class="num">差异率</th><th class="num" v-if="hasPerm('finance:amount')">差异金额</th><th>判定</th>
          </tr></thead>
          <tbody>
            <tr v-for="r in data.rows" :key="r.orderNo + r.materialCode">
              <td>{{ r.orderNo }}</td>
              <td>{{ r.productName }}</td>
              <td>{{ r.materialCode }} {{ r.materialName }}</td>
              <td class="num">{{ r.plannedQty }}</td>
              <td class="num">{{ r.actualQty }}</td>
              <td class="num" :class="rateClass(r.diffRate)">{{ r.diffQty }}</td>
              <td>{{ r.unit }}</td>
              <td class="num" :class="rateClass(r.diffRate)">{{ pct(r.diffRate) }}</td>
              <td class="num" v-if="hasPerm('finance:amount')" :class="rateClass(r.diffRate)">{{ fmt(r.diffAmount) }}</td>
              <td>
                <el-tag size="small" :type="r.flag === 'OVER' ? 'danger' : r.flag === 'UNDER' ? 'primary' : 'success'">
                  {{ r.flag === 'OVER' ? '多领' : r.flag === 'UNDER' ? '少领' : '相符' }}
                </el-tag>
              </td>
            </tr>
            <tr v-if="!(data.rows || []).length"><td colspan="10" class="empty">暂无数据</td></tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import api from '../api'
import { downloadFile } from '../utils/download'

const data = ref(null)
const perms = ref([])
const loading = ref(false)
const exporting = ref(false)
const view = ref('product')
const onlyWarn = ref('')

function hasPerm(c) { return perms.value.includes(c) }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function pct(v) { return (Number(v || 0) * 100).toFixed(1) + '%' }
function rateClass(v) {
  const n = Number(v || 0)
  if (Math.abs(n) >= 0.05) return n > 0 ? 'red' : 'blue'
  return ''
}
function signal(v) {
  const n = Number(v || 0)
  if (n >= 0.05) return 'sig-over'
  if (n <= -0.05) return 'sig-under'
  return 'sig-ok'
}
function signalText(v) {
  const n = Number(v || 0)
  if (n >= 0.05) return '持续多领·建议复核损耗率'
  if (n <= -0.05) return '持续少领·建议复核用量'
  return '正常'
}

const filteredProducts = computed(() => {
  const list = data.value?.byProduct || []
  return onlyWarn.value === 'warn'
    ? list.filter(r => Math.abs(Number(r.weightedRate || 0)) >= 0.05)
    : list
})
const totalOver = computed(() => (data.value?.rows || []).filter(r => r.flag === 'OVER').length)
const totalUnder = computed(() => (data.value?.rows || []).filter(r => r.flag === 'UNDER').length)

async function fetch() {
  loading.value = true
  try { data.value = await api.get('/report/material-variance') } catch {} finally { loading.value = false }
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/report/material-variance/export', {}, `领料差异分析-${new Date().toISOString().slice(0, 10)}.xlsx`)
  } catch {} finally { exporting.value = false }
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 16px; }
.kpi-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 14px 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.kpi-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.kpi-value { font-size: 20px; font-weight: 700; }
.kpi-value.red { color: #ef4444; }
.kpi-value.orange { color: #ea580c; }
.mv-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.mv-table th, .mv-table td { border: 1px solid #d5dbe3; padding: 5px 8px; }
.mv-table th { background: #f5f7fa; color: #606266; font-weight: 500; text-align: center; }
.mv-table .num { text-align: right; font-variant-numeric: tabular-nums; }
.mv-table .red { color: #dc2626; font-weight: 600; }
.mv-table .blue { color: #2563eb; font-weight: 600; }
.mv-table .muted { color: #94a3b8; }
.mv-table .empty { text-align: center; color: #94a3b8; padding: 18px; }
.sig-over { color: #dc2626; font-size: 11px; }
.sig-under { color: #2563eb; font-size: 11px; }
.sig-ok { color: #16a34a; font-size: 11px; }
</style>
