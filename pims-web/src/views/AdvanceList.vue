<template>
  <div class="page-container">
    <div class="page-header">
      <h2>预收预付</h2>
      <div class="header-actions">
        <el-radio-group v-model="filterDir">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="RECEIVE">预收</el-radio-button>
          <el-radio-button value="PAY">预付</el-radio-button>
        </el-radio-group>
        <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
        <el-button type="primary" @click="openDialog" v-if="hasPerm('finance:write')">登记预存单</el-button>
      </div>
    </div>

    <div class="summary-cards" v-if="hasAmountPerm('advance')">
      <div class="summary-card"><div class="sc-label">预收余额合计</div><div class="sc-value green">¥{{ fmt(remainReceive) }}</div></div>
      <div class="summary-card"><div class="sc-label">预付余额合计</div><div class="sc-value">¥{{ fmt(remainPay) }}</div></div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ filtered.length }} 条记录</span></div>
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单号" min-width="130" show-overflow-tooltip />
        <el-table-column label="方向" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'RECEIVE' ? 'success' : 'warning'" size="small">{{ row.direction === 'RECEIVE' ? '预收' : '预付' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="partnerName" label="往来单位" min-width="160" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="120" align="right" v-if="hasAmountPerm('advance')">
          <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="usedAmount" label="已冲抵" width="110" align="right" v-if="hasAmountPerm('advance')">
          <template #default="{ row }">¥{{ fmt(row.usedAmount) }}</template>
        </el-table-column>
        <el-table-column label="剩余" width="120" align="right" v-if="hasAmountPerm('advance')">
          <template #default="{ row }">
            <span :style="remainOf(row) > 0 ? 'color:#16a34a;font-weight:600' : 'color:#94a3b8'">¥{{ fmt(remainOf(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="payDate" label="日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="{ UNUSED: 'info', PARTIAL: 'warning', USED: 'success' }[row.status] || 'info'" size="small">
              {{ { UNUSED: '未使用', PARTIAL: '部分冲抵', USED: '已用完' }[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="制单人" width="90" />
        <el-table-column label="操作" width="130" align="center" v-if="hasPerm('finance:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="openApply(row)" v-if="remainOf(row) > 0">冲抵</button>
            <button class="op-btn op-btn-danger" @click="del(row)" v-if="Number(row.usedAmount || 0) === 0">删除</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]" :total="filtered.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <!-- 登记弹窗 -->
    <el-dialog title="登记预存单" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="方向" required>
          <el-radio-group v-model="form.direction">
            <el-radio value="RECEIVE">预收（客户先打款）</el-radio>
            <el-radio value="PAY">预付（先付供应商）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.direction === 'RECEIVE' ? '客户' : '供应商'" required>
          <el-select v-model="form.partnerId" filterable placeholder="选择往来单位" style="width:100%">
            <el-option v-for="p in partnerList" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="1000" style="width:100%" />
        </el-form-item>
        <el-form-item label="收款/付款方式">
          <el-select v-model="form.method" filterable style="width:100%"
            placeholder="输入编号或名称，如 01 或 银行">
            <el-option v-for="m in METHOD_OPTIONS" :key="m.value"
              :label="String(m.no).padStart(2, '0') + ' ' + m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期" required>
          <el-date-picker v-model="form.payDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
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

    <!-- 冲抵弹窗 -->
    <el-dialog :title="applyRow ? (applyRow.direction === 'RECEIVE' ? '预收冲应收' : '预付冲应付') : ''" v-model="applyVisible" width="min(1100px, 96vw)" destroy-on-close>
      <div class="apply-tip" v-if="applyRow">
        预存单 {{ applyRow.docNo }}（{{ applyRow.partnerName }}）剩余 <b>¥{{ fmt(remainOf(applyRow)) }}</b>
      </div>
      <el-form :model="applyForm" label-width="100px">
        <el-form-item :label="applyRow?.direction === 'RECEIVE' ? '应收单' : '应付单'" required>
          <el-select v-model="applyForm.targetId" filterable placeholder="选择未结清单据" style="width:100%" @change="onTargetChange">
            <el-option v-for="t in targetList" :key="t.id" :value="t.id"
              :label="`${t.docNo}（剩余¥${fmt(remainOfTarget(t))}${t.refNo ? ' · ' + t.refNo : ''}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="冲抵金额" required>
          <el-input-number v-model="applyForm.amount" :min="0.01" :precision="2" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" @click="doApply" :loading="loading">确认冲抵</el-button>
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

const list = ref([])
const customerList = ref([])
const supplierList = ref([])
const arList = ref([])
const apList = ref([])
const perms = ref([])
const exporting = ref(false)
const visible = ref(false)
const loading = ref(false)
const applyVisible = ref(false)
const applyRow = ref(null)
const applyForm = ref({ targetId: null, amount: null })
const form = ref(emptyForm())

function emptyForm() {
  return { direction: 'RECEIVE', partnerId: null, amount: null, method: 'BANK', payDate: todayLocal(), remark: '' }
}

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function remainOf(a) { return Number(a?.amount || 0) - Number(a?.usedAmount || 0) }

// 收/付款方式固定清单（带序号，下拉支持输入编号快选，如 01=银行转账；与费用管理同规则）
const METHOD_OPTIONS = [
  { no: 1, value: 'BANK', label: '银行转账' },
  { no: 2, value: 'CASH', label: '现金' },
  { no: 3, value: 'ACCEPTANCE', label: '承兑' },
  { no: 4, value: 'WECHAT', label: '微信' }
]

const partnerList = computed(() => form.value.direction === 'RECEIVE' ? customerList.value : supplierList.value)

const filtered = computed(() => filterDir.value ? list.value.filter(a => a.direction === filterDir.value) : list.value)
const filterDir = ref('')

const remainReceive = computed(() => filtered.value.filter(a => a.direction === 'RECEIVE').reduce((s, a) => s + remainOf(a), 0))
const remainPay = computed(() => filtered.value.filter(a => a.direction === 'PAY').reduce((s, a) => s + remainOf(a), 0))

// 冲抵目标：该往来单位未结清单据
const targetList = computed(() => {
  if (!applyRow.value) return []
  if (applyRow.value.direction === 'RECEIVE') {
    return arList.value.filter(a => a.customerId === applyRow.value.partnerId && a.status !== 'PAID')
      .map(a => ({ ...a, refNo: a.salesOrderNo }))
  }
  return apList.value.filter(a => a.supplierId === applyRow.value.partnerId && a.status !== 'PAID')
    .map(a => ({ ...a, refNo: a.purchaseOrderNo || a.outsourceOrderNo }))
})

function remainOfTarget(t) {
  const total = Number(t.amount || 0), used = Number(t.receivedAmount ?? t.paidAmount ?? 0)
  return total - used
}

async function fetch() {
  try { list.value = await api.get('/advance') } catch {}
}

function openDialog() {
  form.value = emptyForm()
  if (customerList.value.length === 0) api.get('/customer', { params: { enabled: true } }).then(l => customerList.value = l).catch(() => {})
  if (supplierList.value.length === 0) api.get('/supplier', { params: { enabled: true } }).then(l => supplierList.value = l).catch(() => {})
  visible.value = true
}

async function submit() {
  if (!form.value.partnerId) { ElMessage.warning('请选择往来单位'); return }
  if (!form.value.amount || form.value.amount <= 0) { ElMessage.warning('金额必须大于0'); return }
  const p = partnerList.value.find(x => x.id === form.value.partnerId)
  loading.value = true
  try {
    await api.post('/advance', { ...form.value, partnerName: p?.name || '' })
    ElMessage.success('预存单已创建')
    visible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

async function openApply(row) {
  applyRow.value = row
  applyForm.value = { targetId: null, amount: null }
  try {
    if (row.direction === 'RECEIVE') arList.value = await api.get('/finance/ar')
    else apList.value = await api.get('/finance/ap')
  } catch {}
  applyVisible.value = true
}

function onTargetChange() {
  const t = targetList.value.find(x => x.id === applyForm.value.targetId)
  if (t) applyForm.value.amount = Number(Math.min(remainOf(applyRow.value), remainOfTarget(t)).toFixed(2))
}

async function doApply() {
  if (!applyForm.value.targetId) { ElMessage.warning('请选择冲抵单据'); return }
  if (!applyForm.value.amount || applyForm.value.amount <= 0) { ElMessage.warning('冲抵金额必须大于0'); return }
  loading.value = true
  try {
    const url = applyRow.value.direction === 'RECEIVE'
      ? `/advance/${applyRow.value.id}/apply-ar`
      : `/advance/${applyRow.value.id}/apply-ap`
    await api.post(url, applyForm.value)
    ElMessage.success('冲抵成功')
    applyVisible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '冲抵失败')
  } finally { loading.value = false }
}

async function del(row) {
  try {
    await ElMessageBox.confirm(`确定删除预存单 ${row.docNo}？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await api.delete(`/advance/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/advance/export', {}, `预收预付-${todayLocal()}.xlsx`)
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
.sc-value.green { color: #16a34a; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
.apply-tip { font-size: 13px; color: #475569; background: #eff4f7; border: 1px solid #c2d5de; border-radius: 6px; padding: 10px 12px; margin-bottom: 16px; }
</style>
