<template>
  <div class="page-container">
    <div class="page-header">
      <h2>到货录入</h2>
      <div class="header-actions">
        <el-button @click="doExport" :loading="exporting">导出 Excel</el-button>
      </div>
    </div>

    <div class="table-card">
      <!-- 类型筛选标签页（v5.2：新增「到货明细」） -->
      <div class="type-tabs">
        <div class="filter-tabs">
          <button 
            :class="['filter-btn', { active: activeTab === 'RAW' }]" 
            @click="switchTab('RAW')"
          >原料到货</button>
          <button 
            :class="['filter-btn', { active: activeTab === 'FINISHED' }]" 
            @click="switchTab('FINISHED')"
          >成品到货</button>
          <button 
            :class="['filter-btn', { active: activeTab === 'DETAIL' }]" 
            @click="switchTab('DETAIL')"
          >到货明细</button>
        </div>
        <span class="type-count">{{ activeTab === 'DETAIL' ? `共 ${detailTotal} 条到货记录` : `共 ${list.length} 条待到货` }}</span>
      </div>

      <!-- ===== 待到货列表（原料/成品） ===== -->
      <template v-if="activeTab !== 'DETAIL'">
      <p-table :data="pagedRows" stripe border @header-dragend="onHeaderDragend">
        <el-table-column prop="orderNo" label="合同号" :width="cw('合同号') || undefined" min-width="140" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" :width="cw('供应商') || undefined" min-width="120" show-overflow-tooltip />
        <el-table-column prop="materialName" label="品名" :width="cw('品名') || undefined" min-width="150" show-overflow-tooltip />
        <el-table-column prop="brand" label="牌号" :width="cw('牌号') || 100" />
        <el-table-column label="采购数量" :width="cw('采购数量') || 100" align="right">
          <template #default="{row}">{{ row.qty }}</template>
        </el-table-column>
        <el-table-column label="已到货" :width="cw('已到货') || 100" align="right">
          <template #default="{row}">
            <span :class="{ 'text-success': row.receivedQty >= row.qty }">{{ row.receivedQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="未到货" :width="cw('未到货') || 100" align="right">
          <template #default="{row}">
            <span class="text-warning">{{ (row.qty - row.receivedQty).toFixed(3) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" :width="cw('状态') || 90" align="center">
          <template #default="{row}">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="{row}">
            <button class="op-btn op-btn-primary" @click="showArrivalDialog(row)" v-if="row.status === 'APPROVED'">录入到货</button>
            <button class="op-btn op-btn-danger" @click="closeOrder(row)" v-if="row.status === 'APPROVED'">结束订单</button>
            <span v-if="row.status === 'CLOSED'" class="text-muted">已关闭</span>
            <span v-if="row.status === 'RECEIVED'" class="text-success">已完成</span>
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

      <!-- ===== 到货明细（v5.2：原料/成品区分） ===== -->
      <template v-else>
        <div class="filter-tabs detail-sub-tabs">
          <button 
            :class="['filter-btn', { active: detailType === 'RAW' }]" 
            @click="switchDetailType('RAW')"
          >原材料明细</button>
          <button 
            :class="['filter-btn', { active: detailType === 'FINISHED' }]" 
            @click="switchDetailType('FINISHED')"
          >成品明细</button>
          <el-input v-model="detailSearch" placeholder="搜索合同号/编码/品名/供应商" clearable size="small" style="width:240px;margin-left:auto" @keyup.enter="onDetailSearch" @clear="onDetailSearch" />
          <!-- v5.27：选中到货明细打印 8×10 标签 -->
          <el-button size="small" type="primary" :disabled="!selectedDetailRows.length" @click="printLabelsWithQc(selectedDetailRows)">打印标签（{{ selectedDetailRows.length }}）</el-button>
        </div>
        <p-table :data="detailRows" stripe border @selection-change="sel => selectedDetailRows = sel">
          <el-table-column type="selection" width="40" />
          <el-table-column prop="docNo" label="到货单号" :width="cw('到货单号') || undefined" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.docNo || '—' }}</template>
          </el-table-column>
          <el-table-column prop="arrivalDate" label="到货日期" :width="cw('到货日期') || undefined" min-width="110" />
          <el-table-column prop="refOrderNo" label="合同号" :width="cw('合同号') || undefined" min-width="140" show-overflow-tooltip />
          <el-table-column prop="supplierName" label="供应商" :width="cw('供应商') || undefined" min-width="120" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="编码" :width="cw('编码') || undefined" min-width="105" show-overflow-tooltip />
          <el-table-column prop="materialName" label="品名" :width="cw('品名') || undefined" min-width="140" show-overflow-tooltip />
          <el-table-column prop="batchNo" label="批号" :width="cw('批号') || undefined" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.batchNo || '—' }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="到货数量" width="100" align="right" />
          <el-table-column label="单价(含税)" width="100" align="right">
            <template #default="{ row }">{{ fmtTax(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="不含税单价" width="100" align="right">
            <template #default="{ row }">{{ fmtTax(netOfTax(row.unitPrice, row.taxRate ?? taxRate)) }}</template>
          </el-table-column>
          <el-table-column label="税额" width="85" align="right">
            <template #default="{ row }">{{ fmtTax(taxOf(row.unitPrice, row.taxRate ?? taxRate)) }}</template>
          </el-table-column>
          <el-table-column label="税率" width="65" align="center">
            <template #default="{ row }">{{ (row.taxRate ?? taxRate) }}%</template>
          </el-table-column>
          <el-table-column label="仓库" :width="cw('仓库') || undefined" min-width="110">
            <template #default="{row}">{{ whName(row.warehouseId) }}</template>
          </el-table-column>
          <el-table-column prop="zoneName" label="分库" :width="cw('分库') || undefined" min-width="90" />
          <el-table-column prop="locationName" label="库位" :width="cw('库位') || undefined" min-width="90" />
          <el-table-column prop="operator" label="操作人" width="90" />
        </p-table>
        <!-- 分页（v5.2） -->
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="detailPage"
            v-model:page-size="detailPageSize"
            :page-sizes="[25, 50, 100]"
            :total="detailTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="fetchDetails"
            @size-change="onDetailSearch"
          />
        </div>
      </template>
    </div>

    <!-- 录入到货弹窗 -->
    <el-dialog title="录入到货" v-model="arrivalVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="arrivalForm" label-width="90px">
        <el-form-item label="合同号">
          <el-input :value="arrivalForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="品名">
          <el-input :value="arrivalForm.materialName" disabled />
        </el-form-item>
        <el-form-item label="采购数量">
          <el-input :value="arrivalForm.qty" disabled />
        </el-form-item>
        <el-form-item label="已到货">
          <el-input :value="arrivalForm.receivedQty" disabled />
        </el-form-item>
        <el-form-item label="本次到货" required>
          <el-input-number
            v-model="arrivalForm.arrivalQty"
            :min="0.001"
            :max="arrivalForm.qty - arrivalForm.receivedQty"
            :precision="3"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <!-- v5.35：批号到货时自动生成（B+日期+序号，全局唯一），不允许手工干预；质检单与入库台账沿用同一批号 -->
        <el-form-item label="批号">
          <div class="arrival-batch-tip">确认到货时自动生成，质检单与入库台账沿用</div>
        </el-form-item>
        <el-form-item label="入库仓库" required>
          <el-select v-model="arrivalForm.warehouseId" placeholder="请选择仓库" style="width: 100%" @change="onWarehouseChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="分库" required>
          <el-select v-model="arrivalForm.zoneId" placeholder="请先选择仓库" style="width: 100%" :disabled="!arrivalForm.warehouseId" @change="onZoneChange">
            <el-option v-for="z in zones" :key="z.id" :label="z.name" :value="String(z.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="库位" required>
          <el-select v-model="arrivalForm.locationId" placeholder="请先选择分库" style="width: 100%" :disabled="!arrivalForm.zoneId">
            <el-option v-for="loc in locations" :key="loc.id" :label="loc.name" :value="String(loc.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="arrivalVisible = false">取消</el-button>
        <el-button type="primary" @click="submitArrival" :loading="submitting">确认到货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { statusType as globalStatusType } from '../utils/statusTag'
import { ref, computed, onMounted, watch } from 'vue'
import { loadTaxRate, netOfTax, taxOf, fmtTax } from '../utils/tax'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'
import { useColumnResize } from '../composables/useColumnResize'
import { printLabels } from '../utils/labelPrint'
import { useBucketPrint } from '../composables/useBucketPrint'
// v9.6 导出当前筛选（下载工具绕过 JSON 拦截器）
import { downloadFile } from '../utils/download'
const exporting = ref(false)
async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/purchase-arrival/export', { type: detailType.value || undefined, keyword: detailSearch.value.trim() || undefined }, `采购到货明细-${new Date().toLocaleDateString('sv')}.xlsx`)
  } finally { exporting.value = false }
}


// v5.79.1 打印标签前带出质检结果/检验员：批号精确优先（模糊 LIKE 会撞前缀批号），
// 多条时优先取已判定的（PASS/CONCESSION/REJECT），查不到再按 合同号+批号 兜底
async function pickQc(row) {
  try {
    const res = await api.get('/qc/search', { params: { batchNo: row.batchNo, page: 0, size: 20 } })
    let content = res.content || []
    // 精确批号优先；无精确再看是否只有一条模糊结果
    let exact = content.filter(q => q.batchNo === row.batchNo)
    if (!exact.length && content.length === 1) exact = content
    if (!exact.length) {
      // 兜底：按合同号关联单再筛同物料
      const r2 = await api.get('/qc/search', { params: { refDocNo: row.refOrderNo, page: 0, size: 20 } })
      exact = (r2.content || []).filter(q => (q.batchNo || '') === (row.batchNo || '') || q.materialCode === row.materialCode)
    }
    if (!exact.length) return null
    // 已判定的优先（复检 PENDING 单不算），同优先级取最新检验日期
    const judged = exact.filter(q => q.status && q.status !== 'PENDING')
    const pool = judged.length ? judged : exact
    pool.sort((a, b) => String(b.inspectDate || '').localeCompare(String(a.inspectDate || '')))
    return pool[0]
  } catch { return null }
}

async function printLabelsWithQc(rows) {
  // v7.3：质检带出在前 → 分桶对话框在后 → 打印（顺序不可倒：分桶不能跳过 QC 回填）
  const list = rows.map(r => ({ ...r }))
  for (const row of list) {
    const q = await pickQc(row)
    if (q) { row.qcStatus = q.status; row.qcInspector = q.inspector }
  }
  await printWithBuckets(list)
}

const { cw, onHeaderDragend } = useColumnResize('purchase_arrival')
const { printWithBuckets } = useBucketPrint()

const taxRate = ref(13)
const list = ref([])
const type = ref('RAW')
const warehouses = ref([])
const zones = ref([])
const locations = ref([])
const arrivalVisible = ref(false)
const arrivalForm = ref({})
const submitting = ref(false)
const perms = ref([])

// ===== v5.2：到货明细 Tab =====
const activeTab = ref('RAW')       // RAW / FINISHED / DETAIL
const detailType = ref('RAW')      // 明细内部分类
const detailRows = ref([])
const detailTotal = ref(0)
// v5.27：多选到货明细（打印标签用）
const selectedDetailRows = ref([])

// v5.9：到货明细后端分页（合同号/编码/品名/供应商关键字传后端）
const detailSearch = ref('')
const detailPage = ref(1)
const detailPageSize = ref(50)

function switchDetailType(t) {
  detailType.value = t
  detailSearch.value = ''
  fetchDetails()
}

function hasPerm(c) { return perms.value.includes(c) }

// 仓库 ID → 名称
function whName(id) {
  const w = warehouses.value.find(x => String(x.id) === String(id))
  return w ? w.name : (id || '-')
}

// 顶层 Tab 切换：到货明细进入时加载数据
function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'DETAIL') fetchDetails()
  else fetch()
}

// 加载到货明细（按类型 + 关键字，v5.9 后端分页）
async function fetchDetails() {
  const params = { type: detailType.value, page: detailPage.value, pageSize: detailPageSize.value }
  if (detailSearch.value.trim()) params.keyword = detailSearch.value.trim()
  try {
    const res = await api.get('/purchase-arrival', { params })
    detailRows.value = res.rows
    detailTotal.value = res.total
  } catch (e) {
    ElMessage.error('获取到货明细失败')
  }
}
function onDetailSearch() { detailPage.value = 1; fetchDetails() }

// 状态标签类型
// v6.6 收口：全局映射（CLOSED 归档灰，原 danger 红易误读为异常）
const statusTagType = globalStatusType;

// 状态标签文本
function statusLabel(status) {
  const map = { 'DRAFT': '开立', 'APPROVED': '已审核', 'RECEIVED': '已完成', 'CLOSED': '已关闭' }
  return map[status] || '未知'
}

// 获取未完全到货的订单
async function fetch() {
  resetPage()
  try {
    list.value = await api.get('/purchase/incomplete', { params: { type: type.value } })
  } catch (e) {
    ElMessage.error('获取数据失败')
  }
}

// 获取仓库列表
async function fetchWarehouses() {
  try {
    warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false)
  } catch (e) {
    console.error('获取仓库列表失败', e)
  }
}

// 显示录入到货弹窗
function showArrivalDialog(row) {
  arrivalForm.value = {
    id: row.id,
    orderNo: row.orderNo,
    materialName: row.materialName,
    qty: row.qty,
    receivedQty: row.receivedQty,
    arrivalQty: row.qty - row.receivedQty,
    warehouseId: row.warehouseId || '', // v5.34：采购单带目标仓库时默认带出（发往不同仓库）
    zoneId: '',
    locationId: ''
  }
  zones.value = []
  locations.value = []
  if (arrivalForm.value.warehouseId) onWarehouseChange(arrivalForm.value.warehouseId)
  arrivalVisible.value = true
}

// 仓库切换→加载分库
async function onWarehouseChange(val) {
  arrivalForm.value.zoneId = ''
  arrivalForm.value.locationId = ''
  zones.value = []
  locations.value = []
  if (!val) return
  try {
    const all = await api.get(`/warehouse/${val}/zone`)
    // v5.71.7：过滤隔离分库（不合格品/油尾）——到货是正常入库，隔离库只收质检不合格/过期隔离
    zones.value = (all || []).filter(z => {
      const t = z.zoneType || z.type || ''
      return !t.startsWith('UNQUALIFIED') && !t.startsWith('TAILING')
    })
  } catch (e) { console.error('获取分库失败', e) }
}

// 分库切换→加载库位
async function onZoneChange(val) {
  arrivalForm.value.locationId = ''
  locations.value = []
  if (!val) return
  try {
    locations.value = await api.get(`/warehouse/zone/${val}/location`)
  } catch (e) { console.error('获取库位失败', e) }
}

// 提交到货录入
async function submitArrival() {
  if (!arrivalForm.value.warehouseId) {
    ElMessage.warning('请选择入库仓库')
    return
  }
  if (!arrivalForm.value.zoneId) {
    ElMessage.warning('请选择分库')
    return
  }
  if (!arrivalForm.value.locationId) {
    ElMessage.warning('请选择库位')
    return
  }
  if (arrivalForm.value.arrivalQty <= 0) {
    ElMessage.warning('请输入有效的到货数量')
    return
  }

  submitting.value = true
  try {
    await api.post('/purchase/arrival/record', {
      id: arrivalForm.value.id,
      type: type.value,
      arrivalQty: arrivalForm.value.arrivalQty,
      warehouseId: arrivalForm.value.warehouseId,
      locationId: arrivalForm.value.locationId
    })
    ElMessage.success('到货录入成功')
    arrivalVisible.value = false
    fetch()
    fetchDetails()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '到货录入失败')
  } finally {
    submitting.value = false
  }
}

// 手动结束订单
async function closeOrder(row) {
  try {
    await ElMessageBox.confirm(
      `确定要结束订单 ${row.orderNo} 吗？\n\n采购数量：${row.qty}\n已到货：${row.receivedQty}\n未到货：${(row.qty - row.receivedQty).toFixed(3)}\n\n结束后将无法继续录入到货。`,
      '结束订单确认',
      { type: 'warning' }
    )
    // v6.8：短量关闭必须填原因（留痕进订单备注）
    let reason = null
    const shortfall = Number(row.qty) - Number(row.receivedQty || 0)
    if (shortfall > 0.0001) {
      const { value } = await ElMessageBox.prompt(
        `本单尚有 ${shortfall.toFixed(3)} 未到货，请填写短量原因（将记入订单备注）：`,
        '短量关闭原因', { inputValue: '供应商短量交货，不再补货' })
      reason = value
    }
    await api.post(`/purchase/close/${row.id}`, null, { params: { type: type.value, reason: reason || undefined } })
    ElMessage.success('订单已关闭')
    fetch()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e.response?.data?.msg || '操作失败')
    }
  }
}

// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(list)
// 到货明细独立分页（v5.7：基于搜索过滤结果，搜索变化回到第一页）

onMounted(async () => {
  loadTaxRate(api).then(r => { taxRate.value = r })
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  await fetchWarehouses()
  await fetch()
})
</script>

<style scoped>
.page-container {
  width: 100%;
}

.type-tabs {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
}

/* v5.35：批号自动生成提示（不再手工录入） */
.arrival-batch-tip {
  width: 100%;
  height: 32px;
  line-height: 32px;
  padding: 0 12px;
  font-size: 13px;
  color: #94a3b8;
  background: #f8fafc;
  border: 1px dashed #d1d5db;
  border-radius: 6px;
}

/* 筛选标签按钮样式 */
.filter-tabs {
  display: flex;
  gap: 8px;
}

.filter-btn {
  background: #fff;
  border: 1px solid #d1d5db;
  color: #374151;
  border-radius: 6px;
  padding: 6px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;  outline: none;
}

.filter-btn:hover {
  border-color: #22c55e;
  color: #15803d;
}

.filter-btn.active {
  background: #f0fdf4;
  border-color: #22c55e;
  color: #15803d;  font-weight: 600;
}

.type-count {
  font-size: 13px;
  color: #64748b;
  margin-left: auto;
}

/* v5.27：打印标签按钮 */

/* v5.2：到货明细子 Tab 与主 Tab 保持间距 */
.detail-sub-tabs {
  margin-bottom: 12px;
}

.text-success {
  color: #67c23a;
  font-weight: 500;
}

.text-warning {
  color: #e6a23c;
  font-weight: 500;
}

.text-muted {
  color: #94a3b8;
}

/* 操作按钮样式 */

</style>
