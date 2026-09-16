<template>
  <div class="page-container">
    <div class="page-header">
      <h2>收款单</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openDialog" v-if="hasPerm('finance:write')">新建收款单</el-button>
        <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
      </div>
    </div>
    <div class="table-card">
      <div class="type-tabs">
        <span class="type-count">共 {{ list.length }} 条记录</span>
      </div>
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="arDocNo" label="关联应收单" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.arDocNo">{{ row.arDocNo }}</span>
            <span v-else style="color:#94a3b8">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="180" show-overflow-tooltip />
        <el-table-column prop="amount" label="收款金额" width="130" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="收款方式" width="100" align="center">
          <template #default="{ row }">{{ payMethodLabel(row.method) }}</template>
        </el-table-column>
        <el-table-column prop="bankAccount" label="银行账户" min-width="130" show-overflow-tooltip />
        <el-table-column prop="receiptDate" label="收款日期" width="110" />
        <el-table-column prop="operator" label="经办人" width="90" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" v-if="hasPerm('finance:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" :disabled="genDlg?.hasGenerated('RECEIPT', row.docNo)"
              @click="genDlg?.open('RECEIPT', row.id)">
              {{ genDlg?.hasGenerated('RECEIPT', row.docNo) ? '已生成' : '生成凭证' }}
            </button>
          </template>
        </el-table-column>
      </p-table>
      <!-- 分页（v5.2） -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]"
          :total="list.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>

    <!-- 新建收款单弹窗 -->
    <el-dialog title="新建收款单" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="客户" required>
          <el-select v-model="form.customerId" filterable placeholder="选择客户" style="width:100%" @change="onCustomerChange">
            <el-option v-for="c in customerList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="未结清应收" v-if="form.customerId">
          <div class="unpaid-tip">
            <span>共 {{ unpaidCount }} 张未结清应收单</span>
            <span class="unpaid-amount">剩余合计：¥{{ fmt(unpaidTotal) }}</span>
          </div>
        </el-form-item>
        <el-form-item label="收款金额" required>
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="100" style="width:100%" />
        </el-form-item>
        <el-form-item label="收款方式" required>
          <el-select v-model="form.method" style="width:100%">
            <el-option label="银行" value="BANK" />
            <el-option label="现金" value="CASH" />
            <el-option label="承兑汇票" value="ACCEPTANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="银行账户">
          <el-input v-model="form.bankAccount" placeholder="可选" />
        </el-form-item>
        <el-form-item label="收款日期" required>
          <el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
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

    <!-- 业务转凭证（v5.61） -->
    <voucher-generate-dialog ref="genDlg" />
  </div>
</template>

<script setup>
import { todayLocal } from '../utils/date'
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'
import { downloadFile } from '../utils/download'
import VoucherGenerateDialog from '../components/VoucherGenerateDialog.vue'

const genDlg = ref(null)

const list = ref([])
const customerList = ref([])
const arList = ref([])  // 全部应收单（用于按客户计算未结清金额）
const visible = ref(false)
const loading = ref(false)
const perms = ref([])
const exporting = ref(false)
const form = ref({ customerId: null, amount: null, method: 'BANK', bankAccount: '', receiptDate: todayLocal(), remark: '' })

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
function fmtTime(t) { return t ? String(t).replace('T', ' ').substring(0, 16) : '' }
function payMethodLabel(m) { return { CASH: '现金', BANK: '银行', ACCEPTANCE: '承兑汇票' }[m] || m || '未知' }

// v5.23：导出全部收款单流水（金额列按后端 finance:amount 权限脱敏）
async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/finance/receipt/export', {}, `收款单-${todayLocal()}.xlsx`)
  } catch (e) { /* downloadFile 内已提示 */ }
  finally { exporting.value = false }
}

// 当前选中客户的未结清应收单
const currentUnpaidList = computed(() => {
  if (!form.value.customerId) return []
  return arList.value.filter(a => a.customerId === form.value.customerId && a.status !== 'PAID')
})
const unpaidCount = computed(() => currentUnpaidList.value.length)
const unpaidTotal = computed(() => currentUnpaidList.value.reduce((s, a) => s + (Number(a.amount || 0) - Number(a.receivedAmount || 0)), 0))

async function fetch() {
  resetPage()
  try { list.value = await api.get('/finance/receipt') } catch {} }
async function fetchCustomer() { try { customerList.value = await api.get('/customer', { params: { enabled: true } }) } catch {} }
async function fetchAR() { try { arList.value = await api.get('/finance/ar') } catch {} }

function openDialog() {
  form.value = { customerId: null, amount: null, method: 'BANK', bankAccount: '', receiptDate: todayLocal(), remark: '' }
  fetchCustomer()
  fetchAR()
  visible.value = true
}

function onCustomerChange() {
  // 选客户后自动填入未结清合计金额（可手动修改）
  if (unpaidTotal.value > 0) {
    form.value.amount = Number(unpaidTotal.value.toFixed(2))
  } else {
    form.value.amount = null
  }
}

async function submit() {
  if (!form.value.customerId) { ElMessage.warning('请选择客户'); return }
  if (!form.value.amount || form.value.amount <= 0) { ElMessage.warning('收款金额必须大于0'); return }
  loading.value = true
  try {
    await api.post('/finance/receipt', form.value)
    ElMessage.success('收款单已创建')
    visible.value = false
    fetch()
  } catch (e) {
    const msg = e?.response?.data?.msg || e?.message || '创建失败'
    ElMessage.error(msg)
  } finally { loading.value = false }
}


// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(list)

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
.unpaid-tip { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: #64748b; }
.unpaid-amount { color: #ef4444; font-weight: 600; }
</style>
