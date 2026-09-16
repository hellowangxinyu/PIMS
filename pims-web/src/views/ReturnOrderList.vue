<template>
  <div class="page-container">
    <div class="page-header">
      <h2>采购退货单</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">新增退货单</el-button>
        <el-select v-if="activeTab === 'HANDLE'" v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width:130px" @change="fetch">
          <el-option label="待审核" value="DRAFT" />
          <el-option label="待出库" value="APPROVED" />
          <el-option label="已退货完成" value="DONE" />
          <el-option label="已驳回" value="REJECTED" />
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
        <span v-if="activeTab === 'HANDLE'" class="flow-tip">两类退货：①来料质检退货（自动生成，货物未入库、无库存变更、不涉及应付）；②新增退货单（选择库存退货，审核通过后退货出库，扣库存并冲减应付）</span>
      </div>

      <!-- ===== Tab1：退货单处理 ===== -->
      <template v-if="activeTab === 'HANDLE'">
        <p-table :data="pagedRows" stripe border style="width:100%">
          <el-table-column prop="docNo" label="退货单号" width="150" show-overflow-tooltip />
          <el-table-column label="类型" width="90" align="center">
            <template #default="{ row }">{{ typeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="purchaseOrderNo" label="原采购单号" width="150" show-overflow-tooltip />
          <el-table-column prop="qcInspectionNo" label="质检单号" width="150" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="物料编码" min-width="130" />
          <el-table-column prop="batchNo" label="批号" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.batchNo || '-' }}</template>
          </el-table-column>
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
              <!-- v5.27：手工库存退货（无质检单号）审核通过后需退货出库（扣库存+冲减应付）；质检退货无库存直接完成 -->
              <template v-else-if="row.status === 'APPROVED' && !row.qcInspectionNo">
                <button class="op-btn op-btn-primary" @click="openOutbound(row)">退货出库</button>
              </template>
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

      <!-- ===== Tab2：已退货明细（v5.4，status=DONE 的退货记录） ===== -->
      <template v-else>
        <p-table :data="donePagedRows" stripe border style="width:100%">
          <el-table-column prop="docNo" label="退货单号" width="150" show-overflow-tooltip />
          <el-table-column prop="purchaseOrderNo" label="原采购单号" width="150" show-overflow-tooltip />
          <el-table-column prop="qcInspectionNo" label="质检单号" width="150" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="物料编码" min-width="130" />
          <el-table-column prop="materialName" label="品名" min-width="140" show-overflow-tooltip />
          <el-table-column prop="qty" label="退货数量" width="100" align="right" />
          <el-table-column prop="unitPrice" label="单价" width="90" align="right">
            <template #default="{ row }">{{ row.unitPrice != null ? Number(row.unitPrice).toFixed(2) : '-' }}</template>
          </el-table-column>
          <el-table-column prop="returnAmount" label="退货金额" width="110" align="right">
            <template #default="{ row }">{{ row.returnAmount != null ? Number(row.returnAmount).toFixed(2) : '-' }}</template>
          </el-table-column>
          <el-table-column prop="createdBy" label="制单人" width="90" />
          <el-table-column prop="approvedBy" label="审核人" width="90">
            <template #default="{ row }">{{ row.approvedBy || '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="退货完成时间" width="150">
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

    <!-- v5.27：手工新增采购退货单（参照到货单退货，自动带出物料/批号） -->
    <el-dialog title="新增采购退货单" v-model="createVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="参照到货单" required>
          <el-select v-model="createForm.arrivalId" filterable placeholder="选择有库存的到货单（仅合格入库且有库存的）" style="width:100%" @change="onArrivalChange">
            <el-option v-for="a in returnableArrivals" :key="a.arrivalId" :value="a.arrivalId"
              :label="`到货单#${a.arrivalId} ${a.materialName || ''}（${a.batchNo}，可退 ${a.stockQty}）`" />
          </el-select>
          <div v-if="!returnableArrivals.length" class="qty-hint">暂无有库存的到货单可参照退货</div>
        </el-form-item>
        <template v-if="preview">
          <el-form-item label="物料"><el-input :value="(preview.materialCode || '') + ' ' + (preview.materialName || '')" disabled /></el-form-item>
          <el-form-item label="供应商"><el-input :value="preview.supplierName || '-'" disabled /></el-form-item>
          <el-form-item label="原采购单"><el-input :value="preview.refOrderNo || '-'" disabled /></el-form-item>
          <el-form-item label="库存批号"><el-input :value="preview.batchNo || '未合格入库，不可退'" disabled /></el-form-item>
          <el-form-item label="批号库存"><el-input :value="preview.stockQty" disabled /></el-form-item>
          <el-form-item label="采购单价"><el-input :value="'￥' + Number(preview.unitPrice || 0).toFixed(2)" disabled /></el-form-item>
          <el-form-item label="退货数量" required>
            <el-input-number v-model="createForm.qty" :min="0.001" :max="Number(preview.stockQty) || 99999" :precision="3" :step="1" style="width:100%" />
            <div class="qty-hint">不能超过批号库存 {{ preview.stockQty }}；金额按采购单价自动计算</div>
          </el-form-item>
          <el-form-item label="退货金额">
            <el-input :value="(createForm.qty * Number(preview.unitPrice || 0)).toFixed(2)" disabled />
          </el-form-item>
          <el-form-item label="备注"><el-input v-model="createForm.remark" type="textarea" :rows="2" /></el-form-item>
        </template>
        <div v-else class="qty-hint">请先选择到货单</div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="loading" :disabled="!preview || !preview.batchNo">保存退货单</el-button>
      </template>
    </el-dialog>

    <!-- v5.27：退货出库（批号与仓库均由退货单自动带出，只需确认） -->
    <el-dialog :title="'退货出库 ' + (outboundRow?.docNo || '')" v-model="outboundVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="outboundForm" label-width="100px">
        <el-form-item label="物料"><el-input :value="(outboundRow?.materialCode || '') + ' ' + (outboundRow?.materialName || '')" disabled /></el-form-item>
        <el-form-item label="批号"><el-input :value="outboundRow?.batchNo || '-'" disabled /></el-form-item>
        <el-form-item label="出库仓库"><el-input :value="outboundWh || '自动定位…'" disabled /></el-form-item>
        <el-form-item label="退货数量"><el-input :value="outboundRow?.qty" disabled /></el-form-item>
        <div class="qty-hint">批号与仓库由退货单自动带出：扣减库存，并按退货金额冲减该供应商应付账款</div>
      </el-form>
      <template #footer>
        <el-button @click="outboundVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOutbound" :loading="loading">确认退货出库</el-button>
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

// v5.27：手工新增退货单（参照到货单退货，仅列有库存的到货单）
const createVisible = ref(false)
const loading = ref(false)
const returnableArrivals = ref([])
const preview = ref(null)
const createForm = ref({ arrivalId: null, qty: 1, remark: '' })
// v5.27：退货出库（批号与仓库由退货单自动带出，只需确认）
const outboundVisible = ref(false)
const outboundRow = ref(null)
const warehouses = ref([])
const outboundWh = ref('') // 出库仓库名（打开弹窗时按锁定批号自动带出）
const outboundForm = ref({})
function whName(id) {
  if (!id) return '-'
  const w = warehouses.value.find(w => String(w.id) === String(id))
  return w ? w.name : id
}

function fmtTime(t) { return t ? String(t).replace('T', ' ').substring(0, 16) : '' }
function typeLabel(t) { return { PURCHASE_RETURN: '采购退货' }[t] || '未知' }
function statusLabel(s) { return { DRAFT: '待审核', APPROVED: '待出库', DONE: '已退货完成', REJECTED: '已驳回' }[s] || '未知' }
function statusTagType(s) { return { DRAFT: 'warning', APPROVED: 'primary', DONE: 'success', REJECTED: 'danger' }[s] || 'info' }

// ===== v5.27：新增退货单（参照到货单，仅列有库存的） =====
async function openCreate() {
  createForm.value = { arrivalId: null, qty: 1, remark: '' }
  preview.value = null
  createVisible.value = true
  try {
    if (!returnableArrivals.value.length) {
      returnableArrivals.value = await api.get('/return-order/returnable-arrivals')
    }
  } catch {}
}
async function onArrivalChange(arrivalId) {
  preview.value = null
  if (!arrivalId) return
  const a = returnableArrivals.value.find(x => x.arrivalId === arrivalId)
  if (!a) return
  // v5.27：列表已含批号/可退量/单价，直接带出（无库存的到货单不会出现在列表中）
  preview.value = a
  createForm.value.qty = Number(a.stockQty) || 1
}
async function submitCreate() {
  if (!createForm.value.arrivalId) { ElMessage.warning('请选择参照到货单'); return }
  if (!preview.value?.batchNo) { ElMessage.warning('该到货单尚未合格入库，无库存批号可退'); return }
  if (!createForm.value.qty || createForm.value.qty <= 0) { ElMessage.warning('请填写退货数量'); return }
  if (createForm.value.qty > Number(preview.value.stockQty)) { ElMessage.warning(`退货数量不能超过批号库存 ${preview.value.stockQty}`); return }
  loading.value = true
  try {
    await api.post('/return-order', null, {
      params: { arrivalId: createForm.value.arrivalId, qty: createForm.value.qty, remark: createForm.value.remark || undefined }
    })
    ElMessage.success('退货单已创建（待审核）；审核通过后退货出库时按锁定批号扣库存并冲减应付')
    createVisible.value = false
    fetch()
  } catch (e) {
    if (e?.response?.data?.msg) ElMessage.error(e.response.data.msg)
  } finally { loading.value = false }
}

// ===== v5.27：退货出库（批号与仓库自动带出） =====
async function openOutbound(row) {
  outboundRow.value = row
  outboundWh.value = ''
  outboundVisible.value = true
  try {
    if (!warehouses.value.length) warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false)
    // v5.27：按退货单锁定的批号自动带出仓库（该批号库存最大的仓）
    if (row.refArrivalId) {
      const pv = await api.get('/return-order/arrival-preview', { params: { arrivalId: row.refArrivalId } })
      outboundWh.value = whName(pv.warehouseId)
    }
  } catch {}
}
async function submitOutbound() {
  const row = outboundRow.value
  loading.value = true
  try {
    // v5.27：仓库不传，后端按锁定批号自动定位
    await api.post('/outbound/other-outbound/from-return-order', null, {
      params: { returnOrderId: row.id, batchNo: row.batchNo }
    })
    ElMessage.success('退货出库完成，库存已扣减并冲减应付')
    outboundVisible.value = false
    fetch()
  } catch (e) {
    if (e?.response?.data?.msg) ElMessage.error(e.response.data.msg)
  } finally { loading.value = false }
}

// Tab 切换（v5.4：已退货明细进入时加载数据）
function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'DONE') fetchDone()
  else fetch()
}

async function fetch() {
  resetPage()
  try {
    const params = { type: 'PURCHASE_RETURN' }
    if (filterStatus.value) params.status = filterStatus.value
    const all = await api.get('/return-order', { params })
    // v5.97：已完成退货只在「已退货明细」tab 显示，处理 tab 默认排除（状态下拉主动选 DONE 除外）
    list.value = filterStatus.value === 'DONE' ? all : (all || []).filter(r => r.status !== 'DONE')
  } catch {}
}

// 已退货明细（status=DONE，独立分页）
async function fetchDone() {
  doneResetPage()
  try {
    doneList.value = await api.get('/return-order', { params: { type: 'PURCHASE_RETURN', status: 'DONE' } })
  } catch {}
}

// 采购员审核：v5.5 审核通过即退货完成（货物未入库，直接退回供应商，自动冲减应付）
async function approve(row) {
  try {
    const { value: remark } = await ElMessageBox.prompt('审核备注（可选）', `审核退货单 ${row.docNo}`, {
      confirmButtonText: '审核通过', cancelButtonText: '取消', inputType: 'textarea', inputPlaceholder: '可填写审核说明'
    })
    await api.post(`/return-order/${row.id}/approve`, null, { params: { remark: remark || undefined } })
    ElMessage.success('退货单已审核通过，退货完成（货物直接退回供应商，已冲减应付账款）')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

// 采购员驳回
async function reject(row) {
  try {
    const { value: reason } = await ElMessageBox.prompt('驳回原因', `驳回退货单 ${row.docNo}`, {
      confirmButtonText: '确认驳回', cancelButtonText: '取消', inputType: 'textarea', inputPlaceholder: '请填写驳回原因'
    })
    if (!reason) { ElMessage.warning('请填写驳回原因'); return }
    await api.post(`/return-order/${row.id}/reject`, null, { params: { reason } })
    ElMessage.success('退货单已驳回')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(list)
// v5.4：已退货明细独立分页
const { page: donePage, pageSize: donePageSize, pagedRows: donePagedRows, resetPage: doneResetPage } = usePaging(doneList)

onMounted(() => { fetch() })
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; gap: 8px; }
.table-card { background: #fff; border-radius: 8px; padding: 16px; }
.type-tabs { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 12px 16px; background: #f8fafc; border-radius: 8px; flex-wrap: wrap; }
.filter-tabs { display: flex; gap: 8px; }
.filter-btn { background: #fff; border: 1px solid #d1d5db; color: #374151; border-radius: 6px; padding: 6px 16px; font-size: 13px; cursor: pointer; transition: all 0.2s; outline: none; }
.filter-btn:hover { border-color: #7fa07f; color: #15803d; }
.filter-btn.active { background: #f0f5f0; border-color: #7fa07f; color: #15803d; font-weight: 600; }
.type-count { font-size: 13px; color: #64748b; }
.flow-tip { font-size: 12px; color: #94a3b8; margin-left: auto; }
.text-muted { color: #94a3b8; font-size: 12px; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
