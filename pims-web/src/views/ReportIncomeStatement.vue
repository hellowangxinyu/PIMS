<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">利润表</h2>
      <span class="report-sub">小企业会计准则 · 会计口径（与「利润试算」管理口径并存）· 已排除结转损益凭证</span>
    </div>

    <div class="toolbar">
      <el-date-picker v-model="period" type="month" value-format="YYYY-MM" :clearable="false" @change="fetch" style="width:140px" />
      <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
      <el-button @click="doPrint">打印</el-button>
    </div>

    <el-skeleton :rows="8" animated v-if="loading" />

    <div class="table-card" v-if="data && !loading" style="max-width:760px">
      <div class="card-title">{{ period }}（单位：元）</div>
      <table class="bs-table" v-if="hasAmountPerm('finance-report')">
        <thead><tr><th>项目</th><th class="amt" style="width:150px">本月金额</th><th class="amt" style="width:150px">本年累计</th></tr></thead>
        <tbody>
          <tr v-for="(r, i) in data.rows" :key="i" :class="{ strong: /^[一二三四]/.test(r.item) }">
            <td :style="{ paddingLeft: /^[一二三四]/.test(r.item) ? '8px' : '26px' }">{{ r.item }}</td>
            <td class="amt">{{ nz(r.month) }}</td>
            <td class="amt">{{ nz(r.yearCum) }}</td>
          </tr>
        </tbody>
      </table>
      <div class="no-perm" v-else>金额明细需「查看金额」权限</div>
    </div>
  </div>
</template>

<script setup>
import { monthLocal } from '../utils/date'
import { ref, onMounted } from 'vue'
import api from '../api'
import { downloadFile } from '../utils/download'
import { printTableHtml, fmtAmt, esc } from '../utils/reportPrint'

const data = ref(null)
const perms = ref([])
const loading = ref(false)
const exporting = ref(false)
const period = ref(monthLocal())

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
function nz(v) { return Number(v || 0) !== 0 ? fmtAmt(v) : '' }

async function fetch() {
  loading.value = true
  try { data.value = await api.get('/finance-report/income-statement', { params: { period: period.value } }) }
  catch {} finally { loading.value = false }
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/finance-report/income-statement/export', { period: period.value }, `利润表-${period.value}.xlsx`)
  } catch {} finally { exporting.value = false }
}

function doPrint() {
  if (!data.value) return
  const rows = data.value.rows.map(r => `<tr${/^[一二三四]/.test(r.item) ? ' class="strong"' : ''}>
    <td${/^[一二三四]/.test(r.item) ? '' : ' class="indent"'}>${esc(r.item)}</td>
    <td class="amt">${fmtAmt(r.month)}</td><td class="amt">${fmtAmt(r.yearCum)}</td></tr>`).join('')
  printTableHtml('利润表', `${period.value} · 单位：元`,
    `<table><thead><tr><th>项目</th><th class="amt" style="width:140px">本月金额</th><th class="amt" style="width:140px">本年累计</th></tr></thead><tbody>${rows}</tbody></table>`)
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
