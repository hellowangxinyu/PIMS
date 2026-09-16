<template>
  <div class="page-container">
    <div class="page-header">
      <h2>物流运费</h2>
      <div class="header-actions">
        <el-date-picker v-model="filterMonth" type="month" value-format="YYYY-MM" placeholder="全部月份" clearable style="width:130px" />
        <el-input v-model="keyword" placeholder="订单号/发货单/物流单号/客户" clearable style="width:220px" />
        <el-button type="primary" @click="openDialog(null)" v-if="hasPerm('sales:write')">登记运费</el-button>
      </div>
    </div>

    <div class="summary-cards" v-if="hasAmountPerm('shipping')">
      <div class="summary-card"><div class="sc-label">公司承担合计</div><div class="sc-value red">¥{{ fmt(companyTotal) }}</div></div>
      <div class="summary-card"><div class="sc-label">客户到付合计</div><div class="sc-value muted">¥{{ fmt(customerTotal) }}</div></div>
      <div class="summary-card"><div class="sc-label">记录数</div><div class="sc-value">{{ filtered.length }}</div></div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">运费自动归集到销售订单成本（毛利分析月度/客户维度含运费；客户到付仅记录不进成本）</span></div>
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单号" width="135" />
        <el-table-column prop="shipDate" label="发货日期" width="105" />
        <el-table-column prop="salesOrderNo" label="销售订单" width="130" show-overflow-tooltip />
        <el-table-column prop="outboundDocNo" label="发货单" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.outboundDocNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
        <el-table-column prop="carrier" label="承运商" width="90" align="center" />
        <el-table-column prop="trackingNo" label="物流单号" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.trackingNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="freight" label="运费" width="110" align="right" v-if="hasAmountPerm('shipping')">
          <template #default="{ row }">¥{{ fmt(row.freight) }}</template>
        </el-table-column>
        <el-table-column label="承担方" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.borne === 'COMPANY' ? 'danger' : 'info'" size="small">{{ row.borne === 'COMPANY' ? '公司' : '客户到付' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="登记人" width="85" />
        <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip />
        <el-table-column label="操作" width="130" align="center" v-if="hasPerm('sales:write')">
          <template #default="{ row }">
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

    <el-dialog :title="editing ? '编辑运费 ' + editing.docNo : '登记运费'" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <!-- v5.66.3 参照发货单为主入口（可跳过直接手填订单）；选了发货单自动带出订单/客户 -->
        <el-form-item label="发货单">
          <el-select v-model="form.outboundDocNo" filterable clearable :filter-method="outboundFilter" placeholder="搜索选择发货单（可跳过，直接填下方订单号）" style="width:100%" @change="onOutboundChange" @clear="onOutboundClear">
            <el-option v-for="o in outboundOptions" :key="o.docNo" :value="o.docNo"
              :label="o.docNo + ' · ' + (o.salesOrderNo || '') + ' · ' + (o.customerName || '')" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售订单" required>
          <el-input v-model="form.salesOrderNo" :disabled="!!form.outboundDocNo" placeholder="选发货单自动带出；未选发货单时手填订单号（如 SO-20260826-0001）" />
        </el-form-item>
        <el-form-item label="客户"><el-input v-model="form.customerName" placeholder="自动带出，可手填" /></el-form-item>
        <el-form-item label="承运商">
          <el-select v-model="form.carrier" filterable allow-create style="width:100%" placeholder="选择或输入承运商">
            <el-option v-for="d in carriers" :key="d.value" :label="d.label" :value="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号"><el-input v-model="form.trackingNo" placeholder="可空" /></el-form-item>
        <el-form-item label="运费金额" required>
          <el-input-number v-model="form.freight" :min="0" :precision="2" :step="50" style="width:100%" />
        </el-form-item>
        <el-form-item label="承担方" required>
          <el-radio-group v-model="form.borne">
            <el-radio value="COMPANY">公司承担（进订单成本）</el-radio>
            <el-radio value="CUSTOMER">客户到付（仅记录）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发货日期">
          <el-date-picker v-model="form.shipDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存</el-button>
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

const list = ref([])
const dicts = ref({})
const perms = ref([])
const visible = ref(false)
const loading = ref(false)
const editing = ref(null)
const filterMonth = ref('')
const keyword = ref('')
const form = ref(emptyForm())

function emptyForm() {
  return { outboundDocNo: '', salesOrderNo: '', customerName: '', carrier: '', trackingNo: '',
    freight: null, borne: 'COMPANY', shipDate: todayLocal(), remark: '' }
}

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
const carriers = computed(() => dicts.value['logistics_company'] || [])

const filtered = computed(() => {
  let arr = list.value
  if (filterMonth.value) arr = arr.filter(r => String(r.shipDate || '').startsWith(filterMonth.value))
  if (keyword.value) {
    const kw = keyword.value.trim().toLowerCase()
    arr = arr.filter(r => [r.salesOrderNo, r.outboundDocNo, r.trackingNo, r.customerName, r.carrier]
      .some(v => (v || '').toLowerCase().includes(kw)))
  }
  return arr
})

// v5.66.1 订单下拉（搜索选择 + 选中带出客户）
const orders = ref([])
const orderOptions = ref([])
function orderFilter(kw) {
  const k = (kw || '').trim().toLowerCase()
  orderOptions.value = !k ? orders.value.slice(0, 60)
    : orders.value.filter(o => (o.orderNo || '').toLowerCase().includes(k) || (o.customerName || '').toLowerCase().includes(k)).slice(0, 60)
}
function onOrderChange(orderNo) {
  const o = orders.value.find(x => x.orderNo === orderNo)
  if (o && !form.value.customerName) form.value.customerName = o.customerName || ''
}

// v5.66.2 发货单下拉（主入口）：已确认销售出库单，选中带出订单/客户
const byOrderDirect = ref(false)
const outbounds = ref([])
const outboundOptions = ref([])
function outboundFilter(kw) {
  const k = (kw || '').trim().toLowerCase()
  outboundOptions.value = !k ? outbounds.value.slice(0, 60)
    : outbounds.value.filter(o => [o.docNo, o.salesOrderNo, o.customerName, o.materialName]
        .some(v => (v || '').toLowerCase().includes(k))).slice(0, 60)
}
function onOutboundChange(docNo) {
  const o = outbounds.value.find(x => x.docNo === docNo)
  if (o) {
    form.value.salesOrderNo = o.salesOrderNo || ''
    form.value.customerName = o.customerName || ''
    if (!form.value.shipDate) form.value.shipDate = (o.createTime || '').toString().replace('T', ' ').slice(0, 10)
  }
}
function onOutboundClear() {
  form.value.salesOrderNo = ''
  form.value.customerName = ''
}
const companyTotal = computed(() => filtered.value.filter(r => r.borne === 'COMPANY').reduce((s, r) => s + Number(r.freight || 0), 0))
const customerTotal = computed(() => filtered.value.filter(r => r.borne === 'CUSTOMER').reduce((s, r) => s + Number(r.freight || 0), 0))

async function fetch() {
  try { list.value = await api.get('/shipping') } catch {}
}

function openDialog(row) {
  editing.value = row
  form.value = row
    ? { outboundDocNo: row.outboundDocNo || '', salesOrderNo: row.salesOrderNo, customerName: row.customerName || '',
        carrier: row.carrier || '', trackingNo: row.trackingNo || '', freight: Number(row.freight) || null,
        borne: row.borne, shipDate: row.shipDate || '', remark: row.remark || '' }
    : emptyForm()
  orderOptions.value = orders.value.slice(0, 60)
  outboundOptions.value = outbounds.value.slice(0, 60)
  visible.value = true
}

async function resolveOutbound() {
  if (!form.value.outboundDocNo) { ElMessage.warning('请先填发货单号'); return }
  try {
    const r = await api.get('/shipping/resolve-outbound', { params: { outboundDocNo: form.value.outboundDocNo.trim() } })
    form.value.salesOrderNo = r.salesOrderNo || form.value.salesOrderNo
    form.value.customerName = r.customerName || form.value.customerName
    ElMessage.success('已带出订单与客户')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '带出失败')
  }
}

async function submit() {
  if (!form.value.salesOrderNo) { ElMessage.warning('销售订单号必填（选发货单自动带出或手填）'); return }
  if (form.value.freight === null || form.value.freight === undefined || form.value.freight < 0) { ElMessage.warning('运费金额不能为负'); return }
  if (form.value.borne === 'COMPANY' && (!form.value.freight || form.value.freight <= 0)) { ElMessage.warning('公司承担的运费必须大于 0（客户到付可填 0）'); return }
  loading.value = true
  try {
    if (editing.value) await api.put(`/shipping/${editing.value.id}`, form.value)
    else await api.post('/shipping', form.value)
    ElMessage.success('已保存')
    visible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

async function del(row) {
  try { await ElMessageBox.confirm(`确定删除运费记录 ${row.docNo}？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await api.delete(`/shipping/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败') }
}

const { page, pageSize, pagedRows, resetPage } = usePaging(filtered)

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
  try { orders.value = await api.get('/sales-order') } catch {}   // 订单下拉（直接选订单模式）
  try {   // 发货单下拉（主入口）：已确认销售出库单
    const r = await api.get('/outbound/sales', { params: { page: 1, pageSize: 500 } })
    const d = r.rows || r
    outbounds.value = (Array.isArray(d) ? d : []).filter(o => o.status === 'CONFIRMED')
  } catch {}
  try {
    const all = await api.get('/dict')
    const map = {}
    for (const item of all) {
      if (!map[item.type]) map[item.type] = []
      map[item.type].push(item)
    }
    dicts.value = map
  } catch {}
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.sc-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.sc-value { font-size: 20px; font-weight: 700; }
.sc-value.red { color: #b56a5c; }
.sc-value.muted { color: #94a3b8; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
</style>
