<template>
  <div class="page-container">
    <div class="page-header">
      <h2>生产领料</h2>
      <div class="header-actions">
        <el-button @click="doExport" :loading="exporting">导出 Excel</el-button>
        <el-button @click="openReturnDialog()">生产退料</el-button>
        <el-button type="primary" @click="openDialog">参照订单领料</el-button>
      </div>
    </div>
    <div class="table-card">
      <div class="type-tabs">
        <span class="type-count">共 {{ total }} 条记录</span>
          <el-input v-model="searchText" placeholder="搜索品名/编码/批号" clearable size="small" style="width:220px;margin-left:auto"  @keyup.enter="onSearch" @clear="onSearch" />
      </div>
      <p-table :data="rows" stripe border style="width:100%">
        <el-table-column label="单据号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.docType === 'RETURN'" type="danger" size="small" style="margin-right:4px">退料</el-tag><el-tag v-if="row.supplementType === 'COLOR_ADJUST'" type="warning" size="small" style="margin-right:4px">色差补领</el-tag>{{ row.docNo }}
          </template>
        </el-table-column>
        <el-table-column prop="productionOrderNo" label="生产订单" min-width="130" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品" min-width="130" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="原料编码" min-width="120" />
        <el-table-column prop="materialName" label="品名" min-width="130" show-overflow-tooltip />
        <el-table-column prop="qty" label="出库数量" width="100" align="right" />
        <el-table-column prop="batchNo" label="批号" width="120" show-overflow-tooltip />
        <el-table-column v-if="hasFinanceAmount" label="批号单价" width="100" align="right">
          <template #default="{ row }">{{ row.unitPrice != null ? '￥' + Number(row.unitPrice).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column v-if="hasFinanceAmount" label="实际成本" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.cost != null" class="cost-cell">￥{{ Number(row.cost).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="出库仓库" width="120">
          <template #default="{ row }">{{ whName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column label="出库库位" min-width="130">
          <template #default="{ row }">
            <span v-if="row.locationName">{{ row.zoneName ? row.zoneName + ' / ' : '' }}{{ row.locationName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'CANCELLED'" type="info" size="small">已作废</el-tag>
            <el-tag v-else :type="row.status === 'CONFIRMED' ? 'success' : 'warning'" size="small">
              {{ row.status === 'CONFIRMED' ? '已确认' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="90" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 'CANCELLED'"><span class="text-muted">—</span></template>
            <button v-else-if="row.status === 'DRAFT'" class="op-btn op-btn-primary" @click="confirmOne(row)">确认</button>
            <template v-else>
              <button v-if="row.docType !== 'RETURN' && row.productionOrderNo" class="op-btn" @click="openReturnDialog(row.productionOrderNo)">退料</button>
              <button v-if="row.docType !== 'RETURN'" class="op-btn op-btn-danger" @click="voidOne(row)">作废</button>
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

    <!-- 参照生产订单创建出库（补领模式：可选已领料订单，仅填写本次追加用量） -->
    <el-dialog :title="supplement ? '补领（追加领料）' : '参照生产订单领料'" v-model="visible" width="min(1250px, 96vw)" destroy-on-close>
    <div v-if="supplement" style="margin: 0 0 12px; display: flex; align-items: center; gap: 8px">
      <span style="font-size: 13px; color: #64748b">补领原因：</span>
      <el-radio-group v-model="supplementType" size="small">
        <el-radio-button value="OVER_CONSUME">超耗补充</el-radio-button>
        <el-radio-button value="COLOR_ADJUST">色差调整</el-radio-button>
      </el-radio-group>
    </div>
      <el-form :model="form" label-width="100px">
        <el-form-item label="领料方式">
          <el-radio-group v-model="supplement" @change="onModeChange">
            <el-radio :value="false">正常领料</el-radio>
            <el-radio :value="true">补领（已领料订单追加）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生产订单" required>
          <el-select v-model="form.productionOrderId" filterable :placeholder="supplement ? '选择已领料的生产订单' : '选择已确认的生产订单'" style="width:100%" @change="onOrderChange">
            <el-option v-for="o in confirmedOrders" :key="o.id" :label="o.orderNo + ' - ' + o.productName" :value="o.id" />
          </el-select>
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
            <el-table-column prop="materialName" label="品名" min-width="140" show-overflow-tooltip />
            <el-table-column label="出库仓库" width="150" align="center">
              <template #default="{ row }">
                <!-- v5.7：跨库领料——半成品/原料分仓存放，每行独立选择出库仓库 -->
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
                <el-input-number v-model="row.qty" :min="supplement ? 0 : 0.001" :precision="3" :step="1" size="small" controls-position="right" style="width:130px" />
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
          <div class="qty-hint">{{ supplement ? '补领：仅填写本次追加的用量（未填或 0 的行不生成出库单），每行仍需选择批号' : '可修改出库用量，每行必须选择出库批号（实际成本按批号单价直取）；半成品为常备库存，按半成品批次领用' }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">{{ supplement ? '生成补领单' : '生成出库单' }}</el-button>
      </template>
    </el-dialog>

    <!-- 生产退料：选订单 → 带出已领明细 → 逐行勾选填退料数量（上限=可退量） -->
    <el-dialog title="生产退料" v-model="retVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="生产订单" required>
          <el-select v-model="retOrderNo" filterable allow-create default-first-option placeholder="选择或输入生产订单号" style="width:100%" @change="loadIssuedLines">
            <el-option v-for="o in retOrders" :key="o.orderNo" :label="o.orderNo + ' - ' + o.productName" :value="o.orderNo" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="retLines.length" label="已领明细">
          <p-table :data="retLines" border size="small" style="width:100%">
            <el-table-column label="退" width="46" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.checked" :disabled="Number(row.returnableQty) <= 0" />
              </template>
            </el-table-column>
            <el-table-column prop="materialCode" label="原料编码" min-width="110" />
            <el-table-column prop="materialName" label="品名" min-width="130" show-overflow-tooltip />
            <el-table-column prop="batchNo" label="批号" width="130" show-overflow-tooltip />
            <el-table-column label="仓库/库位" min-width="130">
              <template #default="{ row }">
                {{ whName(row.warehouseId) }}<span v-if="row.locationName"> / {{ row.zoneName ? row.zoneName + '·' : '' }}{{ row.locationName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="issuedQty" label="已领量" width="90" align="right" />
            <el-table-column prop="returnedQty" label="已退量" width="90" align="right" />
            <el-table-column prop="returnableQty" label="可退量" width="90" align="right" />
            <el-table-column label="退料数量" width="150" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.returnQty" :min="0" :max="Number(row.returnableQty)" :precision="3" :step="1" size="small" controls-position="right" style="width:130px" :disabled="!row.checked" />
              </template>
            </el-table-column>
          </p-table>
          <div class="qty-hint">按原批次原库位退回库存，成本按原领料单价冲减；退料数量不能超过可退量</div>
        </el-form-item>
        <el-form-item v-else-if="retOrderNo" label=" ">
          <span class="text-muted">该订单没有可退料的已领明细</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="retRemark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="retVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReturn" :loading="retLoading">确认退料</el-button>
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
    await downloadFile('/outbound/production/export', { keyword: searchText.value || undefined }, `生产领料-${new Date().toLocaleDateString('sv')}.xlsx`)
  } finally { exporting.value = false }
}


// 有「查看金额」权限才显示单价/成本（生产人员不显示价格）
const hasFinanceAmount = computed(() => {
  try { return (JSON.parse(localStorage.getItem('user') || '{}').permissions || []).includes('finance:amount') } catch { return false }
})

const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(25)
const warehouses = ref([])
const confirmedOrders = ref([])
const previewItems = ref([])
const batchOptions = ref({})
const costingMethod = ref('SPECIFIC')   // v5.63 计价方式（FIFO 默认选最早批次/均价提示）
const visible = ref(false)
const loading = ref(false)
const supplement = ref(false)   // 补领模式：可选已领料订单，仅填写本次追加用量
const supplementType = ref('OVER_CONSUME')   // v6.8 补领原因：COLOR_ADJUST 色差调整 / OVER_CONSUME 超耗补充
const form = ref({ productionOrderId: null, warehouseId: '', remark: '' })

function whName(id) { const w = warehouses.value.find(w => String(w.id) === String(id)); return w ? w.name : id }
function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }

async function fetch() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (searchText.value.trim()) params.keyword = searchText.value.trim()
  try {
    const res = await api.get('/outbound/production', { params })
    rows.value = res.rows
    total.value = res.total
  } catch {} }
function onSearch() { page.value = 1; fetch() }

async function openDialog() {
  form.value = { productionOrderId: null, warehouseId: '', remark: '' }
  supplement.value = false
  previewItems.value = []
  try { confirmedOrders.value = await api.get('/outbound/referenceable-production-orders', { params: { forType: 'outbound' } }) } catch {}
  visible.value = true
}

// 切换 正常领料/补领：重载可参照订单（补领不排除已领料订单），清空已选
async function onModeChange() {
  form.value.productionOrderId = null
  previewItems.value = []
  batchOptions.value = {}
  try {
    confirmedOrders.value = await api.get('/outbound/referenceable-production-orders', {
      params: { forType: 'outbound', mode: supplement.value ? 'supplement' : undefined }
    })
  } catch {}
}

async function onOrderChange(orderId) {
  previewItems.value = []
  batchOptions.value = {}
  if (!orderId) return
  try {
    const items = await api.get(`/production-order/${orderId}/items`)
    // 补领：默认用量 0，由用户填写本次追加用量，避免按配方全额重复领料
    previewItems.value = items.map(it => ({ ...it, qty: supplement.value ? 0 : it.qty, batchNo: null, warehouseId: null }))
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
      // v5.7：按行级仓库过滤批次（跨库领料——半成品/原料分仓存放）
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

// v5.7：行级仓库变化——清空该行批次并重载批次选项（半成品/原料分仓，跨库领料）
function onRowWhChange(row) {
  row.batchNo = null
  row.locationId = null
  loadBatchOptions()
}

async function submit() {
  if (!form.value.productionOrderId) { ElMessage.warning('请选择生产订单'); return }
  // 补领：只提交填写了追加用量（>0）的行
  const activeItems = supplement.value ? previewItems.value.filter(it => Number(it.qty) > 0) : previewItems.value
  if (supplement.value && !activeItems.length) { ElMessage.warning('补领请至少填写一行的本次追加用量'); return }
  // v5.7：跨库领料——逐行选择出库仓库与批号
  const missingWh = activeItems.filter(it => !it.warehouseId)
  if (missingWh.length) { ElMessage.warning(`物料 ${missingWh.map(m => m.materialCode).join('、')} 未选择出库仓库`); return }
  // 出库必须逐行选择批号，实际成本按批号直取
  const missing = activeItems.filter(it => !it.batchNo)
  if (missing.length) { ElMessage.warning(`物料 ${missing.map(m => m.materialCode).join('、')} 未选择出库批号`); return }
  loading.value = true
  try {
    // 收集修改后的用量、批次、库位、行级仓库（materialCode -> {qty, batchNo, locationId, warehouseId}）
    const overrides = {}
    activeItems.forEach(it => { overrides[it.materialCode] = { qty: Number(it.qty), batchNo: it.batchNo || null, locationId: it.locationId || null, warehouseId: it.warehouseId } })
    await api.post('/outbound/production', overrides, {
      params: { productionOrderId: form.value.productionOrderId, remark: form.value.remark || undefined, supplement: supplement.value,
        supplementType: supplement.value ? (supplementType.value || undefined) : undefined }   // v6.8 补领原因
    })
    ElMessage.success('出库单据已生成并确认，库存已扣减')
    visible.value = false
    fetch()
  } catch {} finally { loading.value = false }
}

async function confirmOne(row) {
  try {
    await ElMessageBox.confirm(`确认出库单 ${row.docNo}？\n确认后将扣减库存。`, '确认出库', { type: 'warning' })
    await api.post(`/outbound/production/${row.id}/confirm`)
    ElMessage.success('已确认，库存已扣减')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

// v5.64 领料单作废：整行冲回（库存加回原批次原库位）+ 原行标记，不可逆
// v8.11：必填原因——ElMessageBox.prompt 输入，空原因后端也会拦
async function voidOne(row) {
  let reason
  try {
    const { value } = await ElMessageBox.prompt(
      `作废领料行 ${row.docNo}（${row.materialName || row.materialCode} × ${row.qty}）不可逆，库存将整行冲回。请填写作废原因：`,
      '作废领料行',
      { confirmButtonText: '确认作废', cancelButtonText: '取消', inputPlaceholder: '必填（如：录错数量/计划取消）',
        inputValidator: v => (v && v.trim()) ? true : '作废原因不能为空' })
    reason = value.trim()
  } catch { return }
  try {
    const r = await api.put(`/outbound/production/${row.id}/void`, { reason })
    ElMessage.success(`已作废（原因已留痕），冲减单 ${r.returnDocNo || ''} 已生成，库存已退回`)
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '作废失败')
  }
}

// ===== 生产退料：选订单 → 带出已领明细 → 逐行勾选填退料数量 =====
const retVisible = ref(false)
const retOrderNo = ref('')
const retOrders = ref([])     // 已领料未入库的订单（下拉建议，也可手输单号）
const retLines = ref([])
const retRemark = ref('')
const retLoading = ref(false)

async function openReturnDialog(orderNo) {
  retOrderNo.value = orderNo || ''
  retLines.value = []
  retRemark.value = ''
  try { retOrders.value = await api.get('/outbound/referenceable-production-orders', { params: { forType: 'inbound' } }) } catch {}
  retVisible.value = true
  if (retOrderNo.value) loadIssuedLines()
}

async function loadIssuedLines() {
  retLines.value = []
  if (!retOrderNo.value) return
  try {
    const lines = await api.get('/outbound/production/issued-lines', { params: { orderNo: retOrderNo.value } })
    retLines.value = lines.map(l => ({ ...l, checked: false, returnQty: null }))
  } catch {}
}

async function submitReturn() {
  if (!retOrderNo.value) { ElMessage.warning('请选择生产订单'); return }
  const picked = retLines.value.filter(l => l.checked && Number(l.returnQty) > 0)
  if (!picked.length) { ElMessage.warning('请勾选退料行并填写退料数量'); return }
  const over = picked.filter(l => Number(l.returnQty) > Number(l.returnableQty))
  if (over.length) { ElMessage.warning(`物料 ${over.map(m => m.materialCode).join('、')} 退料数量超出可退量`); return }
  retLoading.value = true
  try {
    const lines = picked.map(l => ({
      materialCode: l.materialCode, batchNo: l.batchNo,
      warehouseId: l.warehouseId, locationId: l.locationId, qty: Number(l.returnQty)
    }))
    await api.post('/outbound/production/return', lines, {
      params: { productionOrderNo: retOrderNo.value, remark: retRemark.value || undefined }
    })
    ElMessage.success('退料完成，库存已按原批次原库位加回')
    retVisible.value = false
    fetch()
  } catch {} finally { retLoading.value = false }
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
