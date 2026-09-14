<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">资产负债表</h2>
      <span class="report-sub">小企业会计准则 · 期末数含未结转损益（体现在未分配利润）</span>
    </div>

    <div class="toolbar">
      <el-date-picker v-model="period" type="month" value-format="YYYY-MM" :clearable="false" @change="fetch" style="width:140px" />
      <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
      <el-button @click="doPrint">打印</el-button>
    </div>

    <el-skeleton :rows="8" animated v-if="loading" />

    <template v-if="data && !loading">
      <el-alert v-if="hasAmountPerm('finance-report') && Number(data.diff) !== 0" type="warning" :closable="false" style="margin-bottom:12px"
        :title="`资产与负债+权益差额 ¥${fmt(data.diff)}（借方余额为负的负债科目或未结转损益所致，请核对凭证/期初建账）`" />

      <div class="grid-2col" v-if="hasAmountPerm('finance-report')">
        <div class="table-card">
          <div class="card-title">资产</div>
          <table class="bs-table">
            <thead><tr><th>项目</th><th class="amt">年初余额</th><th class="amt">期末余额</th></tr></thead>
            <tbody>
              <tr v-for="r in data.assets" :key="r.item"><td>{{ r.item }}</td>
                <td class="amt">{{ nz(r.yearBegin) }}</td><td class="amt">{{ nz(r.periodEnd) }}</td></tr>
              <tr class="strong"><td>资产总计</td>
                <td class="amt">{{ fmt(data.assetBeginTotal) }}</td><td class="amt">{{ fmt(data.assetTotal) }}</td></tr>
            </tbody>
          </table>
        </div>
        <div class="stack-col">
          <div class="table-card">
            <div class="card-title">负债</div>
            <table class="bs-table">
              <thead><tr><th>项目</th><th class="amt">年初余额</th><th class="amt">期末余额</th></tr></thead>
              <tbody>
                <tr v-for="r in data.liabilities" :key="r.item"><td>{{ r.item }}</td>
                  <td class="amt">{{ nz(r.yearBegin) }}</td><td class="amt">{{ nz(r.periodEnd) }}</td></tr>
                <tr class="strong"><td>负债合计</td>
                  <td class="amt">{{ fmt(data.liabBeginTotal) }}</td><td class="amt">{{ fmt(data.liabTotal) }}</td></tr>
              </tbody>
            </table>
          </div>
          <div class="table-card">
            <div class="card-title">所有者权益</div>
            <table class="bs-table">
              <thead><tr><th>项目</th><th class="amt">年初余额</th><th class="amt">期末余额</th></tr></thead>
              <tbody>
                <tr v-for="r in data.equity" :key="r.item"><td>{{ r.item }}</td>
                  <td class="amt">{{ nz(r.yearBegin) }}</td><td class="amt">{{ nz(r.periodEnd) }}</td></tr>
                <tr class="strong"><td>所有者权益合计</td>
                  <td class="amt">{{ fmt(data.eqBeginTotal) }}</td><td class="amt">{{ fmt(data.eqTotal) }}</td></tr>
                <tr class="strong"><td>负债和所有者权益总计</td>
                  <td class="amt">{{ fmt(Number(data.liabBeginTotal) + Number(data.eqBeginTotal)) }}</td>
                  <td class="amt">{{ fmt(Number(data.liabTotal) + Number(data.eqTotal)) }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <div class="no-perm" v-else>金额明细需「查看金额」权限</div>
    </template>
  </div>
</template>

<script setup>
import { monthLocal } from '../utils/date'
import { fmt } from '../utils/fmt'
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
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function nz(v) { return Number(v || 0) !== 0 ? fmt(v) : '' }

async function fetch() {
  loading.value = true
  try { data.value = await api.get('/finance-report/balance-sheet', { params: { period: period.value } }) }
  catch {} finally { loading.value = false }
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/finance-report/balance-sheet/export', { period: period.value }, `资产负债表-${period.value}.xlsx`)
  } catch {} finally { exporting.value = false }
}

function doPrint() {
  if (!data.value) return
  const d = data.value
  const row = r => `<tr><td>${esc(r.item)}</td><td class="amt">${fmtAmt(r.yearBegin)}</td><td class="amt">${fmtAmt(r.periodEnd)}</td></tr>`
  const maxLen = Math.max(d.assets.length, d.liabilities.length + d.equity.length + 4)
  const left = [...d.assets.map(row), `<tr class="strong"><td>资产总计</td><td class="amt">${fmtAmt(d.assetBeginTotal)}</td><td class="amt">${fmtAmt(d.assetTotal)}</td></tr>`]
  const right = [...d.liabilities.map(row),
    `<tr class="strong"><td>负债合计</td><td class="amt">${fmtAmt(d.liabBeginTotal)}</td><td class="amt">${fmtAmt(d.liabTotal)}</td></tr>`,
    ...d.equity.map(row),
    `<tr class="strong"><td>所有者权益合计</td><td class="amt">${fmtAmt(d.eqBeginTotal)}</td><td class="amt">${fmtAmt(d.eqTotal)}</td></tr>`,
    `<tr class="strong"><td>负债和权益总计</td><td class="amt">${fmtAmt(Number(d.liabBeginTotal) + Number(d.eqBeginTotal))}</td><td class="amt">${fmtAmt(Number(d.liabTotal) + Number(d.eqTotal))}</td></tr>`]
  while (left.length < maxLen) left.push('<tr><td colspan="3"></td></tr>')
  while (right.length < maxLen) right.push('<tr><td colspan="3"></td></tr>')
  const rows = left.map((l, i) => `<tr>${l.replace(/^<tr>|<\/tr>$/g, '')}${right[i].replace(/^<tr>|<\/tr>$/g, '')}</tr>`).join('')
  printTableHtml('资产负债表', `${period.value} · 单位：元`,
    `<table><thead><tr>
      <th style="width:22%">资产</th><th class="amt">年初余额</th><th class="amt">期末余额</th>
      <th style="width:22%">负债和所有者权益</th><th class="amt">年初余额</th><th class="amt">期末余额</th>
    </tr></thead><tbody>${rows}</tbody></table>`)
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.grid-2col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; align-items: start; }
.stack-col { display: flex; flex-direction: column; gap: 16px; }
@media (max-width: 1000px) { .grid-2col { grid-template-columns: 1fr; } }
.bs-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.bs-table th, .bs-table td { border: 1px solid #d5dbe3; padding: 5px 8px; }
.bs-table th { background: #f5f7fa; color: #606266; font-weight: 500; text-align: center; }
.bs-table .amt { text-align: right; font-variant-numeric: tabular-nums; }
.bs-table .strong td { font-weight: 700; background: #fafbfc; }
</style>
