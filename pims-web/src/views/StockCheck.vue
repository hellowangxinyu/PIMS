<template>
  <div class="page-container">
    <div class="page-header">
      <h2>盘库管理</h2>
      <div class="header-actions">
        <el-button type="success" @click="openGainDialog">盘盈</el-button>
        <el-button type="danger" @click="openLossDialog">盘亏</el-button>
        <el-button type="primary" @click="openAdjustDialog">库位调整</el-button>
      </div>
    </div>

    <!-- 类型筛选 -->
    <div class="type-tabs">
      <div class="filter-tabs">
        <button :class="['filter-btn', { active: filterType === '' }]" @click="filterType = ''; fetchList()">全部</button>
        <button :class="['filter-btn', { active: filterType === 'STOCK_GAIN' }]" @click="filterType = 'STOCK_GAIN'; fetchList()">盘盈</button>
        <button :class="['filter-btn', { active: filterType === 'STOCK_LOSS' }]" @click="filterType = 'STOCK_LOSS'; fetchList()">盘亏</button>
        <button :class="['filter-btn', { active: filterType === 'LOCATION_ADJUST' }]" @click="filterType = 'LOCATION_ADJUST'; fetchList()">库位调整</button>
      </div>
      <span class="type-count">共 {{ records.length }} 条记录</span>
    </div>

    <!-- 记录列表 -->
    <div class="table-card">
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单据号" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.docType)" size="small">{{ typeLabel(row.docType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="materialCode" label="物料编码" min-width="130" />
        <el-table-column prop="materialName" label="品名" min-width="130" show-overflow-tooltip />
        <el-table-column label="仓库" width="110">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column label="数量信息" min-width="160">
          <template #default="{ row }">
            <template v-if="row.docType === 'LOCATION_ADJUST'">
              {{ row.fromLocationName || '—' }} → {{ row.toLocationName || '—' }}（{{ row.adjustQty }}）
            </template>
            <template v-else>
              系统 {{ row.systemQty }} / 实盘 {{ row.actualQty }} / 差异
              <span :style="{ color: row.docType === 'STOCK_GAIN' ? '#16a34a' : '#a85d50', fontWeight: 600 }">
                {{ row.docType === 'STOCK_GAIN' ? '+' : '' }}{{ row.diffQty }}
              </span>
            </template>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </p-table>
      <!-- 分页（v5.2） -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]"
          :total="records.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>

    <!-- 盘盈/盘亏弹窗 -->
    <el-dialog :title="dialogType === 'gain' ? '盘盈' : '盘亏'" v-model="glVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="glForm" label-width="100px">
        <el-form-item label="仓库" required>
          <el-select v-model="glForm.warehouseId" placeholder="选择仓库" style="width:100%" @change="onGlWarehouseChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料" required>
          <el-select v-model="glForm.materialCode" filterable placeholder="搜索选择物料" style="width:100%" @change="onGlMaterialChange">
            <el-option v-for="m in inventoryOfWarehouse" :key="m.materialCode + '-' + (m.locationId||'')"
              :label="m.materialCode + ' ' + (m.materialName||'')" :value="m.materialCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="品名">
          <el-input :value="glForm.materialName" disabled />
        </el-form-item>
        <el-form-item label="系统数量">
          <el-input :value="glForm.systemQty" disabled />
        </el-form-item>
        <el-form-item label="实盘数量" required>
          <el-input-number v-model="glForm.actualQty" :min="0" :precision="3" :step="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="glForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="glVisible = false">取消</el-button>
        <el-button type="primary" @click="submitGl" :loading="submitting">确认</el-button>
      </template>
    </el-dialog>

    <!-- 库位调整弹窗 -->
    <el-dialog title="库位调整" v-model="adjVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="adjForm" label-width="100px">
        <el-form-item label="仓库" required>
          <el-select v-model="adjForm.warehouseId" placeholder="选择仓库" style="width:100%" @change="onAdjWarehouseChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料/批次" required>
          <el-select v-model="adjForm.matKey" filterable placeholder="搜索选择物料（含批次/库位，仅库存>0）" style="width:100%" @change="onAdjMaterialChange">
            <el-option v-for="m in adjMaterials" :key="m.materialCode + '##' + (m.batchNo||'') + '##' + (m.locationId||'')"
              :label="m.materialCode + ' ' + (m.materialName||'') + (m.batchNo ? ' 批次:' + m.batchNo : '') + '（' + (m.locationName || '未分配库位') + '）'"
              :value="m.materialCode + '##' + (m.batchNo||'') + '##' + (m.locationId||'')" />
          </el-select>
        </el-form-item>
        <el-form-item label="品名">
          <el-input :value="adjForm.materialName" disabled />
        </el-form-item>
        <el-form-item label="当前批次">
          <el-input :value="adjForm.batchNo || '（无批次）'" disabled />
        </el-form-item>
        <el-form-item label="当前库位">
          <el-input :value="adjForm.fromLocationName" disabled />
        </el-form-item>
        <el-form-item label="当前库存">
          <el-input :value="adjForm.currentQty" disabled />
        </el-form-item>
        <el-form-item label="目标仓库">
          <el-select v-model="adjForm.toWarehouseId" clearable placeholder="留空=同仓库内调库位" style="width:100%" @change="onAdjToWarehouseChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标库位" required>
          <el-select v-model="adjForm.toLocationId" placeholder="选择目标库位" style="width:100%">
            <el-option v-for="loc in locations" :key="loc.id" :label="loc.name" :value="String(loc.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整数量" required>
          <el-input-number v-model="adjForm.qty" :min="0.001" :max="adjForm.currentQty || 99999" :precision="3" :step="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdj" :loading="submitting">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const records = ref([])
const warehouses = ref([])
const locations = ref([])
const filterType = ref('')
const submitting = ref(false)

// 盘盈/盘亏
const glVisible = ref(false)
const dialogType = ref('gain')
const glForm = ref({ warehouseId: '', materialCode: '', materialName: '', systemQty: 0, actualQty: 0, remark: '' })

// 库位调整
const adjVisible = ref(false)
const adjForm = ref({ warehouseId: '', matKey: '', materialCode: '', materialName: '', batchNo: '', fromLocationId: '', fromLocationName: '', currentQty: 0, toWarehouseId: '', toLocationId: '', qty: 1, remark: '' })

// v5.18 修复：/inventory 已改远程分页（返回 {rows,total}），不再整表拉取。
// 改为选中仓库后按仓库拉取该仓全部库存（pageSize 兜底），避免物料下拉空。
const inventoryOfWarehouse = ref([])
// 库位调整只列有库存的（qty>0）；盘盈/盘亏仍用全量（盘盈需看到 0 库存物料）
const adjMaterials = computed(() => inventoryOfWarehouse.value.filter(m => Number(m.qty) > 0))
async function loadInventoryOfWarehouse() {
  const whId = glVisible.value ? glForm.value.warehouseId : adjForm.value.warehouseId
  inventoryOfWarehouse.value = []
  if (!whId) return
  try {
    const res = await api.get('/inventory', { params: { warehouseId: whId, pageSize: 5000 } })
    // 防竞态：拉取期间仓库又切换了则丢弃
    const cur = glVisible.value ? glForm.value.warehouseId : adjForm.value.warehouseId
    if (cur === whId) inventoryOfWarehouse.value = res.rows || []
  } catch (e) { /* ignore */ }
}

function typeLabel(t) {
  return { STOCK_GAIN: '盘盈', STOCK_LOSS: '盘亏', LOCATION_ADJUST: '库位调整' }[t] || '未知'
}
function typeTag(t) {
  return { STOCK_GAIN: 'success', STOCK_LOSS: 'danger', LOCATION_ADJUST: 'warning' }[t] || 'info'
}
function warehouseName(id) {
  const w = warehouses.value.find(w => String(w.id) === String(id))
  return w ? w.name : id
}
function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

async function fetchList() {
  resetPage()
  try {
    const params = filterType.value ? { docType: filterType.value } : {}
    records.value = await api.get('/stock-check', { params })
  } catch (e) { /* ignore */ }
}

async function fetchLocations(warehouseId) {
  locations.value = []
  if (!warehouseId) return
  try {
    const zones = await api.get(`/warehouse/${warehouseId}/zone`)
    for (const z of zones) {
      const locs = await api.get(`/warehouse/zone/${z.id}/location`)
      locations.value.push(...locs)
    }
  } catch (e) { /* ignore */ }
}

// ===== 盘盈/盘亏 =====
function openGainDialog() {
  dialogType.value = 'gain'
  glForm.value = { warehouseId: '', materialCode: '', materialName: '', systemQty: 0, actualQty: 0, remark: '' }
  glVisible.value = true
}
function openLossDialog() {
  dialogType.value = 'loss'
  glForm.value = { warehouseId: '', materialCode: '', materialName: '', systemQty: 0, actualQty: 0, remark: '' }
  glVisible.value = true
}
function onGlWarehouseChange() {
  glForm.value.materialCode = ''
  glForm.value.materialName = ''
  glForm.value.systemQty = 0
  loadInventoryOfWarehouse()
}
function onGlMaterialChange(code) {
  const row = inventoryOfWarehouse.value.find(r => r.materialCode === code)
  if (row) {
    glForm.value.materialName = row.materialName || ''
    glForm.value.systemQty = Number(row.qty) || 0
    glForm.value.actualQty = Number(row.qty) || 0
  }
}

async function submitGl() {
  const f = glForm.value
  if (!f.warehouseId || !f.materialCode) { ElMessage.warning('请选择仓库和物料'); return }
  if (dialogType.value === 'gain' && f.actualQty <= f.systemQty) { ElMessage.warning('盘盈时实盘数量必须大于系统数量'); return }
  if (dialogType.value === 'loss' && f.actualQty >= f.systemQty) { ElMessage.warning('盘亏时实盘数量必须小于系统数量'); return }
  submitting.value = true
  try {
    const url = dialogType.value === 'gain' ? '/stock-check/gain' : '/stock-check/loss'
    await api.post(url, null, {
      params: {
        materialCode: f.materialCode,
        materialName: f.materialName,
        warehouseId: f.warehouseId,
        actualQty: f.actualQty,
        remark: f.remark || undefined
      }
    })
    ElMessage.success(dialogType.value === 'gain' ? '盘盈处理成功' : '盘亏处理成功')
    glVisible.value = false
    fetchList()
    loadInventoryOfWarehouse()
  } catch (e) { /* handled */ } finally { submitting.value = false }
}

// ===== 库位调整 =====
function openAdjustDialog() {
  adjForm.value = { warehouseId: '', matKey: '', materialCode: '', materialName: '', batchNo: '', fromLocationId: '', fromLocationName: '', currentQty: 0, toWarehouseId: '', toLocationId: '', qty: 1, remark: '' }
  locations.value = []
  adjVisible.value = true
}
async function onAdjWarehouseChange(whId) {
  adjForm.value.matKey = ''
  adjForm.value.materialCode = ''
  adjForm.value.materialName = ''
  adjForm.value.batchNo = ''
  adjForm.value.currentQty = 0
  adjForm.value.fromLocationId = ''
  adjForm.value.fromLocationName = ''
  loadInventoryOfWarehouse()
  // 目标仓库未选时，目标库位 = 当前仓库的库位
  if (!adjForm.value.toWarehouseId) await fetchLocations(whId)
}
async function onAdjToWarehouseChange(whId) {
  adjForm.value.toLocationId = ''
  await fetchLocations(whId || adjForm.value.warehouseId)
}
function onAdjMaterialChange(key) {
  const [code, batchNo, locId] = String(key).split('##')
  const row = inventoryOfWarehouse.value.find(r =>
    r.materialCode === code && (r.batchNo || '') === (batchNo || '') && (r.locationId || '') === (locId || ''))
  if (row) {
    adjForm.value.materialCode = row.materialCode
    adjForm.value.materialName = row.materialName || ''
    adjForm.value.batchNo = row.batchNo || ''
    adjForm.value.currentQty = Number(row.qty) || 0
    adjForm.value.fromLocationId = row.locationId || ''
    adjForm.value.fromLocationName = row.locationName || '未分配库位'
    adjForm.value.qty = 1
  }
}

async function submitAdj() {
  const f = adjForm.value
  if (!f.warehouseId || !f.materialCode) { ElMessage.warning('请选择仓库和物料'); return }
  if (!f.toLocationId) { ElMessage.warning('请选择目标库位'); return }
  if (!f.qty || f.qty <= 0) { ElMessage.warning('请输入有效数量'); return }
  submitting.value = true
  try {
    await api.post('/stock-check/location-adjust', null, {
      params: {
        materialCode: f.materialCode,
        materialName: f.materialName,
        batchNo: f.batchNo || undefined,
        warehouseId: f.warehouseId,
        toWarehouseId: f.toWarehouseId || undefined,
        fromLocationId: f.fromLocationId || undefined,
        toLocationId: f.toLocationId,
        qty: f.qty,
        remark: f.remark || undefined
      }
    })
    ElMessage.success(f.toWarehouseId ? '跨仓库调整成功' : '库位调整成功')
    adjVisible.value = false
    fetchList()
    loadInventoryOfWarehouse()
  } catch (e) { /* handled */ } finally { submitting.value = false }
}


// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(records)

onMounted(async () => {
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch (e) { /* ignore */ }
  fetchList()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; gap: 10px; }

.type-tabs {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 8px;
  flex-wrap: wrap;
}
.filter-tabs { display: flex; gap: 8px; flex-wrap: wrap; }
.filter-btn {
  background: #fff;
  border: 1px solid #d1d5db;
  color: #374151;
  border-radius: 6px;
  padding: 6px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  outline: none;
}
.filter-btn:hover { border-color: #7288a5; color: #5d5f85; }
.filter-btn.active { background: #eef0f6; border-color: #7288a5; color: #5d5f85; font-weight: 600; }
.type-count { font-size: 13px; color: #64748b; margin-left: auto; white-space: nowrap; }

.table-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

@media (max-width: 768px) {
  .header-actions { width: 100%; }
}
</style>
