<template>
  <div class="page-container">
    <div class="page-header">
      <h2>委外入库</h2>
      <el-button type="primary" @click="openDialog">参照委外单入库</el-button>
        <el-button @click="doExport" :loading="exporting">导出 Excel</el-button>
    </div>
    <div class="table-card">
      <div class="type-tabs">
        <span class="type-count">共 {{ total }} 条记录</span>
        <!-- v5.27：选中行打印 8×10 入库标签 -->
        <el-button size="small" type="primary" :disabled="!selectedRows.length" @click="onPrintLabels">打印标签（{{ selectedRows.length }}）</el-button>
          <el-input v-model="searchText" placeholder="搜索品名/编码/批号" clearable size="small" style="width:220px;margin-left:auto"  @keyup.enter="onSearch" @clear="onSearch" />
      </div>
      <p-table :data="rows" stripe border style="width:100%" @selection-change="sel => selectedRows = sel">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="docNo" label="单据号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="outsourceOrderNo" label="委外订单" min-width="130" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip />
        <el-table-column prop="productCode" label="产品编码" min-width="110" />
        <el-table-column prop="batchNo" label="批次" width="100" />
        <el-table-column prop="qty" label="实际产出" width="100" align="right" />
        <el-table-column label="理论产出" width="100" align="right">
          <template #default="{ row }">{{ row.theoreticalQty != null ? row.theoreticalQty : '-' }}</template>
        </el-table-column>
        <el-table-column label="得率" width="90" align="right">
          <template #default="{ row }">
            <span v-if="row.yieldRate != null" :class="yieldClass(row.yieldRate)" title="实际产出 ÷ 理论产出（配方批量）">{{ Number(row.yieldRate).toFixed(1) }}%</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column label="实际材料成本" width="130" align="right">
          <template #default="{ row }">
            <span v-if="actualCostMap[row.outsourceOrderNo] != null" class="cost-cell" title="按发料出库批号单价汇总">￥{{ Number(actualCostMap[row.outsourceOrderNo]).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="单位成本" width="110" align="right">
          <template #default="{ row }">
            <span v-if="actualCostMap[row.outsourceOrderNo] != null && Number(row.qty) > 0" class="cost-cell">￥{{ (Number(actualCostMap[row.outsourceOrderNo]) / Number(row.qty)).toFixed(2) }}/{{ row.unit || 'kg' }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="仓库" width="110">
          <template #default="{ row }">{{ whName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="90" />
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-primary" @click="confirmOne(row)">确认</button>
            <button class="op-btn op-btn-trace" @click="openTrace(row)">溯源</button>
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

    <!-- 参照委外订单入库 -->
    <el-dialog title="参照委外订单入库" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="委外订单" required>
          <el-select v-model="form.outsourceOrderId" filterable placeholder="选择已确认的委外订单" style="width:100%" @change="onOrderChange">
            <el-option v-for="o in confirmedOrders" :key="o.id" :label="o.orderNo + ' - ' + o.productName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedOrder" label="产品">
          <el-input :value="selectedOrder.productName" disabled />
        </el-form-item>
        <el-form-item v-if="selectedOrder" label="批量">
          <el-input :value="selectedOrder.batchQty + ' ' + (selectedOrder.unit||'')" disabled />
        </el-form-item>
        <el-form-item label="入库数量" required>
          <el-input-number v-model="form.qty" :min="0.001" :precision="3" :step="100" style="width:100%" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input value="系统自动生成" disabled />
        </el-form-item>
        <el-form-item label="入库仓库" required>
          <el-select v-model="form.warehouseId" placeholder="选择仓库" style="width:100%" @change="onWarehouseChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="分库" required>
          <el-select v-model="form.zoneId" placeholder="请先选择仓库" style="width:100%" :disabled="!form.warehouseId" @change="onZoneChange">
            <el-option v-for="z in zones" :key="z.id" :label="z.name" :value="String(z.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="库位" required>
          <el-select v-model="form.locationId" placeholder="请先选择分库" style="width:100%" :disabled="!form.zoneId">
            <el-option v-for="loc in locations" :key="loc.id" :label="loc.name" :value="String(loc.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存单据</el-button>
      </template>
    </el-dialog>

    <!-- 溯源弹窗 -->
    <el-dialog title="配方溯源" v-model="traceVisible" width="min(1100px, 96vw)" destroy-on-close>
      <div v-if="traceLoading" style="text-align:center;padding:40px">加载中...</div>
      <div v-else-if="!traceData">
        <el-empty description="无法溯源" :image-size="60" />
      </div>
      <div v-else class="trace-content">
        <div class="trace-header">
          <span class="trace-product">{{ traceData.productName }}</span>
          <el-tag v-if="traceData.processor" size="small" type="warning">代工厂：{{ traceData.processor }}</el-tag>
          <el-tag v-if="traceData.hasRecipe" size="small" type="success">{{ traceData.recipeNo }} {{ traceData.versionNo }}</el-tag>
          <span class="trace-qty">批量: {{ traceData.batchQty }} {{ traceData.unit }}</span>
        </div>
        <!-- 配方谱系树 -->
        <el-tree v-if="traceTree.length" :data="traceTree" :props="{ label: 'label', children: 'children' }" default-expand-all node-key="uid" class="trace-tree">
          <template #default="{ data }">
            <div class="trace-node">
              <el-tag v-if="data.isSubRecipe" type="warning" size="small">{{ data.recipeType === 'GRINDING' ? '色浆' : '子配方' }}</el-tag>
              <el-tag v-else size="small">原料</el-tag>
              <span class="tn-name">{{ data.materialName || data.materialCode }}</span>
              <span class="tn-code">{{ data.materialCode }}</span>
              <span class="tn-qty">× {{ data.qty }} {{ data.unit }}</span>
            </div>
          </template>
        </el-tree>
        <!-- 实际发料批次记录 -->
        <div v-if="traceData.outboundBatches && traceData.outboundBatches.length" class="batch-section">
          <h4 style="margin:16px 0 8px;font-size:14px">实际发料批次追溯</h4>
          <p-table :data="traceData.outboundBatches" border size="small" style="width:100%">
            <el-table-column prop="materialCode" label="物料编码" min-width="110" />
            <el-table-column prop="materialName" label="品名" min-width="120" show-overflow-tooltip />
            <el-table-column prop="batchNo" label="批次号" min-width="110" />
            <el-table-column prop="qty" label="用量" width="90" align="right" />
            <el-table-column prop="unit" label="单位" width="60" align="center" />
            <el-table-column prop="sourceDocNo" label="来源单据" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sourceDocNo || '-' }}</template>
            </el-table-column>
          </p-table>
        </div>
        <el-empty v-if="!traceData.hasRecipe && !(traceData.outboundBatches && traceData.outboundBatches.length)" description="无溯源数据" :image-size="60" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { statusType as globalStatusType } from '../utils/statusTag'
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { printLabels } from '../utils/labelPrint'
import { useBucketPrint } from '../composables/useBucketPrint'
// v9.6 导出当前筛选（下载工具绕过 JSON 拦截器）
import { downloadFile } from '../utils/download'
const exporting = ref(false)
async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/outbound/outsource-inbound/export', { keyword: searchText.value || undefined }, `委外入库-${new Date().toLocaleDateString('sv')}.xlsx`)
  } finally { exporting.value = false }
}


const rows = ref([])
// v5.27：多选行（打印标签用）
const selectedRows = ref([])

// v7.3 分桶打印：逐行弹桶数/微调（Σ守恒禁打），不拆分则保持整单一张
const { printWithBuckets } = useBucketPrint()
async function onPrintLabels() { await printWithBuckets(selectedRows.value) }
const total = ref(0)
const page = ref(1)
const pageSize = ref(25)
const warehouses = ref([])
const confirmedOrders = ref([])
const zones = ref([])
const locations = ref([])
const visible = ref(false)
const loading = ref(false)
const form = ref({ outsourceOrderId: null, qty: null, batchNo: '', warehouseId: '', zoneId: '', locationId: '', remark: '' })

// 实际材料成本：按委外订单汇总的发料出库批号成本
const actualCostMap = ref({})
async function fetchActualCosts() {
  try {
    const costs = await api.get('/outbound/outsource/actual-costs')
    const map = {}
    costs.forEach(c => { map[c.orderNo] = c.totalCost })
    actualCostMap.value = map
  } catch {}
}

const selectedOrder = computed(() => confirmedOrders.value.find(o => o.id === form.value.outsourceOrderId))

// 溯源
const traceVisible = ref(false)
const traceLoading = ref(false)
const traceData = ref(null)
const traceTree = ref([])
let traceUid = 0

function whName(id) { const w = warehouses.value.find(w => String(w.id) === String(id)); return w ? w.name : id }
function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }
// 状态：草稿→待质检（确认后）→已入库（QC合格后由系统回写DONE）
const statusTagType = (s) => globalStatusType(s, {CONFIRMED: 'warning', DRAFT: 'info', DONE: 'success', REJECTED: 'danger'})   // v6.6 收口：全局 + 域局部
function statusLabel(s) { return { CONFIRMED: '待质检', DRAFT: '草稿', DONE: '已入库', REJECTED: '已入不合格库' }[s] || '未知' }
// 得率着色：≥98% 绿，≥90% 橙，否则红
function yieldClass(rate) {
  const r = Number(rate)
  return r >= 98 ? 'yield-good' : r >= 90 ? 'yield-warn' : 'yield-bad'
}

async function fetch() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (searchText.value.trim()) params.keyword = searchText.value.trim()
  try {
    const res = await api.get('/outbound/outsource-inbound', { params })
    rows.value = res.rows
    total.value = res.total
  } catch {} }
function onSearch() { page.value = 1; fetch() }

async function openDialog() {
  form.value = { outsourceOrderId: null, qty: null, batchNo: '', warehouseId: '', zoneId: '', locationId: '', remark: '' }
  zones.value = []
  locations.value = []
  try { confirmedOrders.value = await api.get('/outbound/referenceable-outsource-orders', { params: { forType: 'inbound' } }) } catch {}
  visible.value = true
}

function onOrderChange(orderId) {
  const o = confirmedOrders.value.find(o => o.id === orderId)
  if (o) form.value.qty = Number(o.batchQty)
}

async function onWarehouseChange(val) {
  form.value.zoneId = ''
  form.value.locationId = ''
  zones.value = []
  locations.value = []
  if (!val) return
  try { zones.value = await api.get(`/warehouse/${val}/zone`) } catch {}
}

async function onZoneChange(val) {
  form.value.locationId = ''
  locations.value = []
  if (!val) return
  try { locations.value = await api.get(`/warehouse/zone/${val}/location`) } catch {}
}

async function submit() {
  if (!form.value.outsourceOrderId) { ElMessage.warning('请选择委外订单'); return }
  if (!form.value.warehouseId) { ElMessage.warning('请选择入库仓库'); return }
  if (!form.value.zoneId) { ElMessage.warning('请选择分库'); return }
  if (!form.value.locationId) { ElMessage.warning('请选择库位'); return }
  loading.value = true
  try {
    await api.post('/outbound/outsource-inbound', null, {
      params: {
        outsourceOrderId: form.value.outsourceOrderId,
        warehouseId: form.value.warehouseId,
        locationId: form.value.locationId,
        qty: form.value.qty,
        batchNo: form.value.batchNo || undefined,
        remark: form.value.remark || undefined
      }
    })
    ElMessage.success('入库单据已创建（草稿）')
    visible.value = false
    fetch()
  } catch {} finally { loading.value = false }
}

async function confirmOne(row) {
  try {
    await ElMessageBox.confirm(`确认入库单 ${row.docNo}？\n确认后将提交出厂质检，质检合格后自动入库。`, '提交质检', { type: 'warning' })
    await api.post(`/outbound/outsource-inbound/${row.id}/confirm`)
    ElMessage.success('已提交质检，请到「质量管理」判定，合格后自动入库')
    fetch()
    fetchActualCosts()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

async function openTrace(row) {
  traceVisible.value = true
  traceLoading.value = true
  traceData.value = null
  traceTree.value = []
  try {
    // 通过委外订单号找到订单ID
    const orders = await api.get('/outsource-order')
    const order = orders.find(o => o.orderNo === row.outsourceOrderNo)
    if (!order) { traceData.value = { hasRecipe: false, orderNo: row.outsourceOrderNo }; return }
    const data = await api.get(`/outsource-order/${order.id}/trace`)
    traceData.value = data
    if (data.hasRecipe && data.materials) {
      traceTree.value = buildTraceTree(data.materials)
    }
  } catch { traceData.value = null } finally { traceLoading.value = false }
}

function buildTraceTree(materials) {
  if (!materials) return []
  return materials.map(m => {
    const node = { ...m, uid: ++traceUid, label: m.materialName || m.materialCode, isSubRecipe: m.nodeType === 'SUB_RECIPE', recipeType: m.refRecipeType }
    if (m.children && m.children.length) {
      node.children = buildTraceTree(m.children)
    }
    return node
  })
}

// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
// v5.7：按品名/编码/批号查询（批号全系统可追溯）
const searchText = ref('')

onMounted(async () => {
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch {}
  fetch()
  fetchActualCosts()
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
.yield-good { color: #16a34a; font-weight: 600; }
.yield-warn { color: #d97706; font-weight: 600; }
.yield-bad { color: #dc2626; font-weight: 600; }
.trace-content { max-height: 60vh; overflow-y: auto; }
.trace-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 12px 16px; background: #f8fafc; border-radius: 8px; }
.trace-product { font-size: 16px; font-weight: 600; }
.trace-qty { font-size: 13px; color: #64748b; }
.trace-tree { margin-top: 8px; }
.trace-node { display: flex; align-items: center; gap: 8px; width: 100%; padding: 2px 0; }
.tn-name { font-weight: 500; }
.tn-code { font-size: 12px; color: #94a3b8; }
.tn-qty { font-size: 12px; color: #6366f1; font-weight: 600; }
</style>
