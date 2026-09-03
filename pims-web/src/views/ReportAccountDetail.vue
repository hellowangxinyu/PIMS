<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">明细账</h2>
      <span class="report-sub">科目期间流水 · 仅已记账凭证 · 余额为正在借方、为负在贷方</span>
    </div>

    <div class="toolbar">
      <el-select v-model="subjectCode" filterable placeholder="选择科目" style="width:240px" @change="fetch">
        <el-option v-for="s in subjects" :key="s.code" :label="s.code + ' ' + s.name" :value="s.code" />
      </el-select>
      <el-date-picker v-model="from" type="month" value-format="YYYY-MM" :clearable="false" @change="fetch" style="width:130px" />
      <span style="color:#94a3b8">至</span>
      <el-date-picker v-model="to" type="month" value-format="YYYY-MM" :clearable="false" @change="fetch" style="width:130px" />
      <el-input v-model="auxName" placeholder="辅助核算筛选（客户/供应商）" clearable style="width:200px" @change="fetch" />
      <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
      <el-button @click="doPrint">打印</el-button>
    </div>

    <el-skeleton :rows="8" animated v-if="loading" />

    <template v-if="data && !loading">
      <div class="kpi-row" v-if="hasAmountPerm('finance-report')">
        <div class="kpi-card"><div class="kpi-label">期初余额</div>
          <div class="kpi-value">{{ data.beginDr > 0 ? '借 ' : '贷 ' }}¥{{ fmt(data.beginDr > 0 ? data.beginDr : data.beginCr) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">本期借方合计</div><div class="kpi-value">¥{{ fmt(data.sumDebit) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">本期贷方合计</div><div class="kpi-value">¥{{ fmt(data.sumCredit) }}</div></div>
        <div class="kpi-card"><div class="kpi-label">期末余额</div>
          <div class="kpi-value">{{ data.endDr > 0 ? '借 ' : '贷 ' }}¥{{ fmt(data.endDr > 0 ? data.endDr : data.endCr) }}</div></div>
      </div>

      <div class="table-card">
        <div class="card-title">{{ data.subject?.code }} {{ data.subject?.name }}（{{ data.from }} ~ {{ data.to }}）</div>
        <table class="bs-table" v-if="hasAmountPerm('finance-report')">
          <thead><tr>
            <th style="width:100px">日期</th><th style="width:120px">凭证号</th><th>摘要</th>
            <th style="width:120px">辅助</th><th style="width:120px" class="amt">借方</th>
            <th style="width:120px" class="amt">贷方</th><th style="width:140px" class="amt">余额（借/贷）</th>
          </tr></thead>
          <tbody>
            <tr><td colspan="4">期初余额</td><td class="amt"></td><td class="amt"></td>
              <td class="amt">{{ data.beginDr > 0 ? '借 ' + fmt(data.beginDr) : '贷 ' + fmt(data.beginCr) }}</td></tr>
            <tr v-for="(l, i) in data.lines" :key="i">
              <td>{{ l.voucherDate }}</td><td>{{ l.docNo }}</td><td>{{ l.digest }}</td><td>{{ l.auxName }}</td>
              <td class="amt">{{ Number(l.debit) !== 0 ? fmt(l.debit) : '' }}</td>
              <td class="amt">{{ Number(l.credit) !== 0 ? fmt(l.credit) : '' }}</td>
              <td class="amt">{{ Number(l.balanceDr) > 0 ? '借 ' + fmt(l.balanceDr) : '贷 ' + fmt(l.balanceCr) }}</td>
            </tr>
            <tr v-if="!data.lines.length"><td colspan="7" style="text-align:center;color:#94a3b8">该科目在此期间无已记账流水</td></tr>
            <tr class="strong"><td colspan="4">本期合计</td>
              <td class="amt">{{ fmt(data.sumDebit) }}</td><td class="amt">{{ fmt(data.sumCredit) }}</td>
              <td class="amt">{{ data.endDr > 0 ? '借 ' + fmt(data.endDr) : '贷 ' + fmt(data.endCr) }}</td></tr>
          </tbody>
        </table>
        <div class="no-perm" v-else>金额明细需「查看金额」权限</div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, onMounted } from 'vue'
import api from '../api'
import { downloadFile } from '../utils/download'
import { printTableHtml, fmtAmt } from '../utils/reportPrint'

const subjects = ref([])
const data = ref(null)
const perms = ref([])
const loading = ref(false)
const exporting = ref(false)
const nowMonth = new Date().toISOString().slice(0, 7)
const subjectCode = ref('1002')
const from = ref(nowMonth)
const to = ref(nowMonth)
const auxName = ref('')

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
async function fetch() {
  if (!subjectCode.value) return
  loading.value = true
  try {
    data.value = await api.get('/finance-report/account-detail',
      { params: { subjectCode: subjectCode.value, from: from.value, to: to.value, auxName: auxName.value || undefined } })
  } catch {} finally { loading.value = false }
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/finance-report/account-detail/export',
      { subjectCode: subjectCode.value, from: from.value, to: to.value, auxName: auxName.value || undefined },
      `明细账-${subjectCode.value}-${from.value}.xlsx`)
  } catch {} finally { exporting.value = false }
}

function doPrint() {
  if (!data.value) return
  const d = data.value
  const rows = d.lines.map(l => `<tr><td>${l.voucherDate || ''}</td><td>${l.docNo}</td><td>${l.digest || ''}</td>
    <td class="amt">${Number(l.debit) ? fmtAmt(l.debit) : ''}</td><td class="amt">${Number(l.credit) ? fmtAmt(l.credit) : ''}</td>
    <td class="amt">${Number(l.balanceDr) > 0 ? '借 ' + fmtAmt(l.balanceDr) : '贷 ' + fmtAmt(l.balanceCr)}</td></tr>`).join('')
  printTableHtml('明细账', `${d.subject.code} ${d.subject.name} · ${d.from} ~ ${d.to}`, `
    <table><thead><tr><th style="width:100px">日期</th><th style="width:110px">凭证号</th><th>摘要</th>
    <th class="amt">借方</th><th class="amt">贷方</th><th class="amt">余额</th></tr></thead>
    <tbody>
      <tr><td colspan="3">期初余额</td><td></td><td></td>
      <td class="amt">${d.beginDr > 0 ? '借 ' + fmtAmt(d.beginDr) : '贷 ' + fmtAmt(d.beginCr)}</td></tr>
      ${rows}
      <tr class="strong"><td colspan="3">本期合计</td>
      <td class="amt">${fmtAmt(d.sumDebit)}</td><td class="amt">${fmtAmt(d.sumCredit)}</td>
      <td class="amt">${d.endDr > 0 ? '借 ' + fmtAmt(d.endDr) : '贷 ' + fmtAmt(d.endCr)}</td></tr>
    </tbody></table>`)
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  try {
    const list = await api.get('/account-subject')
    subjects.value = list.filter(s => s.status === 'ENABLED')
    if (!subjects.value.find(s => s.code === subjectCode.value) && subjects.value.length) {
      subjectCode.value = subjects.value[0].code
    }
  } catch {}
  fetch()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin-bottom: 16px; }
.kpi-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 14px 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.kpi-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.kpi-value { font-size: 17px; font-weight: 700; }
.bs-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.bs-table th, .bs-table td { border: 1px solid #d5dbe3; padding: 5px 8px; }
.bs-table th { background: #f5f7fa; color: #606266; font-weight: 500; text-align: center; }
.bs-table .amt { text-align: right; font-variant-numeric: tabular-nums; }
.bs-table .strong td { font-weight: 700; background: #fafbfc; }
</style>
