<template>
  <div class="page-container">
    <div class="page-header">
      <h2>委外出库</h2>
      <div class="header-actions">
        <el-button @click="doExport" :loading="exporting">导出 Excel</el-button>
        <el-button type="primary" @click="openDialog">参照委外单领料</el-button>
      </div>
    </div>
    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ total }} 条记录</span>
          <el-input v-model="searchText" placeholder="搜索品名/编码/批号" clearable size="small" style="width:220px;margin-left:auto"  @keyup.enter="onSearch" @clear="onSearch" /></div>
      <p-table :data="rows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单据号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="outsourceOrderNo" label="委外订单" min-width="130" show-overflow-tooltip />
        <el-table-column label="代工厂" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.processorName || row.processor || row.processorId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="materialCode" label="原料编码" min-width="120" />
        <el-table-column prop="qty" label="数量" width="90" align="right" />
        <el-table-column prop="batchNo" label="批号" width="120" show-overflow-tooltip />
        <el-table-column label="批号单价" width="100" align="right">
          <template #default="{ row }">{{ row.unitPrice != null ? '￥' + Number(row.unitPrice).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column label="实际成本" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.cost != null" class="cost-cell">￥{{ Number(row.cost).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="调出仓" width="110">
          <template #default="{ row }">{{ whName(row.fromWarehouseId) }}</template>
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
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-primary" @click="confirmOne(row)">确认</button>
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
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="fetch"
          @size-change="onSearch"
        />
      </div>
    </div>

    <!-- 参照委外订单创建出库 -->
    <el-dialog title="参照委外订单领料" v-model="visible" width="min(1250px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="委外订单" required>
          <el-select v-model="form.outsourceOrderId" filterable placeholder="选择已确认的委外订单" style="width:100%" @change="onOrderChange">
            <el-option v-for="o in confirmedOrders" :key="o.id" :label="o.orderNo + ' - ' + o.productName" :value="o.id" />
          </el-select>
        </el-form-item>
        <!-- 代工厂：从委外订单带入，只读展示，不可编辑 -->
        <el-form-item v-if="form.processor" label="代工厂">
          <el-input :model-value="form.processor" readonly placeholder="选择委外订单后自动带入" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-if="previewItems.length" label="配方明细">
          <p-table :data="previewItems" border size="small" style="width:100%">
            <el-table-column label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.nodeType === 'SUB_RECIPE'" type="warning" size="small">半成品</el-tag>
                <span v-else class="text-muted">原料</span>
              </template>
            </el-table-column>
            <el-table-column prop="materialCode" label="编码" min-width="110" />
            <el-table-column prop="materialName" label="品名" min-width="120" />
            <el-table-column label="调出仓库" width="150" align="center">
              <template #default="{ row }">
                <!-- v5.7：跨库发料——半成品/原料分仓存放，每行独立选择调出仓库 -->
                <el-select v-model="row.warehouseId" size="small" placeholder="选择仓库" style="width:100%" @change="onRowWhChange(row)">
                  <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label-width="10" align="center">
              <template #header>
                <span>批次（必选）</span>
                <el-tag v-if="costingMethod === 'MOVING_AVG' || costingMethod === 'MONTHLY_AVG'" size="small" type="warning" style="margin-left:4px">成本按均价</el-tag>
              </template>
              <template #default="{ row }">
                <el-select v-model="row.batchNo" size="small" placeholder="选择批次" style="width:100%" :disabled="!row.warehouseId" @change="(val) => onBatchChange(row, val)">
                  <el-option
                    v-for="(b, bi) in (batchOptions[row.materialCode] || [])"
                    :key="b.key"
                    :label="(costingMethod === 'FIFO' && bi === 0 ? '【推荐·最早】' : '') + b.label"
                    :value="b.batchNo"
                    :style="{ color: (b.totalQty || 0) < (row.qty || 0) ? '#dc2626' : '', fontWeight: (b.totalQty || 0) < (row.qty || 0) ? '600' : '400' }"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="出库用量" width="150" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.qty" :min="0.001" :precision="3" :step="1" size="small" controls-position="right" style="width:130px" />
              </template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="60" align="center" />
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <button v-if="row.nodeType === 'SUB_RECIPE'" class="op-btn op-btn-primary" @click="tracePreview(row)">溯源</button>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
          </p-table>
          <div class="qty-hint">可修改出库用量，每行必须选择出库批号（实际成本按批号单价直取）；半成品为常备库存，按半成品批次领用</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">生成出库单</el-button>
      </template>
    </el-dialog>

    <!-- 半成品溯源弹窗（v5.6：半成品为常备库存，领用半成品可追溯其配方原料） -->
    <el-dialog :title="'半成品溯源 - ' + (traceTitle || '')" v-model="traceVisible" width="min(1100px, 96vw)" destroy-on-close>
      <p-table :data="traceRows" border size="small">
        <el-table-column label="层级" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.nodeType === 'SUB_RECIPE'" type="warning" size="small">半成品</el-tag>
            <span v-else class="text-muted">原料</span>
          </template>
        </el-table-column>
        <el-table-column prop="materialCode" label="物料编码" min-width="130" />
        <el-table-column prop="materialName" label="品名" min-width="150" />
        <el-table-column prop="qty" label="用量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
      </p-table>
      <p class="trace-tip">半成品由以下物料组成（按当前订单批量折算）</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
// v9.6 导出当前筛选（下载工具绕过 JSON 拦截器）
import { downloadFile } from '../utils/download'
const exporting = ref(false)
async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/outbound/outsource/export', { keyword: searchText.value || undefined }, `委外发料-${new Date().toLocaleDateString('sv')}.xlsx`)
  } finally { exporting.value = false }
}


const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(25)
const warehouses = ref([])
const confirmedOrders = ref([])
const previewItems = ref([])
const batchOptions = ref({})
const costingMethod = ref('SPECIFIC')   // v5.63 计价方式
const visible = ref(false)
const loading = ref(false)
// 表单：去掉 toWarehouseId（v4.5 后端不再需要），新增 processor（从委外订单带入，只读）
const form = ref({ outsourceOrderId: null, fromWarehouseId: '', processor: '', remark: '' })

function whName(id) { const w = warehouses.value.find(w => String(w.id) === String(id)); return w ? w.name : id }
function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }

async function fetch() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (searchText.value.trim()) params.keyword = searchText.value.trim()
  try {
    const res = await api.get('/outbound/outsource', { params })
    rows.value = res.rows
    total.value = res.total
  } catch {} }
function onSearch() { page.value = 1; fetch() }

async function openDialog() {
  form.value = { outsourceOrderId: null, fromWarehouseId: '', processor: '', remark: '' }
  previewItems.value = []
  try { confirmedOrders.value = await api.get('/outbound/referenceable-outsource-orders', { params: { forType: 'outbound' } }) } catch {}
  visible.value = true
}

// 切换委外订单：重置明细，带入代工厂名称（processor，只读），并加载配方明细与批次选项
async function onOrderChange(orderId) {
  previewItems.value = []
  batchOptions.value = {}
  const order = confirmedOrders.value.find(o => o.id === orderId)
  form.value.processor = order ? (order.processor || '') : ''
  if (!orderId) return
  try {
    const items = await api.get(`/outsource-order/${orderId}/items`)
    previewItems.value = items.map(it => ({ ...it, batchNo: null, warehouseId: null }))
    loadBatchOptions()
  } catch {}
}

// v5.6：半成品溯源——按半成品引用的子配方版本 trace
const traceVisible = ref(false)
const traceTitle = ref('')
const traceRows = ref([])

async function tracePreview(row) {
  if (!row.refRecipeId) { ElMessage.warning('该半成品未关联子配方，无法溯源'); return }
  traceTitle.value = row.materialName || row.materialCode || ''
  traceRows.value = []
  traceVisible.value = true
  try {
    const data = await api.get(`/recipe/${row.refRecipeId}/trace`)
    const flatten = (nodes, depth) => {
      if (!Array.isArray(nodes)) return []
      return nodes.flatMap(n => {
        const base = [{ nodeType: n.nodeType, materialCode: n.materialCode, materialName: n.materialName, qty: n.qty, unit: n.unit, depth }]
        return base.concat(flatten(n.children, depth + 1))
      })
    }
    traceRows.value = flatten(data.materials, 0)
  } catch (e) {
    if (e?.response?.data?.msg) ElMessage.error(e.response.data.msg)
  }
}

async function loadBatchOptions() {
  const opts = {}
  for (const item of previewItems.value) {
    const wh = item.warehouseId
    try {
      const records = await api.get(`/inventory/material/${item.materialCode}`)
      // v5.7：按行级仓库过滤批次（跨库发料——半成品/原料分仓存放）
      // locationId 置空，后端按批次+仓库 FIFO 跨库位扣减，避免单库位库存不足
      const batchMap = {}
      records.filter(r => Number(r.qty) > 0 && (!wh || String(r.warehouseId) === String(wh)) && r.batchNo).forEach(r => {
        if (!batchMap[r.batchNo]) {
          batchMap[r.batchNo] = { batchNo: r.batchNo, totalQty: 0, details: [], earliest: r.inboundDate || '9999-12-31' }
        }
        batchMap[r.batchNo].totalQty += Number(r.qty)
        if (r.inboundDate && r.inboundDate < batchMap[r.batchNo].earliest) batchMap[r.batchNo].earliest = r.inboundDate
        const place = [r.zoneName, r.locationName].filter(Boolean).join('/')
        if (place) batchMap[r.batchNo].details.push(place + ':' + r.qty)
      })
      opts[item.materialCode] = Object.values(batchMap)
        .sort((a, b) => (a.earliest < b.earliest ? -1 : 1))   // v5.63 FIFO：最早批次排最前
        .map(b => ({
          key: b.batchNo,
          batchNo: b.batchNo,
          locationId: null,
          totalQty: b.totalQty,
          label: b.batchNo + ' (现存量:' + b.totalQty + (b.details.length ? ' ' + b.details.join(' ') : '') + ')'
        }))
    } catch { opts[item.materialCode] = [] }
  }
  batchOptions.value = opts
  // v5.63 FIFO 模式：未选批次的行默认选最早批次
  if (costingMethod.value === 'FIFO') {
    for (const item of previewItems.value) {
      if (!item.batchNo && opts[item.materialCode] && opts[item.materialCode].length) {
        item.batchNo = opts[item.materialCode][0].batchNo
      }
    }
  }
}

// 选择批次时清空库位ID（按仓库FIFO扣减，不锁定库位）
function onBatchChange(row, val) {
  row.locationId = null
}

// v5.7：行级仓库变化——清空该行批次并重载批次选项（跨库发料）
function onRowWhChange(row) {
  row.batchNo = null
  row.locationId = null
  loadBatchOptions()
}

// 提交领料：v5.7 逐行选择调出仓库与批次（半成品/原料分仓，跨库发料）
async function submit() {
  if (!form.value.outsourceOrderId) { ElMessage.warning('请选择委外订单'); return }
  const missingWh = previewItems.value.filter(it => !it.warehouseId)
  if (missingWh.length) { ElMessage.warning(`物料 ${missingWh.map(m => m.materialCode).join('、')} 未选择调出仓库`); return }
  // 出库必须逐行选择批号，实际成本按批号直取
  const missing = previewItems.value.filter(it => !it.batchNo)
  if (missing.length) { ElMessage.warning(`物料 ${missing.map(m => m.materialCode).join('、')} 未选择出库批号`); return }
  loading.value = true
  try {
    // 收集修改后的用量、批次、库位、行级仓库（materialCode -> {qty, batchNo, locationId, warehouseId}）
    const overrides = {}
    previewItems.value.forEach(it => { overrides[it.materialCode] = { qty: Number(it.qty), batchNo: it.batchNo || null, locationId: it.locationId || null, warehouseId: it.warehouseId } })
    await api.post('/outbound/outsource', overrides, {
      params: { outsourceOrderId: form.value.outsourceOrderId, remark: form.value.remark || undefined }
    })
    ElMessage.success('出库单据已生成并确认，已发给委外工厂')
    visible.value = false
    fetch()
  } catch {} finally { loading.value = false }
}

// 确认草稿出库单：库存从调出仓发给委外工厂
async function confirmOne(row) {
  try {
    await ElMessageBox.confirm(`确认出库单 ${row.docNo}？\n确认后将从 ${whName(row.fromWarehouseId)} 发给委外工厂。`, '确认出库', { type: 'warning' })
    await api.post(`/outbound/outsource/${row.id}/confirm`)
    ElMessage.success('已确认，已发给委外工厂')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
// v5.7：按品名/编码/批号查询（批号全系统可追溯）
const searchText = ref('')

onMounted(async () => {
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch {}
  try { costingMethod.value = (await api.get('/costing/config')).method || 'SPECIFIC' } catch {}   // v5.63
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
.trace-tip { font-size: 12px; color: #64748b; margin: 10px 0 0; }
</style>
