<template>
  <div class="page-container">
    <div class="page-header">
      <h2>销售退货</h2>
      <div class="header-actions">
        <button v-if="activeTab === 'HANDLE'" class="btn-primary" @click="openCreateDialog">新建退货单</button>
        <el-select v-if="activeTab === 'HANDLE'" v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width:130px" @change="fetch">
          <el-option label="待审核" value="DRAFT" />
          <el-option label="已审核待入库" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已入库完成" value="DONE" />
        </el-select>
      </div>
    </div>

    <div class="table-card">
      <div class="type-tabs">
        <div class="filter-tabs">
          <button :class="['filter-btn', { active: activeTab === 'HANDLE' }]" @click="switchTab('HANDLE')">退货单处理</button>
          <button :class="['filter-btn', { active: activeTab === 'DONE' }]" @click="switchTab('DONE')">已退货明细</button>
        </div>
        <span class="type-count">{{ activeTab === 'DONE' ? `共 ${doneList.length} 条已退货记录` : `共 ${list.length} 条记录` }}</span>
        <span v-if="activeTab === 'HANDLE'" class="flow-tip">流程：客户退货 → 创建退货单 → 销售员审核 → 仓管参照退货单入库（自动冲减应收）</span>
      </div>

      <!-- ===== Tab1：退货单处理 ===== -->
      <template v-if="activeTab === 'HANDLE'">
        <p-table :data="pagedRows" stripe border style="width:100%">
          <el-table-column prop="docNo" label="退货单号" width="150" show-overflow-tooltip />
          <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
          <el-table-column prop="salesOrderNo" label="原销售单号" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.salesOrderNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="refSalesOutboundNo" label="原出库单号" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.refSalesOutboundNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="materialCode" label="物料编码" min-width="130" />
          <el-table-column prop="materialName" label="品名" min-width="140" show-overflow-tooltip />
          <el-table-column prop="qty" label="退货数量" width="100" align="right" />
          <el-table-column prop="unitPrice" label="单价" width="90" align="right">
            <template #default="{ row }">{{ row.unitPrice != null ? Number(row.unitPrice).toFixed(2) : '-' }}</template>
          </el-table-column>
          <el-table-column prop="returnAmount" label="退货金额" width="110" align="right">
            <template #default="{ row }">{{ row.returnAmount != null ? Number(row.returnAmount).toFixed(2) : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="inboundDocNo" label="入库单号" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.inboundDocNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="createdBy" label="制单人" width="90" />
          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="230" align="center" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'DRAFT'">
                <button class="op-btn op-btn-success" @click="approve(row)">审核通过</button>
                <button class="op-btn op-btn-danger" @click="reject(row)">驳回</button>
              </template>
              <button v-else-if="row.status === 'APPROVED'" class="op-btn op-btn-primary" @click="openInboundDialog(row)">退货入库</button>
              <span v-else class="text-muted">—</span>
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
      </template>

      <!-- ===== Tab2：已退货明细（v5.4，status=DONE） ===== -->
      <template v-else>
        <p-table :data="donePagedRows" stripe border style="width:100%">
          <el-table-column prop="docNo" label="退货单号" width="150" show-overflow-tooltip />
          <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
          <el-table-column prop="salesOrderNo" label="原销售单号" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.salesOrderNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="materialCode" label="物料编码" min-width="130" />
          <el-table-column prop="materialName" label="品名" min-width="140" show-overflow-tooltip />
          <el-table-column prop="qty" label="退货数量" width="100" align="right" />
          <el-table-column prop="unitPrice" label="单价" width="90" align="right">
            <template #default="{ row }">{{ row.unitPrice != null ? Number(row.unitPrice).toFixed(2) : '-' }}</template>
          </el-table-column>
          <el-table-column prop="returnAmount" label="退货金额" width="110" align="right">
            <template #default="{ row }">{{ row.returnAmount != null ? Number(row.returnAmount).toFixed(2) : '-' }}</template>
          </el-table-column>
          <el-table-column prop="inboundDocNo" label="入库单号" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.inboundDocNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="createdBy" label="制单人" width="90" />
          <el-table-column prop="approvedBy" label="审核人" width="90">
            <template #default="{ row }">{{ row.approvedBy || '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="入库时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.updateTime) }}</template>
          </el-table-column>
        </p-table>
        <!-- 明细分页（独立分页实例） -->
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="donePage"
            v-model:page-size="donePageSize"
            :page-sizes="[25, 50, 100]"
            :total="doneList.length"
            layout="total, sizes, prev, pager, next"
          />
        </div>
      </template>
    </div>

    <!-- 新建退货单对话框（v5.4；v5.27 参照销售出库单必选，带出并锁定） -->
    <el-dialog title="新建销售退货单" v-model="createVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="createForm" label-width="120px">
        <el-form-item label="参照销售出库单" required>
          <el-select v-model="createForm.refSalesOutboundNo" filterable placeholder="选择销售出库单（仅未退完的）" style="width:100%" @change="onRefOutboundChange">
            <el-option v-for="o in outboundOptions" :key="o.docNo" :value="o.docNo"
              :label="`${o.docNo} | ${o.customerName} | ${o.materialCode} ${o.materialName} | ${o.batchNo} | 可退 ${o.remaining}${o.unit || 'kg'}`" />
          </el-select>
          <div v-if="!outboundOptions.length" class="qty-hint">暂无未退完的销售出库单可参照</div>
        </el-form-item>
        <el-form-item label="客户" required>
          <el-input :value="createForm.customerName" placeholder="由销售出库单带出" disabled />
        </el-form-item>
        <el-form-item label="物料" required>
          <el-input :value="(createForm.materialCode || '') + ' ' + (createForm.materialName || '')" placeholder="由销售出库单带出" disabled />
        </el-form-item>
        <el-form-item label="出库批号">
          <el-input :value="createForm.batchNo || '-'" placeholder="由销售出库单带出" disabled />
        </el-form-item>
        <el-form-item label="销售单价">
          <el-input :value="createForm.unitPrice != null ? '￥' + Number(createForm.unitPrice).toFixed(2) : '-'" disabled />
          <div class="qty-hint">按销售订单单价，退货入库时冲减应收</div>
        </el-form-item>
        <el-form-item label="退货数量" required>
          <el-input-number v-model="createForm.qty" :min="0.001" :max="maxQty" :precision="3" :step="1" style="width:200px" />
          <span class="unit-text">{{ createForm.unit || 'kg' }}</span>
          <div v-if="maxQty" class="qty-hint">不能超过出库单剩余可退量 {{ maxQty }}</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="退货原因说明（可选）" />
        </el-form-item>
      </el-form>
      <div class="tip">提交后生成「待审核」退货单；审核通过后由仓管参照退货单入库（自动生成新批号，库存增加），并自动冲减该客户销售订单的应收账款。</div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="loading" :disabled="!createForm.refSalesOutboundNo">提交退货单</el-button>
      </template>
    </el-dialog>

    <!-- 退货入库对话框：仓管选择仓库/分库/库位（批号自动生成） -->
    <el-dialog title="退货入库" v-model="inboundVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="退货单号">{{ current.docNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ current.customerName }}</el-descriptions-item>
        <el-descriptions-item label="物料">{{ current.materialCode }} {{ current.materialName }}</el-descriptions-item>
        <el-descriptions-item label="退货数量">{{ current.qty }} {{ current.unit }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="inboundForm" label-width="90px">
        <el-form-item label="入库仓库" required>
          <el-select v-model="inboundForm.warehouseId" placeholder="选择仓库" style="width:100%" @change="onWhChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="分库" required>
          <el-select v-model="inboundForm.zoneId" placeholder="请先选择仓库" style="width:100%" :disabled="!inboundForm.warehouseId" @change="onZoneChange">
            <el-option v-for="z in zones" :key="z.id" :label="z.name" :value="String(z.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="库位" required>
          <el-select v-model="inboundForm.locationId" placeholder="请先选择分库" style="width:100%" :disabled="!inboundForm.zoneId">
            <el-option v-for="l in locations" :key="l.id" :label="l.name || l.code" :value="String(l.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="tip">入库后将自动生成新批号增加库存，退货单置为「已入库完成」，同步冲减该客户销售订单的应收账款。</div>
      <template #footer>
        <el-button @click="inboundVisible = false">取消</el-button>
        <el-button type="primary" @click="submitInbound" :loading="loading">确认入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const activeTab = ref('HANDLE')   // HANDLE 退货单处理 / DONE 已退货明细
const list = ref([])
const doneList = ref([])
const filterStatus = ref('')
const loading = ref(false)

// 新建退货单（v5.27：参照销售出库单必选，带出并锁定）
const createVisible = ref(false)
const createForm = ref({ refSalesOutboundNo: '', customerId: null, customerName: '', materialCode: '', materialName: '', batchNo: '', salesOrderNo: '', qty: null, unitPrice: null, remark: '', unit: 'kg' })
const outboundOptions = ref([])
const maxQty = ref(0)

// 退货入库对话框
const inboundVisible = ref(false)
const current = ref({})
const inboundForm = ref({ warehouseId: '', zoneId: '', locationId: '' })
const warehouses = ref([])
const zones = ref([])
const locations = ref([])

function fmtTime(t) { return t ? String(t).replace('T', ' ').substring(0, 16) : '' }
function statusLabel(s) { return { DRAFT: '待审核', APPROVED: '已审核', REJECTED: '已驳回', DONE: '已入库' }[s] || '未知' }
function statusTagType(s) { return { DRAFT: 'warning', APPROVED: 'primary', REJECTED: 'danger', DONE: 'success' }[s] || 'info' }

// Tab 切换（已退货明细进入时加载数据）
function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'DONE') fetchDone()
  else fetch()
}

async function fetch() {
  resetPage()
  try {
    const params = {}
    if (filterStatus.value) params.status = filterStatus.value
    list.value = await api.get('/sales-return', { params })
  } catch {}
}

// 已退货明细（status=DONE，独立分页）
async function fetchDone() {
  doneResetPage()
  try {
    doneList.value = await api.get('/sales-return', { params: { status: 'DONE' } })
  } catch {}
}

// ===== 新建退货单 =====
async function openCreateDialog() {
  createForm.value = { refSalesOutboundNo: '', customerId: null, customerName: '', materialCode: '', materialName: '', batchNo: '', salesOrderNo: '', qty: null, unitPrice: null, remark: '', unit: 'kg' }
  maxQty.value = 0
  createVisible.value = true
  // v5.27：只列未退完的已确认销售出库单（含剩余可退量/销售单价/批号）
  try { outboundOptions.value = await api.get('/sales-return/returnable-outbounds') } catch {}
}

// v5.27：参照销售出库单（必选）→ 自动带出客户/物料/批号/销售单价并锁定，只填数量
function onRefOutboundChange(docNo) {
  const o = outboundOptions.value.find(x => x.docNo === docNo)
  if (!o) return
  createForm.value.customerName = o.customerName
  createForm.value.customerId = o.customerId || null
  createForm.value.materialCode = o.materialCode
  createForm.value.materialName = o.materialName
  createForm.value.batchNo = o.batchNo || ''
  createForm.value.salesOrderNo = o.salesOrderNo
  createForm.value.unit = o.unit || 'kg'
  createForm.value.unitPrice = o.salePrice != null ? Number(o.salePrice) : null
  maxQty.value = Number(o.remaining) || 0
  createForm.value.qty = maxQty.value || null
}

async function submitCreate() {
  const f = createForm.value
  if (!f.refSalesOutboundNo) { ElMessage.warning('请选择参照销售出库单'); return }
  if (!f.customerName) { ElMessage.warning('请填写退货客户'); return }
  if (!f.materialCode) { ElMessage.warning('请选择物料'); return }
  if (!f.qty || f.qty <= 0) { ElMessage.warning('请填写退货数量'); return }
  if (maxQty.value && f.qty > maxQty.value) { ElMessage.warning(`退货数量不能超过出库单剩余可退量 ${maxQty.value}`); return }
  loading.value = true
  try {
    await api.post('/sales-return', {
      customerId: f.customerId || undefined,
      customerName: f.customerName,
      salesOrderNo: f.salesOrderNo || undefined,
      refSalesOutboundNo: f.refSalesOutboundNo,
      batchNo: f.batchNo || undefined,
      materialCode: f.materialCode,
      materialName: f.materialName,
      unit: f.unit || 'kg',
      qty: f.qty,
      unitPrice: f.unitPrice != null ? f.unitPrice : undefined,
      remark: f.remark || undefined
    })
    ElMessage.success('退货单已创建，等待销售员审核')
    createVisible.value = false
    fetch()
  } catch (e) {
    if (e?.response?.data?.msg) ElMessage.error(e.response.data.msg)
  } finally { loading.value = false }
}

// ===== 销售员审核/驳回 =====
async function approve(row) {
  try {
    const { value: remark } = await ElMessageBox.prompt('审核备注（可选）', `审核退货单 ${row.docNo}`, {
      confirmButtonText: '审核通过', cancelButtonText: '取消', inputType: 'textarea', inputPlaceholder: '可填写审核说明'
    })
    await api.post(`/sales-return/${row.id}/approve`, null, { params: { remark: remark || undefined } })
    ElMessage.success('退货单已审核通过，等待仓管入库')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

async function reject(row) {
  try {
    const { value: reason } = await ElMessageBox.prompt('驳回原因', `驳回退货单 ${row.docNo}`, {
      confirmButtonText: '确认驳回', cancelButtonText: '取消', inputType: 'textarea', inputPlaceholder: '请填写驳回原因'
    })
    if (!reason) { ElMessage.warning('请填写驳回原因'); return }
    await api.post(`/sales-return/${row.id}/reject`, null, { params: { reason } })
    ElMessage.success('退货单已驳回')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

// ===== 仓管退货入库 =====
function openInboundDialog(row) {
  current.value = row
  inboundForm.value = { warehouseId: '', zoneId: '', locationId: '' }
  zones.value = []
  locations.value = []
  inboundVisible.value = true
}

async function onWhChange(whId) {
  inboundForm.value.zoneId = ''
  inboundForm.value.locationId = ''
  zones.value = []
  locations.value = []
  if (!whId) return
  try { zones.value = await api.get(`/warehouse/${whId}/zone`) } catch {}
}

async function onZoneChange(zoneId) {
  inboundForm.value.locationId = ''
  locations.value = []
  if (!zoneId) return
  try { locations.value = await api.get(`/warehouse/zone/${zoneId}/location`) } catch {}
}

async function submitInbound() {
  if (!inboundForm.value.warehouseId || !inboundForm.value.zoneId || !inboundForm.value.locationId) {
    ElMessage.warning('请选择仓库/分库/库位'); return
  }
  loading.value = true
  try {
    await api.post(`/sales-return/${current.value.id}/inbound`, null, { params: {
      warehouseId: inboundForm.value.warehouseId,
      locationId: inboundForm.value.locationId
    }})
    ElMessage.success('退货入库完成，已增加库存并冲减应收账款')
    inboundVisible.value = false
    fetch()
  } catch (e) {
    if (e?.response?.data?.msg) ElMessage.error(e.response.data.msg)
  } finally { loading.value = false }
}

// ===== 分页（v5.2/v5.4）：超过 50 条自动分页展示 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(list)
// 已退货明细独立分页
const { page: donePage, pageSize: donePageSize, pagedRows: donePagedRows, resetPage: doneResetPage } = usePaging(doneList)

onMounted(async () => {
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch {}
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; gap: 8px; align-items: center; }
.btn-primary { background: #16a34a; border: none; color: #fff; border-radius: 6px; padding: 6px 16px; font-size: 13px; cursor: pointer; transition: all 0.2s; }
.btn-primary:hover { background: #15803d; }
.table-card { background: #fff; border-radius: 8px; padding: 16px; }
.type-tabs { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 12px 16px; background: #f8fafc; border-radius: 8px; flex-wrap: wrap; }
.filter-tabs { display: flex; gap: 8px; }
.filter-btn { background: #fff; border: 1px solid #d1d5db; color: #374151; border-radius: 6px; padding: 6px 16px; font-size: 13px; cursor: pointer; transition: all 0.2s; outline: none; }
.filter-btn:hover { border-color: #22c55e; color: #15803d; }
.filter-btn.active { background: #f0fdf4; border-color: #22c55e; color: #15803d; font-weight: 600; }
.type-count { font-size: 13px; color: #64748b; }
.flow-tip { font-size: 12px; color: #94a3b8; margin-left: auto; }
.text-muted { color: #94a3b8; font-size: 12px; }
.unit-text { margin-left: 8px; color: #64748b; font-size: 13px; }
.tip { font-size: 12px; color: #64748b; background: #f8fafc; border-radius: 6px; padding: 8px 12px; line-height: 1.6; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
