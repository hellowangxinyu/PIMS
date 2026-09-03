<template>
  <div class="page-container">
    <div class="page-header">
      <h2>费用管理</h2>
      <div class="header-actions">
        <el-radio-group v-model="filterDir">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="EXPENSE">支出</el-radio-button>
          <el-radio-button value="INCOME">其他收入</el-radio-button>
        </el-radio-group>
        <el-select v-model="filterType" clearable placeholder="费用类型" style="width:130px">
          <el-option v-for="d in typeOptions" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
        <el-date-picker v-model="filterMonth" type="month" value-format="YYYY-MM" placeholder="选择月份" clearable style="width:130px" />
        <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
        <el-button type="primary" @click="openDialog(null)" v-if="hasPerm('finance:write')">登记费用单</el-button>
      </div>
    </div>

    <div class="summary-cards" v-if="hasAmountPerm('expense')">
      <div class="summary-card"><div class="sc-label">支出合计</div><div class="sc-value red">¥{{ fmt(totalExpense) }}</div></div>
      <div class="summary-card"><div class="sc-label">其他收入合计</div><div class="sc-value green">¥{{ fmt(totalIncome) }}</div></div>
      <div class="summary-card"><div class="sc-label">收支净额</div><div class="sc-value" :class="netAmount >= 0 ? 'green' : 'red'">¥{{ fmt(netAmount) }}</div></div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ filtered.length }} 条记录</span></div>
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单号" min-width="130" show-overflow-tooltip />
        <el-table-column label="方向" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'EXPENSE' ? 'danger' : 'success'" size="small">{{ row.direction === 'EXPENSE' ? '支出' : '其他收入' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="费用类型" width="100" align="center">
          <template #default="{ row }">{{ typeLabel(row.direction, row.expenseType) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120" align="right" v-if="hasAmountPerm('expense')">
          <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="occurDate" label="发生日期" width="110" />
        <el-table-column label="支付方式" width="100" align="center">
          <template #default="{ row }">{{ methodLabel(row.method) }}</template>
        </el-table-column>
        <el-table-column prop="partner" label="往来对象" min-width="140" show-overflow-tooltip />
        <el-table-column prop="handler" label="经手人" width="90" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="200" align="center" v-if="hasPerm('finance:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" :disabled="genDlg?.hasGenerated('EXPENSE', row.docNo)"
              @click="genDlg?.open('EXPENSE', row.id)">
              {{ genDlg?.hasGenerated('EXPENSE', row.docNo) ? '已生成' : '生成凭证' }}
            </button>
            <button class="op-btn op-btn-primary" @click="openDialog(row)">编辑</button>
            <button class="op-btn op-btn-danger" @click="del(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]" :total="filtered.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <el-dialog :title="editing ? '编辑费用单' : '登记费用单'" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="方向" required>
          <el-radio-group v-model="form.direction">
            <el-radio value="EXPENSE">支出</el-radio>
            <el-radio value="INCOME">其他收入</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="费用类型" required>
          <el-select v-model="form.expenseType" filterable style="width:100%"
            placeholder="输入编号或名称，如 01 或 运费">
            <el-option v-for="(d, i) in formTypeOptions" :key="d.value"
              :label="String(i + 1).padStart(2, '0') + ' ' + d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="100" style="width:100%" />
        </el-form-item>
        <el-form-item label="发生日期" required>
          <el-date-picker v-model="form.occurDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="支付方式">
          <el-select v-model="form.method" filterable style="width:100%"
            placeholder="输入编号或名称，如 01 或 银行">
            <el-option v-for="m in METHOD_OPTIONS" :key="m.value"
              :label="String(m.no).padStart(2, '0') + ' ' + m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="往来对象">
          <el-input v-model="form.partner" placeholder="物流公司/房东等，可选" />
        </el-form-item>
        <el-form-item label="经手人">
          <el-input v-model="form.handler" placeholder="可选" />
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'
import { downloadFile } from '../utils/download'
import VoucherGenerateDialog from '../components/VoucherGenerateDialog.vue'

const genDlg = ref(null)

const list = ref([])
const dicts = ref({})
const perms = ref([])
const exporting = ref(false)
const visible = ref(false)
const loading = ref(false)
const editing = ref(null)
const filterDir = ref('')
const filterType = ref('')
const filterMonth = ref('')
const form = ref(emptyForm())

function emptyForm() {
  return { direction: 'EXPENSE', expenseType: '', amount: null, occurDate: new Date().toISOString().slice(0, 10),
    method: 'BANK', partner: '', handler: '', remark: '' }
}

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }

// 支付方式固定清单（带序号，下拉支持输入编号快选，如 01=银行转账）
const METHOD_OPTIONS = [
  { no: 1, value: 'BANK', label: '银行转账' },
  { no: 2, value: 'CASH', label: '现金' },
  { no: 3, value: 'ACCEPTANCE', label: '承兑' },
  { no: 4, value: 'WECHAT', label: '微信' },
  { no: 5, value: 'OTHER', label: '其他' }
]
function methodLabel(m) { return METHOD_OPTIONS.find(x => x.value === m)?.label || m || '' }

function dictItems(type) { return dicts.value[type] || [] }
const typeOptions = computed(() => [...dictItems('expense_type'), ...dictItems('other_income_type')])
const formTypeOptions = computed(() => form.value.direction === 'EXPENSE' ? dictItems('expense_type') : dictItems('other_income_type'))
function typeLabel(dir, v) {
  const arr = dir === 'EXPENSE' ? dictItems('expense_type') : dictItems('other_income_type')
  return arr.find(d => d.value === v)?.label || v
}

const filtered = computed(() => {
  let arr = list.value
  if (filterDir.value) arr = arr.filter(e => e.direction === filterDir.value)
  if (filterType.value) arr = arr.filter(e => e.expenseType === filterType.value)
  if (filterMonth.value) arr = arr.filter(e => String(e.createTime || '').startsWith(filterMonth.value))
  return arr
})

const totalExpense = computed(() => filtered.value.filter(e => e.direction === 'EXPENSE').reduce((s, e) => s + Number(e.amount || 0), 0))
const totalIncome = computed(() => filtered.value.filter(e => e.direction === 'INCOME').reduce((s, e) => s + Number(e.amount || 0), 0))
const netAmount = computed(() => totalIncome.value - totalExpense.value)

async function fetch() {
  try { list.value = await api.get('/expense') } catch {}
}
async function fetchDicts() {
  try {
    const all = await api.get('/dict')
    const map = {}
    for (const item of all) {
      if (!map[item.type]) map[item.type] = []
      map[item.type].push(item)
    }
    dicts.value = map
  } catch {}
}

function openDialog(row) {
  editing.value = row
  if (row) {
    form.value = { direction: row.direction, expenseType: row.expenseType, amount: Number(row.amount),
      occurDate: row.occurDate, method: row.method || 'BANK', partner: row.partner || '', handler: row.handler || '', remark: row.remark || '' }
  } else {
    form.value = emptyForm()
  }
  visible.value = true
}

async function submit() {
  if (!form.value.expenseType) { ElMessage.warning('请选择费用类型'); return }
  if (!form.value.amount || form.value.amount <= 0) { ElMessage.warning('金额必须大于0'); return }
  loading.value = true
  try {
    if (editing.value) await api.put(`/expense/${editing.value.id}`, form.value)
    else await api.post('/expense', form.value)
    ElMessage.success('保存成功')
    visible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

async function del(row) {
  try {
    await ElMessageBox.confirm(`确定删除费用单 ${row.docNo}？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await api.delete(`/expense/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/expense/export', {}, `费用单-${new Date().toISOString().slice(0, 10)}.xlsx`)
  } catch {} finally { exporting.value = false }
}

const { page, pageSize, pagedRows, resetPage } = usePaging(filtered)

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
  fetchDicts()
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
.sc-value.red { color: #ef4444; }
.sc-value.green { color: #16a34a; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
</style>
