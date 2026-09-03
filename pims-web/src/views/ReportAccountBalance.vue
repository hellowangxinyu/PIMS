<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">科目余额表</h2>
      <span class="report-sub">总账口径 · 期初建账数 + 已记账凭证净发生额 · 结转损益凭证含在内</span>
    </div>

    <div class="toolbar">
      <el-date-picker v-model="period" type="month" value-format="YYYY-MM" :clearable="false" @change="fetch" style="width:140px" />
      <el-radio-group v-model="level" @change="fetch">
        <el-radio-button value="ALL">含明细</el-radio-button>
        <el-radio-button value="TOP">仅一级</el-radio-button>
      </el-radio-group>
      <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
      <el-button @click="doPrint">打印</el-button>
    </div>

    <el-skeleton :rows="8" animated v-if="loading" />

    <div class="table-card" v-if="!loading">
      <table class="bs-table" v-if="hasAmountPerm('finance-report')">
        <thead>
          <tr>
            <th rowspan="2" style="width:90px">编码</th><th rowspan="2" style="width:200px">科目名称</th>
            <th colspan="2">期初余额</th><th colspan="2">本期发生</th><th colspan="2">期末余额</th>
          </tr>
          <tr><th class="amt" style="width:110px">借方</th><th class="amt" style="width:110px">贷方</th>
            <th class="amt" style="width:110px">借方</th><th class="amt" style="width:110px">贷方</th>
            <th class="amt" style="width:110px">借方</th><th class="amt" style="width:110px">贷方</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.code" :class="{ 'row-top': r.top, 'row-detail': !r.top }">
            <td>{{ r.code }}</td>
            <td :style="{ paddingLeft: r.top ? '8px' : '24px', fontWeight: r.top ? 600 : 400 }">{{ r.name }}</td>
            <td class="amt">{{ nz(r.beginDr) }}</td><td class="amt">{{ nz(r.beginCr) }}</td>
            <td class="amt">{{ nz(r.debit) }}</td><td class="amt">{{ nz(r.credit) }}</td>
            <td class="amt">{{ nz(r.endDr) }}</td><td class="amt">{{ nz(r.endCr) }}</td>
          </tr>
          <tr class="strong">
            <td colspan="2">合计</td>
            <td class="amt">{{ fmt(total.beginDr) }}</td><td class="amt">{{ fmt(total.beginCr) }}</td>
            <td class="amt">{{ fmt(total.debit) }}</td><td class="amt">{{ fmt(total.credit) }}</td>
            <td class="amt">{{ fmt(total.endDr) }}</td><td class="amt">{{ fmt(total.endCr) }}</td>
          </tr>
        </tbody>
      </table>
      <div class="no-perm" v-else>金额明细需「查看金额」权限</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../api'
import { downloadFile } from '../utils/download'
import { printTableHtml, fmtAmt } from '../utils/reportPrint'

const rows = ref([])
const perms = ref([])
const loading = ref(false)
const exporting = ref(false)
const period = ref(new Date().toISOString().slice(0, 7))
const level = ref('ALL')

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function nz(v) { return Number(v || 0) !== 0 ? fmt(v) : '' }

const total = computed(() => {
  const t = { beginDr: 0, beginCr: 0, debit: 0, credit: 0, endDr: 0, endCr: 0 }
  for (const r of rows.value.filter(x => x.top)) {
    for (const k of Object.keys(t)) t[k] += Number(r[k] || 0)
  }
  return t
})

async function fetch() {
  loading.value = true
  try { rows.value = await api.get('/finance-report/account-balance', { params: { period: period.value, level: level.value } }) }
  catch {} finally { loading.value = false }
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/finance-report/account-balance/export', { period: period.value, level: level.value },
      `科目余额表-${period.value}.xlsx`)
  } catch {} finally { exporting.value = false }
}

function doPrint() {
  const body = rows.value.map(r => `<tr${r.top ? ' class="strong"' : ''}>
    <td>${r.code}</td><td${r.top ? '' : ' class="indent"'}>${r.name}</td>
    <td class="amt">${nz(r.beginDr)}</td><td class="amt">${nz(r.beginCr)}</td>
    <td class="amt">${nz(r.debit)}</td><td class="amt">${nz(r.credit)}</td>
    <td class="amt">${nz(r.endDr)}</td><td class="amt">${nz(r.endCr)}</td></tr>`).join('')
    + `<tr class="strong"><td colspan="2">合计</td>
    <td class="amt">${fmtAmt(total.value.beginDr)}</td><td class="amt">${fmtAmt(total.value.beginCr)}</td>
    <td class="amt">${fmtAmt(total.value.debit)}</td><td class="amt">${fmtAmt(total.value.credit)}</td>
    <td class="amt">${fmtAmt(total.value.endDr)}</td><td class="amt">${fmtAmt(total.value.endCr)}</td></tr>`
  printTableHtml('科目余额表', `${period.value} · ${level.value === 'TOP' ? '一级科目' : '含明细'}`, `
    <table><thead><tr><th rowspan="2">编码</th><th rowspan="2">科目名称</th>
    <th colspan="2">期初余额</th><th colspan="2">本期发生</th><th colspan="2">期末余额</th></tr>
    <tr><th class="amt">借方</th><th class="amt">贷方</th><th class="amt">借方</th><th class="amt">贷方</th><th class="amt">借方</th><th class="amt">贷方</th></tr>
    </thead><tbody>${body}</tbody></table>`)
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
.bs-table .row-detail td { color: #64748b; }
.bs-table .strong td { font-weight: 700; background: #fafbfc; }
</style>
