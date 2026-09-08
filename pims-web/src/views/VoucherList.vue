<template>
  <div class="page-container">
    <div class="page-header">
      <h2>会计凭证</h2>
      <div class="header-actions">
        <el-date-picker v-model="filterMonth" type="month" value-format="YYYY-MM" placeholder="全部期间" clearable style="width:130px" />
        <el-select v-model="filterStatus" clearable placeholder="状态" style="width:100px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已记账" value="POSTED" />
        </el-select>
        <el-input v-model="keyword" placeholder="凭证号/摘要" clearable style="width:160px" />
        <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
        <el-button type="primary" @click="openDialog(null)" v-if="hasPerm('finance:write')">新增凭证</el-button>
      </div>
    </div>

    <div class="summary-cards" v-if="hasAmountPerm('voucher')">
      <div class="summary-card"><div class="sc-label">凭证数</div><div class="sc-value">{{ filtered.length }}</div></div>
      <div class="summary-card"><div class="sc-label">其中草稿</div><div class="sc-value orange">{{ draftCount }}</div></div>
      <div class="summary-card"><div class="sc-label">借方合计</div><div class="sc-value">¥{{ fmt(sumDebitOfList) }}</div></div>
      <div class="summary-card"><div class="sc-label">贷方合计</div><div class="sc-value">¥{{ fmt(sumCreditOfList) }}</div></div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ filtered.length }} 张凭证（点击行首展开分录）</span></div>
      <p-table :data="pagedRows" stripe border style="width:100%" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="entry-expand">
              <table class="entry-table">
                <thead><tr><th style="width:36%">摘要</th><th style="width:28%">会计科目</th><th style="width:10%">辅助</th><th style="width:13%">借方</th><th style="width:13%">贷方</th></tr></thead>
                <tbody>
                  <tr v-for="e in row.entries" :key="e.id">
                    <td>{{ e.digest }}</td>
                    <td>{{ e.subjectCode }} {{ e.subjectName }}</td>
                    <td>{{ e.auxName || '' }}</td>
                    <td class="amt">{{ e.debit > 0 ? fmt(e.debit) : '' }}{{ e.debit < 0 ? fmt(e.debit) : '' }}</td>
                    <td class="amt">{{ e.credit > 0 ? fmt(e.credit) : '' }}{{ e.credit < 0 ? fmt(e.credit) : '' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="docNo" label="凭证号" width="120" show-overflow-tooltip />
        <el-table-column prop="voucherDate" label="日期" width="105" />
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'POSTED' ? 'success' : 'info'" size="small">{{ row.status === 'POSTED' ? '已记账' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.source !== 'MANUAL'" size="small" effect="plain">{{ sourceLabel(row.source) }}</el-tag>
            <span v-else>手工</span>
          </template>
        </el-table-column>
        <el-table-column prop="refDocNo" label="来源单号" width="115" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="制单人" width="85" />
        <el-table-column prop="postedBy" label="记账人" width="85" />
        <el-table-column prop="totalDebit" label="合计金额" width="120" align="right" v-if="hasAmountPerm('voucher')">
          <template #default="{ row }">¥{{ fmt(row.totalDebit) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" v-if="hasPerm('finance:audit') && row.status === 'DRAFT'" @click="post(row)">记账</button>
            <button class="op-btn" v-if="hasPerm('finance:reverse-audit') && row.status === 'POSTED'" @click="unpost(row)">反记账</button>
            <button class="op-btn" @click="print(row)">打印</button>
            <button class="op-btn op-btn-primary" v-if="hasPerm('finance:write') && row.status === 'DRAFT'" @click="openDialog(row)">编辑</button>
            <button class="op-btn op-btn-danger" v-if="hasPerm('finance:write') && row.status === 'DRAFT'" @click="del(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]" :total="filtered.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <!-- 新增/编辑凭证 -->
    <el-dialog :title="editing ? '编辑凭证 ' + editing.docNo : '新增凭证'" v-model="visible" width="min(980px, 96vw)"
      destroy-on-close :close-on-click-modal="false">
      <div class="form-head">
        <div class="fh-item"><span class="fh-label">日期</span>
          <el-date-picker v-model="form.voucherDate" type="date" value-format="YYYY-MM-DD" style="width:150px" /></div>
        <div class="fh-item"><span class="fh-label">附单据</span>
          <el-input-number v-model="form.attachmentCount" :min="0" :max="999" style="width:100px" /></div>
        <div class="fh-item" style="flex:1"><span class="fh-label">备注</span>
          <el-input v-model="form.remark" placeholder="可选" /></div>
        <el-tag v-if="form.source !== 'MANUAL'" type="warning" size="small">
          {{ sourceLabel(form.source) }} {{ form.refDocNo }} 生成
        </el-tag>
      </div>

      <el-table :data="form.entries" border size="small" style="width:100%">
        <el-table-column label="#" width="42" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="摘要" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.digest" placeholder="摘要" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="会计科目" min-width="220">
          <template #default="{ row }">
            <el-select v-model="row.subjectCode" filterable placeholder="编码/名称搜索" size="small" style="width:100%">
              <el-option v-for="s in enabledSubjects" :key="s.code" :label="s.code + ' ' + s.name" :value="s.code" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="辅助核算" width="120">
          <template #default="{ row }">
            <el-input v-model="row.auxName" placeholder="客户/供应商" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="借方" width="140" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.debit" :precision="2" :controls="false" placeholder="0.00" size="small" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="贷方" width="140" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.credit" :precision="2" :controls="false" placeholder="0.00" size="small" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="" width="50" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeLine($index)" :disabled="form.entries.length <= 2">✕</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="entry-footer">
        <el-button size="small" @click="addLine">+ 增加一行</el-button>
        <div class="total-bar">
          <span>借方合计：<b :class="{ red: !balanced }">¥{{ fmt(formDebit) }}</b></span>
          <span>贷方合计：<b :class="{ red: !balanced }">¥{{ fmt(formCredit) }}</b></span>
          <el-tag v-if="balanced" type="success" size="small">借贷平衡</el-tag>
          <el-tag v-else type="danger" size="small">差额 ¥{{ fmt(Math.abs(formDebit - formCredit)) }}</el-tag>
        </div>
      </div>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading" :disabled="!balanced || formDebit === 0">保存为草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { todayLocal } from '../utils/date'
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'
import { downloadFile } from '../utils/download'
import { printVoucher } from '../utils/voucherPrint'

const list = ref([])
const subjects = ref([])
const perms = ref([])
const exporting = ref(false)
const visible = ref(false)
const loading = ref(false)
const editing = ref(null)
const filterMonth = ref('')
const filterStatus = ref('')
const keyword = ref('')
const form = ref(emptyForm())

function emptyForm() {
  return {
    voucherDate: todayLocal(), attachmentCount: 0, remark: '',
    source: 'MANUAL', refDocNo: null, createdBy: '',
    entries: [emptyLine(), emptyLine()]
  }
}
function emptyLine() { return { subjectCode: '', digest: '', debit: null, credit: null, auxType: null, auxName: '' } }

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function sourceLabel(s) {
  return { RECEIPT: '收款单', PAYMENT: '付款单', EXPENSE: '费用单', INVOICE: '发票', TRANSFER: '结转损益',
    SALARY_ACCRUAL: '工资计提', SALARY_PAY: '工资发放', DEPRECIATION: '折旧计提', MANUAL: '手工' }[s] || s
}

const enabledSubjects = computed(() => subjects.value.filter(s => s.status === 'ENABLED'))

const filtered = computed(() => {
  let arr = list.value
  if (filterMonth.value) arr = arr.filter(v => v.period === filterMonth.value)
  if (filterStatus.value) arr = arr.filter(v => v.status === filterStatus.value)
  if (keyword.value) {
    const kw = keyword.value.trim().toLowerCase()
    arr = arr.filter(v => v.docNo.toLowerCase().includes(kw)
      || (v.refDocNo || '').toLowerCase().includes(kw)
      || (v.entries || []).some(e => (e.digest || '').toLowerCase().includes(kw)))
  }
  return arr
})
const draftCount = computed(() => filtered.value.filter(v => v.status === 'DRAFT').length)
const sumDebitOfList = computed(() => filtered.value.reduce((s, v) => s + Number(v.totalDebit || 0), 0))
const sumCreditOfList = computed(() => filtered.value.reduce((s, v) => s + Number(v.totalCredit || 0), 0))

const formDebit = computed(() => form.value.entries.reduce((s, e) => s + Number(e.debit || 0), 0))
const formCredit = computed(() => form.value.entries.reduce((s, e) => s + Number(e.credit || 0), 0))
const balanced = computed(() => Math.abs(formDebit.value - formCredit.value) < 0.005 && formDebit.value !== 0)

async function fetch() {
  try { list.value = await api.get('/voucher') } catch {}
  try { subjects.value = await api.get('/account-subject') } catch {}
}

function addLine() { form.value.entries.push(emptyLine()) }
function removeLine(i) { form.value.entries.splice(i, 1) }

function openDialog(row) {
  editing.value = row
  if (row) {
    form.value = {
      voucherDate: row.voucherDate, attachmentCount: row.attachmentCount || 0, remark: row.remark || '',
      source: row.source, refDocNo: row.refDocNo, createdBy: row.createdBy,
      entries: (row.entries || []).map(e => ({
        subjectCode: e.subjectCode, digest: e.digest || '', debit: Number(e.debit) || null, credit: Number(e.credit) || null,
        auxType: e.auxType, auxName: e.auxName || ''
      }))
    }
  } else {
    form.value = emptyForm()
  }
  visible.value = true
}

async function submit() {
  if (!form.value.voucherDate) { ElMessage.warning('请选择凭证日期'); return }
  for (let i = 0; i < form.value.entries.length; i++) {
    const e = form.value.entries[i]
    if (!e.subjectCode) { ElMessage.warning(`第 ${i + 1} 行未选择科目`); return }
    if (!Number(e.debit || 0) && !Number(e.credit || 0)) { ElMessage.warning(`第 ${i + 1} 行金额不能为零`); return }
  }
  if (!balanced.value) { ElMessage.warning('借贷不平衡，无法保存'); return }
  loading.value = true
  try {
    const payload = { ...form.value, entries: form.value.entries.map(e => ({ ...e, auxType: e.auxName ? (e.auxType || 'CUSTOMER') : null })) }
    if (editing.value) await api.put(`/voucher/${editing.value.id}`, payload)
    else await api.post('/voucher', payload)
    ElMessage.success('已保存为草稿，记账后进入账簿')
    visible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

async function post(row) {
  try { await ElMessageBox.confirm(`确定记账 ${row.docNo}？记账后凭证进入账簿报表，不可再修改`, '记账', { type: 'warning' }) } catch { return }
  try {
    await api.put(`/voucher/${row.id}/post`)
    ElMessage.success('记账成功')
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '记账失败') }
}

async function unpost(row) {
  try { await ElMessageBox.confirm(`确定反记账 ${row.docNo}？凭证将退回草稿，从账簿报表中移除`, '反记账', { type: 'warning' }) } catch { return }
  try {
    await api.put(`/voucher/${row.id}/unpost`)
    ElMessage.success('已反记账')
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '反记账失败') }
}

async function del(row) {
  try { await ElMessageBox.confirm(`确定删除凭证 ${row.docNo}？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await api.delete(`/voucher/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败') }
}

function print(row) { printVoucher(row) }

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/voucher/export', {}, `记账凭证-${todayLocal()}.xlsx`)
  } catch {} finally { exporting.value = false }
}

const { page, pageSize, pagedRows, resetPage } = usePaging(filtered)

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.sc-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.sc-value { font-size: 20px; font-weight: 700; }
.sc-value.orange { color: #ea580c; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
.entry-expand { padding: 4px 24px 8px; background: #fafbfc; }
.entry-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.entry-table th, .entry-table td { border: 1px solid #e4e7ed; padding: 5px 10px; }
.entry-table th { background: #f5f7fa; color: #606266; font-weight: 500; }
.entry-table .amt { text-align: right; font-variant-numeric: tabular-nums; }
.form-head { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; flex-wrap: wrap; }
.fh-item { display: flex; align-items: center; gap: 6px; }
.fh-label { font-size: 13px; color: #606266; white-space: nowrap; }
.entry-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }
.total-bar { display: flex; align-items: center; gap: 18px; font-size: 13px; color: #303133; }
.total-bar .red { color: #ef4444; }
</style>
