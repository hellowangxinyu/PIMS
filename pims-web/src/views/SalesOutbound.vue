<template>
  <div class="page-container">
    <div class="page-header">
      <h2>销售出库</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openDialog">新建出库单</el-button>
      </div>
    </div>
    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ total }} 条记录</span>
          <el-input v-model="searchText" placeholder="搜索品名/编码/批号" clearable size="small" style="width:220px;margin-left:auto"  @keyup.enter="onSearch" @clear="onSearch" /></div>
      <p-table :data="rows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单据号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="salesOrderNo" label="来源销售单" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.salesOrderNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="110" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="物料编码" min-width="130" />
        <el-table-column prop="materialName" label="品名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="qty" label="出库数量" width="100" align="right" />
        <el-table-column prop="batchNo" label="批号" width="120" show-overflow-tooltip />
        <el-table-column label="实际成本" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.cost != null" class="cost-cell">￥{{ Number(row.cost).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="出库仓库" width="120">
          <template #default="{ row }">{{ whName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'warning'" size="small">
              {{ row.status === 'CONFIRMED' ? '已确认' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="90" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 'DRAFT'">
              <button class="op-btn op-btn-primary" @click="openAudit(row)">审核</button>
            </template>
            <template v-else>
              <button class="op-btn op-btn-trace" @click="printNote(row)">送货单</button>
              <button class="op-btn" v-if="hasPerm('sales:write')" @click="openFreight(row)">运费</button>
            </template>
          </template>
        </el-table-column>
      </p-table>
      <!-- 分页（v5.2） -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="fetch"
          @size-change="onSearch"
        />
      </div>
    </div>

    <el-dialog title="新建销售出库单" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="来源销售订单" required>
          <el-select v-model="form.salesOrderNo" filterable placeholder="选择销售订单（销售出库必须参照销售订单）" style="width:100%" @change="onSalesOrderChange">
            <el-option v-for="so in confirmedSalesOrders" :key="so.orderNo" :label="so.orderNo + ' ' + (so.customerName || '')" :value="so.orderNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户" required><el-input :value="form.customerName" placeholder="由销售订单带出" disabled /></el-form-item>
        <el-form-item label="仓库" required>
          <el-select v-model="form.warehouseId" placeholder="选择仓库" style="width:100%" @change="onWhChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料" required>
          <el-select v-model="form.materialCode" filterable placeholder="选择销售订单明细中的物料" style="width:100%" @change="onMatChange" :disabled="!!form.materialCode">
            <el-option v-for="m in orderItems" :key="m.materialCode" :label="m.materialCode + ' ' + (m.materialName || '') + '（剩余 ' + m.remaining + m.unit + '）'" :value="m.materialCode" />
          </el-select>
          <div v-if="orderItems.length && !form.materialCode" class="qty-hint">物料由销售订单带出，选中后不可修改；仅显示未发完的明细</div>
          <div v-else-if="form.materialCode" class="qty-hint">物料已锁定（来源于销售订单），如需更换请重新选择销售订单</div>
          <div v-else class="qty-hint">该订单无可发物料（明细已全部发完）</div>
        </el-form-item>
        <el-form-item label="品名"><el-input :value="form.materialName" disabled /></el-form-item>
        <el-form-item label="当前库存"><el-input :value="form.currentQty" disabled /></el-form-item>
        <el-form-item label="订单剩余"><el-input :value="orderRemaining" disabled /></el-form-item>
        <el-form-item label="出库数量" required>
          <el-input-number v-model="form.qty" :min="0.001" :max="maxQty" :precision="3" :step="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存单据</el-button>
      </template>
    </el-dialog>

    <!-- 审核出库单（仓管核对/调整实际出库数量） -->
    <el-dialog :title="'审核出库单 ' + (auditRow?.docNo || '')" v-model="auditVisible" width="min(1100px, 96vw)">
      <el-form :model="auditForm" label-width="110px">
        <el-form-item label="客户"><el-input :value="auditRow?.customerName || '-'" disabled /></el-form-item>
        <el-form-item label="物料编码"><el-input :value="auditRow?.materialCode || '-'" disabled /></el-form-item>
        <el-form-item label="品名"><el-input :value="auditRow?.materialName || '-'" disabled /></el-form-item>
        <el-form-item label="批号"><el-input :value="auditRow?.batchNo || '-'" disabled /></el-form-item>
        <el-form-item label="单据数量"><el-input :value="auditRow?.qty ?? '-'" disabled /></el-form-item>
        <el-form-item label="实际出库数量" required>
          <el-input-number v-model="auditForm.qty" :min="0.001" :max="Number(auditRow?.qty) || 99999" :precision="3" :step="1" style="width:100%" />
          <div class="qty-hint">可调整实际出库数量（不超过单据数量），确认后扣减库存并生成应收</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAudit" :loading="loading">确认审核</el-button>
      </template>
    </el-dialog>

    <!-- v5.66 运费快捷登记（预填发货单/订单/客户）；已登记的直接编辑 -->
    <el-dialog :title="(freightEditing ? '编辑运费 · ' + freightEditing.docNo : '登记运费 · ') + (freightRow?.docNo || '')" v-model="freightVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="freightForm" label-width="90px">
        <el-form-item label="订单"><el-input :value="freightRow?.salesOrderNo || '-'" disabled /></el-form-item>
        <el-form-item label="客户"><el-input :value="freightRow?.customerName || '-'" disabled /></el-form-item>
        <el-form-item label="承运商">
          <el-input v-model="freightForm.carrier" placeholder="如：德邦 / 自送" />
        </el-form-item>
        <el-form-item label="物流单号"><el-input v-model="freightForm.trackingNo" placeholder="可空" /></el-form-item>
        <el-form-item label="运费金额" required>
          <el-input-number v-model="freightForm.freight" :min="0" :precision="2" :step="50" style="width:100%" />
        </el-form-item>
        <el-form-item label="承担方" required>
          <el-radio-group v-model="freightForm.borne">
            <el-radio value="COMPANY">公司承担（进订单成本）</el-radio>
            <el-radio value="CUSTOMER">客户到付（仅记录）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="freightVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFreight" :loading="loading">{{ freightEditing ? '更新' : '保存' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { printDeliveryNote } from '../utils/deliveryNotePrint'
import api from '../api'

const perms = ref([])
function hasPerm(c) { return perms.value.includes(c) }

// v5.66 运费快捷登记；v5.66.1 已登记的发货单直接进编辑态（一个发货单只能有一笔运费）
const freightVisible = ref(false)
const freightRow = ref(null)
const freightEditing = ref(null)   // 已有运费记录（编辑态）
const freightForm = ref({ carrier: '', trackingNo: '', freight: null, borne: 'COMPANY' })

async function openFreight(row) {
  freightRow.value = row
  freightForm.value = { carrier: '', trackingNo: '', freight: null, borne: 'COMPANY' }
  freightEditing.value = null
  try {
    const r = await api.get('/shipping/resolve-outbound', { params: { outboundDocNo: row.docNo } })
    if (r.existing) {
      freightEditing.value = r.existing
      freightForm.value = { carrier: r.existing.carrier || '', trackingNo: r.existing.trackingNo || '',
        freight: Number(r.existing.freight) || null, borne: r.existing.borne }
    }
  } catch {}
  freightVisible.value = true
}

async function submitFreight() {
  if (freightForm.value.freight === null || freightForm.value.freight === undefined || freightForm.value.freight < 0) { ElMessage.warning('运费金额不能为负'); return }
  if (freightForm.value.borne === 'COMPANY' && (!freightForm.value.freight || freightForm.value.freight <= 0)) { ElMessage.warning('公司承担的运费必须大于 0（客户到付可填 0）'); return }
  loading.value = true
  try {
    if (freightEditing.value) {
      await api.put(`/shipping/${freightEditing.value.id}`, freightForm.value)
      ElMessage.success('运费已更新')
    } else {
      await api.post('/shipping', {
        outboundDocNo: freightRow.value.docNo,
        salesOrderNo: freightRow.value.salesOrderNo,
        customerName: freightRow.value.customerName,
        ...freightForm.value,
      })
      ElMessage.success('运费已登记，将归集到该订单成本')
    }
    freightVisible.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(25)
const warehouses = ref([])
const invList = ref([])
const visible = ref(false)
const loading = ref(false)
const form = ref({ salesOrderNo: '', warehouseId: '', materialCode: '', materialName: '', customerName: '', currentQty: 0, qty: 1, remark: '' })
// v5.27：销售出库必须参照销售订单（已确认的销售订单下拉）
const confirmedSalesOrders = ref([])
// v5.27：当前销售订单的可发物料明细（物料只能从订单带出，不允许选订单外物料）
const orderItems = ref([])
async function loadConfirmedSalesOrders() {
  try { confirmedSalesOrders.value = await api.get('/sales-order', { params: { status: 'CONFIRMED' } }) } catch {}
}
async function onSalesOrderChange(orderNo) {
  form.value.customerName = ''
  form.value.materialCode = ''
  form.value.materialName = ''
  form.value.currentQty = 0
  orderItems.value = []
  if (!orderNo) return
  const so = confirmedSalesOrders.value.find(s => s.orderNo === orderNo)
  if (so && so.customerName) form.value.customerName = so.customerName
  try {
    const items = await api.get(`/sales-order/${so.id}/items`)
    orderItems.value = (items || [])
      .map(it => ({ materialCode: it.materialCode, materialName: it.materialName || '', unit: it.unit || 'kg', remaining: Math.max(0, Number(it.qty || 0) - Number(it.shippedQty || 0)) }))
      .filter(it => it.remaining > 0)
    // 订单只有一条可发明细：自动带出并锁定
    if (orderItems.value.length === 1) {
      const it = orderItems.value[0]
      form.value.materialCode = it.materialCode
      form.value.materialName = it.materialName
    } else if (orderItems.value.length > 1) {
      ElMessage.info(`该订单有 ${orderItems.value.length} 条未发完明细，请选择要出库的物料`)
    }
  } catch {}
}

// 审核弹窗
const auditVisible = ref(false)
const auditRow = ref(null)
const auditForm = ref({ qty: 0 })

// v5.47：打印送货单（客户签收凭证，已确认出库单）；v5.66 顺带带出客户收货地址
function printNote(row) {
  api.post('/print-count', { docType: 'SALES_OUTBOUND', docNo: row.docNo }).then(n => { row.printCount = n }).catch(() => {})
  api.get('/customer', { params: { keyword: row.customerName } }).then(list => {
    const c = (list || []).find(x => x.name === row.customerName)
    doPrint(row, c ? { address: c.address, contactPerson: c.contactPerson, contactPhone: c.contactPhone } : {})
  }).catch(() => doPrint(row, {}))
}

function doPrint(row, extra) {
  const ok = printDeliveryNote({
    docNo: row.docNo,
    salesOrderNo: row.salesOrderNo,
    customerName: row.customerName,
    materialCode: row.materialCode,
    materialName: row.materialName,
    batchNo: row.batchNo,
    qty: row.qty,
    unit: row.unit,
    warehouseName: whName(row.warehouseId),
    createBy: row.createdBy,
    dateText: (row.createTime || '').replace('T', ' ').substring(0, 10),
    ...extra
  })
  if (!ok) ElMessage.warning('浏览器拦截了弹窗，请允许本站弹出窗口')
}

function whName(id) { const w = warehouses.value.find(w => String(w.id) === String(id)); return w ? w.name : id }
function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }

async function fetch() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (searchText.value.trim()) params.keyword = searchText.value.trim()
  try {
    const res = await api.get('/outbound/sales', { params })
    rows.value = res.rows
    total.value = res.total
  } catch {} }
function onSearch() { page.value = 1; fetch() }
function openDialog() { form.value = { salesOrderNo: '', warehouseId: '', materialCode: '', materialName: '', customerName: '', currentQty: 0, qty: 1, remark: '' }; invList.value = []; orderItems.value = []; visible.value = true }
// v5.18 修复：/inventory 已改远程分页（返回 {rows,total}），改为选中仓库后按仓库拉取该仓全部库存
async function onWhChange(whId) {
  form.value.materialCode = ''; form.value.materialName = ''; form.value.currentQty = 0
  invList.value = []
  if (!whId) return
  try {
    const res = await api.get('/inventory', { params: { warehouseId: whId, pageSize: 5000 } })
    invList.value = res.rows || []
  } catch { /* ignore */ }
}
function onMatChange(code) {
  const r = invList.value.find(r => r.materialCode === code)
  if (r) { form.value.materialName = r.materialName || ''; form.value.currentQty = Number(r.qty) || 0 }
  else { form.value.materialName = ''; form.value.currentQty = 0; ElMessage.warning('该仓库没有此物料的库存，无法出库') }
}

// v5.27：出库数量上限 = min(当前库存, 订单剩余可发)
const orderRemaining = computed(() => {
  const it = orderItems.value.find(i => i.materialCode === form.value.materialCode)
  return it ? it.remaining : '-'
})
const maxQty = computed(() => {
  const rem = orderItems.value.find(i => i.materialCode === form.value.materialCode)?.remaining
  const inv = form.value.currentQty || 0
  if (!rem) return inv || 99999
  return Math.min(inv, rem)
})

async function submit() {
  if (!form.value.salesOrderNo) { ElMessage.warning('销售出库必须参照销售订单，请选择来源销售订单'); return }
  if (!form.value.warehouseId || !form.value.materialCode) { ElMessage.warning('请选择仓库和物料'); return }
  loading.value = true
  try {
    await api.post('/outbound/sales', null, { params: { salesOrderNo: form.value.salesOrderNo, materialCode: form.value.materialCode, materialName: form.value.materialName, warehouseId: form.value.warehouseId, qty: form.value.qty, remark: form.value.remark || undefined } })
    ElMessage.success('单据已创建（草稿）')
    visible.value = false
    fetch()
  } catch {} finally { loading.value = false }
}

// 审核：打开弹窗，仓管核对并调整实际出库数量
function openAudit(row) {
  auditRow.value = row
  auditForm.value = { qty: Number(row.qty) || 0 }
  auditVisible.value = true
}

async function submitAudit() {
  const row = auditRow.value
  if (!row) return
  if (!auditForm.value.qty || auditForm.value.qty <= 0) { ElMessage.warning('请输入实际出库数量'); return }
  loading.value = true
  try {
    await api.post(`/outbound/sales/${row.id}/confirm`, null, { params: { qty: auditForm.value.qty } })
    ElMessage.success('审核完成，库存已扣减并生成应收')
    auditVisible.value = false
    fetch()
  } catch (e) {
    if (e?.response?.data?.msg) ElMessage.error(e.response.data.msg)
    else if (e?.message && e.message !== 'Network Error') ElMessage.error(e.message)
  } finally { loading.value = false }
}

// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
// v5.7：按品名/编码/批号查询（批号全系统可追溯）
const searchText = ref('')

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch {}
  loadConfirmedSalesOrders()
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
.text-muted { color: #94a3b8; font-size: 12px; }
.cost-cell { color: #ea580c; font-weight: 600; }
.qty-hint { font-size: 12px; color: #94a3b8; margin-top: 6px; }
</style>
