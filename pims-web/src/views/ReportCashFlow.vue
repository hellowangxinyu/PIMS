<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">现金流量表</h2>
      <span class="report-sub">简化直接法 · 现金类科目（库存现金/银行存款/其他货币资金）按对方科目归集 · 红字为冲减</span>
    </div>

    <div class="toolbar">
      <el-date-picker v-model="period" type="month" value-format="YYYY-MM" :clearable="false" @change="fetch" style="width:140px" />
      <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
      <el-button @click="doPrint">打印</el-button>
    </div>

    <el-skeleton :rows="8" animated v-if="loading" />

    <div class="table-card" v-if="data && !loading" style="max-width:860px">
      <div class="card-title">{{ period }}（单位：元）</div>
      <table class="bs-table" v-if="hasAmountPerm('finance-report')">
        <thead><tr>
          <th style="width:70px">类别</th><th>项目</th>
          <th class="amt" style="width:140px">本月金额</th><th class="amt" style="width:140px">本年累计</th>
        </tr></thead>
        <tbody>
          <template v-for="(r, i) in data.rows" :key="i">
            <tr :class="{ strong: r.code === 'SUB' || r.code === 'NET' }">
              <td>{{ r.segment }}</td>
              <td :style="{ paddingLeft: r.code === 'SUB' || r.code === 'NET' ? '8px' : '26px' }">{{ r.item }}</td>
              <td class="amt">{{ nz(r.month) }}</td>
              <td class="amt">{{ nz(r.yearCum) }}</td>
            </tr>
          </template>
          <tr v-if="!data.rows.length"><td colspan="4" style="text-align:center;color:#94a3b8">该期间无现金收支记录（或期初建账后尚未录凭证）</td></tr>
        </tbody>
      </table>
      <div class="no-perm" v-else>金额明细需「查看金额」权限</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'
import { downloadFile } from '../utils/download'
import { printTableHtml, fmtAmt } from '../utils/reportPrint'

const data = ref(null)
const perms = ref([])
const loading = ref(false)
const exporting = ref(false)
const period = ref(new Date().toISOString().slice(0, 7))

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
function nz(v) { return Number(v || 0) !== 0 ? fmtAmt(v) : '' }

async function fetch() {
  loading.value = true
  try { data.value = await api.get('/finance-report/cash-flow', { params: { period: period.value } }) }
  catch {} finally { loading.value = false }
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/finance-report/cash-flow/export', { period: period.value }, `现金流量表-${period.value}.xlsx`)
  } catch {} finally { exporting.value = false }
}

function doPrint() {
  if (!data.value) return
  const rows = data.value.rows.map(r => {
    const strong = r.code === 'SUB' || r.code === 'NET'
    return `<tr${strong ? ' class="strong"' : ''}><td>${r.segment}</td>
      <td${strong ? '' : ' class="indent"'}>${r.item}</td>
      <td class="amt">${fmtAmt(r.month)}</td><td class="amt">${fmtAmt(r.yearCum)}</td></tr>`
  }).join('')
  printTableHtml('现金流量表', `${period.value} · 单位：元`,
    `<table><thead><tr><th style="width:70px">类别</th><th>项目</th>
    <th class="amt" style="width:130px">本月金额</th><th class="amt" style="width:130px">本年累计</th></tr></thead><tbody>${rows}</tbody></table>`)
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.bs-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.bs-table th, .bs-table td { border: 1px solid #d5dbe3; padding: 5px 8px; }
.bs-table th { background: #f5f7fa; color: #606266; font-weight: 500; text-align: center; }
.bs-table .amt { text-align: right; font-variant-numeric: tabular-nums; }
.bs-table .strong td { font-weight: 700; background: #fafbfc; }
</style>
