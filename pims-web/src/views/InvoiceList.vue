<template>
  <div class="page-container">
    <div class="page-header">
      <h2>发票管理</h2>
      <div class="header-actions">
        <el-radio-group v-model="filterDir" size="default">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="OUTPUT">销项</el-radio-button>
          <el-radio-button value="INPUT">进项</el-radio-button>
        </el-radio-group>
        <el-input v-model="keyword" placeholder="搜索单号/发票号/单位" clearable style="width:200px" />
        <el-button @click="showSummary" v-if="hasAmountPerm('invoice')">开票汇总</el-button>
        <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
        <el-button type="primary" @click="openDialog(null)" v-if="hasPerm('finance:write')">登记发票</el-button>
      </div>
    </div>

    <div class="summary-cards" v-if="hasAmountPerm('invoice')">
      <div class="summary-card"><div class="sc-label">销项开票净额（价税合计）</div><div class="sc-value">¥{{ fmt(totalOutput) }}</div></div>
      <div class="summary-card"><div class="sc-label">进项发票净额（价税合计）</div><div class="sc-value">¥{{ fmt(totalInput) }}</div></div>
      <div class="summary-card"><div class="sc-label">销项税额净额</div><div class="sc-value">¥{{ fmt(totalTax) }}</div></div>
      <div class="summary-card"><div class="sc-label">已红冲笔数</div><div class="sc-value">{{ flushCount }}</div></div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ filtered.length }} 条记录　<span class="dim">红冲单为负数对冲记录，净额口径自动抵消</span></span></div>
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="系统单号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="invoiceNo" label="发票号码" min-width="130" show-overflow-tooltip>
          <template #default="{ row }"><span v-if="row.invoiceNo">{{ row.invoiceNo }}</span><span v-else style="color:#94a3b8">—</span></template>
        </el-table-column>
        <el-table-column label="方向" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'OUTPUT' ? 'primary' : 'success'" size="small">{{ row.direction === 'OUTPUT' ? '销项' : '进项' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="partnerName" label="往来单位" min-width="160" show-overflow-tooltip />
        <el-table-column prop="partnerTaxNo" label="税号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="amount" label="不含税金额" width="120" align="right" v-if="hasAmountPerm('invoice')">
          <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="taxRate" label="税率" width="70" align="center">
          <template #default="{ row }">{{ row.taxRate }}%</template>
        </el-table-column>
        <el-table-column prop="taxAmount" label="税额" width="110" align="right" v-if="hasAmountPerm('invoice')">
          <template #default="{ row }">¥{{ fmt(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="价税合计" width="120" align="right" v-if="hasAmountPerm('invoice')">
          <template #default="{ row }">
            <span :style="Number(row.totalAmount) < 0 ? 'color:#ef4444' : ''">¥{{ fmt(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="invoiceDate" label="开票日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'NORMAL' ? 'success' : 'danger'" size="small">{{ row.status === 'NORMAL' ? '正常' : '已红冲' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="refOrderNo" label="关联订单" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="制单人" width="90" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="250" align="center" v-if="hasPerm('finance:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" :disabled="genDlg?.hasGenerated('INVOICE', row.docNo)"
              @click="genDlg?.open('INVOICE', row.id)">
              {{ genDlg?.hasGenerated('INVOICE', row.docNo) ? '已生成' : '生成凭证' }}
            </button>
            <button class="op-btn op-btn-primary" @click="openDialog(row)" v-if="row.status === 'NORMAL'">编辑</button>
            <button class="op-btn op-btn-warn" @click="openFlush(row)" v-if="row.status === 'NORMAL'">红冲</button>
            <button class="op-btn op-btn-danger" @click="del(row)" v-if="row.status === 'NORMAL' && Number(row.totalAmount) > 0">删除</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]" :total="filtered.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <!-- 登记发票弹窗 -->
    <el-dialog :title="editing ? '编辑发票' : '登记发票'" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="方向" required>
          <el-radio-group v-model="form.direction" :disabled="!!editing">
            <el-radio value="OUTPUT">销项（开给客户）</el-radio>
            <el-radio value="INPUT">进项（供应商开来）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.direction === 'OUTPUT' ? '客户' : '供应商'" required>
          <el-select v-model="form.partnerId" filterable placeholder="选择往来单位" style="width:100%" @change="onPartnerChange">
            <el-option v-for="p in partnerList" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="税号">
          <el-input v-model="form.partnerTaxNo" placeholder="选择单位后自动带出，可修改" />
        </el-form-item>
        <el-form-item label="发票号码">
          <el-input v-model="form.invoiceNo" placeholder="税务系统发票号码，可选" />
        </el-form-item>
        <el-form-item label="不含税金额" required>
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="100" style="width:100%" @change="calcTax" />
        </el-form-item>
        <el-form-item label="税率" required>
          <el-select v-model="form.taxRate" style="width:100%" @change="calcTax">
            <el-option label="13%" :value="13" />
            <el-option label="9%" :value="9" />
            <el-option label="6%" :value="6" />
            <el-option label="0%" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="税额">
          <span class="calc-display">¥{{ fmt(form.taxAmount) }}</span>
        </el-form-item>
        <el-form-item label="价税合计">
          <span class="calc-display strong">¥{{ fmt(form.totalAmount) }}</span>
        </el-form-item>
        <el-form-item label="开票日期" required>
          <el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="关联订单号">
          <el-input v-model="form.refOrderNo" placeholder="销售订单号/采购订单号，可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存</el-button>
      </template>
    </el-dialog>

    <!-- 红冲弹窗 -->
    <el-dialog title="发票红冲" v-model="flushVisible" width="min(1100px, 96vw)" destroy-on-close>
      <div class="flush-tip">将生成一条负数对冲发票，原发票标记为已红冲。此操作不可逆。</div>
      <el-form :model="flushForm" label-width="100px">
        <el-form-item label="原发票">
          <span>{{ flushRow ? flushRow.docNo + '（¥' + fmt(flushRow.totalAmount) + '）' : '' }}</span>
        </el-form-item>
        <el-form-item label="红字发票号">
          <el-input v-model="flushForm.redInvoiceNo" placeholder="税务系统红字发票号码，可选" />
        </el-form-item>
        <el-form-item label="红冲原因">
          <el-input v-model="flushForm.reason" type="textarea" :rows="2" placeholder="如：开票信息有误 / 退货" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="flushVisible = false">取消</el-button>
        <el-button type="danger" @click="doFlush" :loading="loading">确认红冲</el-button>
      </template>
    </el-dialog>

    <!-- 客户开票汇总弹窗 -->
    <el-dialog title="客户开票汇总（销项净额 vs 回款）" v-model="summaryVisible" width="min(900px, 94vw)">
      <p-table :data="summaryList" stripe border style="width:100%" max-height="480">
        <el-table-column prop="partnerName" label="客户" min-width="160" show-overflow-tooltip />
        <el-table-column prop="taxNo" label="税号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="invoiceAmount" label="开票净额(不含税)" width="130" align="right">
          <template #default="{ row }">¥{{ fmt(row.invoiceAmount) }}</template>
        </el-table-column>
        <el-table-column prop="invoiceTotal" label="开票净额(价税合计)" width="140" align="right">
          <template #default="{ row }">¥{{ fmt(row.invoiceTotal) }}</template>
        </el-table-column>
        <el-table-column prop="arAmount" label="应收立账" width="120" align="right">
          <template #default="{ row }">¥{{ fmt(row.arAmount) }}</template>
        </el-table-column>
        <el-table-column prop="receivedAmount" label="已回款" width="120" align="right">
          <template #default="{ row }">¥{{ fmt(row.receivedAmount) }}</template>
        </el-table-column>
      </p-table>
    </el-dialog>

    <!-- 业务转凭证（v5.61） -->
    <voucher-generate-dialog ref="genDlg" />
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
import VoucherGenerateDialog from '../components/VoucherGenerateDialog.vue'

const genDlg = ref(null)

const list = ref([])
const customerList = ref([])
const supplierList = ref([])
const perms = ref([])
const exporting = ref(false)
const visible = ref(false)
const loading = ref(false)
const editing = ref(null)
const filterDir = ref('')
const keyword = ref('')
const flushVisible = ref(false)
const flushRow = ref(null)
const flushForm = ref({ redInvoiceNo: '', reason: '' })
const summaryVisible = ref(false)
const summaryList = ref([])
const form = ref(emptyForm())

function emptyForm() {
  return { direction: 'OUTPUT', partnerId: null, partnerTaxNo: '', invoiceNo: '', amount: null,
    taxRate: 13, taxAmount: 0, totalAmount: 0, invoiceDate: todayLocal(), refOrderNo: '', remark: '' }
}

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
const partnerList = computed(() => form.value.direction === 'OUTPUT' ? customerList.value : supplierList.value)

const filtered = computed(() => {
  let arr = list.value
  if (filterDir.value) arr = arr.filter(i => i.direction === filterDir.value)
  const kw = keyword.value.trim().toLowerCase()
  if (kw) arr = arr.filter(i => [i.docNo, i.invoiceNo, i.partnerName, i.partnerTaxNo, i.refOrderNo].some(v => v && String(v).toLowerCase().includes(kw)))
  return arr
})

const totalOutput = computed(() => filtered.value.filter(i => i.direction === 'OUTPUT').reduce((s, i) => s + Number(i.totalAmount || 0), 0))
const totalInput = computed(() => filtered.value.filter(i => i.direction === 'INPUT').reduce((s, i) => s + Number(i.totalAmount || 0), 0))
const totalTax = computed(() => filtered.value.filter(i => i.direction === 'OUTPUT').reduce((s, i) => s + Number(i.taxAmount || 0), 0))
const flushCount = computed(() => list.value.filter(i => i.status === 'FLUSHED').length)

async function fetch() {
  try { list.value = await api.get('/invoice') } catch {}
}
async function fetchPartners() {
  try { customerList.value = await api.get('/customer', { params: { enabled: true } }) } catch {}
  try { supplierList.value = await api.get('/supplier', { params: { enabled: true } }) } catch {}
}

function onPartnerChange(id) {
  const p = partnerList.value.find(x => x.id === id)
  form.value.partnerTaxNo = p?.taxNo || ''
}

function calcTax() {
  const amt = Number(form.value.amount || 0)
  form.value.taxAmount = Number((amt * (form.value.taxRate || 0) / 100).toFixed(2))
  form.value.totalAmount = Number((amt + form.value.taxAmount).toFixed(2))
}

function openDialog(row) {
  editing.value = row
  if (row) {
    form.value = { direction: row.direction, partnerId: row.partnerId, partnerTaxNo: row.partnerTaxNo || '',
      invoiceNo: row.invoiceNo || '', amount: Number(row.amount), taxRate: row.taxRate || 13,
      taxAmount: Number(row.taxAmount || 0), totalAmount: Number(row.totalAmount || 0),
      invoiceDate: row.invoiceDate, refOrderNo: row.refOrderNo || '', remark: row.remark || '' }
  } else {
    form.value = emptyForm()
  }
  fetchPartners()
  visible.value = true
}

async function submit() {
  if (!form.value.partnerId) { ElMessage.warning('请选择往来单位'); return }
  if (!form.value.amount || form.value.amount <= 0) { ElMessage.warning('金额必须大于0'); return }
  calcTax()
  loading.value = true
  try {
    const body = { ...form.value, partnerType: form.value.direction === 'OUTPUT' ? 'CUSTOMER' : 'SUPPLIER' }
    if (editing.value) await api.put(`/invoice/${editing.value.id}`, body)
    else await api.post('/invoice', body)
    ElMessage.success(editing.value ? '发票已更新' : '发票已登记')
    visible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

function openFlush(row) {
  flushRow.value = row
  flushForm.value = { redInvoiceNo: '', reason: '' }
  flushVisible.value = true
}

async function doFlush() {
  loading.value = true
  try {
    await api.post(`/invoice/${flushRow.value.id}/red-flush`, null, { params: flushForm.value })
    ElMessage.success('已红冲')
    flushVisible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '红冲失败')
  } finally { loading.value = false }
}

async function del(row) {
  try {
    await ElMessageBox.confirm(`确定删除发票 ${row.docNo}？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await api.delete(`/invoice/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

async function showSummary() {
  try {
    summaryList.value = await api.get('/invoice/customer-summary')
    summaryVisible.value = true
  } catch {}
}

async function doExport() {
  exporting.value = true
  try {
    // v8.10.2：导出与页面筛选同口径（原传空对象全量导出）
    const params = {}
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (filterDir.value) params.direction = filterDir.value
    await downloadFile('/invoice/export', params, `发票登记-${todayLocal()}.xlsx`)
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
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.sc-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.sc-value { font-size: 20px; font-weight: 700; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
.dim { color: #94a3b8; font-size: 12px; }
.calc-display { font-size: 14px; color: #475569; }
.calc-display.strong { font-size: 16px; font-weight: 700; color: #0f172a; }
.flush-tip { font-size: 13px; color: #b45309; background: #fffbeb; border: 1px solid #fde68a; border-radius: 6px; padding: 10px 12px; margin-bottom: 16px; }
</style>
