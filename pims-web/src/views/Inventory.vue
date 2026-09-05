<template>
  <div class="page-container">
    <div class="page-header">
      <h2>库存管理</h2>
    </div>

    <div class="table-card">
      <div class="type-tabs">
      <div class="filter-tabs">
        <!-- v5.1：取消「全部仓库」视图，仅按仓库查询（降低数据库负载），默认选中第一个仓库 -->
        <button v-for="w in warehouses" :key="w.id"
          :class="['filter-btn', { active: currentWh === String(w.id) }]"
          @click="switchWarehouse(w)">{{ w.name }}</button>
      </div>
        <div class="tab-right">
          <!-- v5.22：双视图——按编码聚合总量 / 按批次聚合余量 -->
          <el-radio-group v-model="view" size="small" @change="onSearch">
            <el-radio-button value="code">按编码</el-radio-button>
            <el-radio-button value="batch">按批次</el-radio-button>
          </el-radio-group>
          <el-input v-model="keyword" :placeholder="view === 'code' ? '搜索编码/品名' : '搜索编码/品名/批号'" clearable size="small" class="search-input" @keyup.enter="onSearch" @clear="onSearch" />
          <el-button size="small" @click="doExport" :loading="exporting">导出 Excel</el-button>
          <el-button size="small" @click="downloadOpeningTpl">下载期初模板</el-button>
          <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" :on-change="onOpeningFile" style="display:inline-block">
            <el-button size="small" type="primary" :loading="openingImporting">导入期初数据</el-button>
          </el-upload>
          <span class="type-count">共 {{ total }} 条</span>
        </div>
      </div>

      <!-- v7.5：仓库→分库两级筛选（选中仓库后展示其分库；「全部」=整仓汇总，「未分库位」=台账未落库位的历史行） -->
      <div class="zone-bar" v-if="zones.length">
        <span class="zone-label">分库</span>
        <button :class="['zone-btn', { active: currentZone === '' }]" @click="switchZone('')">全部</button>
        <button v-for="z in zones" :key="z.id"
          :class="['zone-btn', { active: currentZone === String(z.id) }]"
          @click="switchZone(String(z.id))">{{ z.name }}</button>
        <button :class="['zone-btn', { active: currentZone === '-' }]" @click="switchZone('-')">未分库位</button>
      </div>

      <!-- v5.22 视图一：按编码聚合（同一编码跨批次/库位合计总量） -->
      <p-table v-if="view === 'code'" :data="rows" stripe border style="width:100%" @header-dragend="onHeaderDragend">
        <el-table-column prop="materialCode" label="编码" :width="cw('编码') || 140" />
        <el-table-column prop="materialName" label="品名" :width="cw('品名') || 160" show-overflow-tooltip />
        <!-- v7.5：大类/小类（material 档案带出，字典转义；无档案的历史编码显示原码） -->
        <el-table-column label="大类" :width="cw('大类') || 80" align="center">
          <template #default="{ row }">{{ row.category ? dictLabel('material_category', row.category) : '—' }}</template>
        </el-table-column>
        <el-table-column label="小类" :width="cw('小类') || 110" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.subCategory ? dictLabel('material_sub_category', row.subCategory) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" :width="cw('单位') || 70" align="center">
          <template #default="{ row }">{{ row.unit || 'kg' }}</template>
        </el-table-column>
        <el-table-column prop="batchCount" label="批次数量" :width="cw('批次数量') || 90" align="center" />
        <el-table-column prop="qty" label="库存总量" :width="cw('库存总量') || 100" align="right" />
        <el-table-column prop="availableQty" label="可用总量" :width="cw('可用总量') || 100" align="right">
          <template #default="{ row }">
            <span :class="availableClass(row)">{{ row.availableQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="均价" :width="cw('均价') || 100" align="right">
          <template #header>
            <el-tooltip content="点击价格查看价格走势" placement="top">
              <span class="price-header">均价<span class="trend-hint">📈</span></span>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <span v-if="row.unitPrice" class="price-link" @click="showPriceTrend(row)">{{ Number(row.unitPrice).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="不含税单价" :width="cw('不含税单价') || 95" align="right">
          <template #default="{ row }">{{ fmtTax(netOfTax(row.unitPrice, taxRate)) }}</template>
        </el-table-column>
        <el-table-column label="税额" :width="cw('税额') || 80" align="right">
          <template #default="{ row }">{{ fmtTax(taxOf(row.unitPrice, taxRate)) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="总价" :width="cw('总价') || 110" align="right" />
        <el-table-column label="周转天数" :width="cw('周转天数') || 100" align="center">
          <template #default="{ row }">
            <span v-if="row.stockDays != null" :style="stockDaysStyle(row.stockDays)">{{ row.stockDays }} 天</span>
            <el-tooltip v-else content="近90天无出库或库存已清" placement="top">
              <span style="color:#94a3b8">—</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="最新入库日期" :width="cw('最新入库日期') || 110">
          <template #default="{ row }">{{ row.inboundDate || '-' }}</template>
        </el-table-column>
      </p-table>

      <!-- v5.22 视图二：按编码+批次聚合（同批次跨库位合计余量） -->
      <p-table v-else :data="rows" stripe border style="width:100%" @header-dragend="onHeaderDragend" :row-class-name="rowClassName">
        <el-table-column prop="materialCode" label="编码" :width="cw('编码') || 140" />
        <el-table-column prop="materialName" label="品名" :width="cw('品名') || 160" show-overflow-tooltip />
        <!-- v7.5：大类/小类（同 code 视图） -->
        <el-table-column label="大类" :width="cw('大类') || 80" align="center">
          <template #default="{ row }">{{ row.category ? dictLabel('material_category', row.category) : '—' }}</template>
        </el-table-column>
        <el-table-column label="小类" :width="cw('小类') || 110" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.subCategory ? dictLabel('material_sub_category', row.subCategory) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批号" :width="cw('批号') || 130" show-overflow-tooltip />
        <!-- v5.31：批次所在库位（不合格品也精确到库位） -->
        <el-table-column label="库位" :width="cw('库位') || 110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.locationNames">{{ row.locationNames }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="qty" label="库存量" :width="cw('库存量') || 100" align="right" />
        <el-table-column prop="availableQty" label="可用量" :width="cw('可用量') || 100" align="right">
          <template #default="{ row }">
            <span :class="availableClass(row)">{{ row.availableQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" :width="cw('单价') || 100" align="right">
          <template #header>
            <el-tooltip content="点击价格查看价格走势" placement="top">
              <span class="price-header">单价<span class="trend-hint">📈</span></span>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <span v-if="row.unitPrice" class="price-link" @click="showPriceTrend(row)">{{ row.unitPrice }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="总价" :width="cw('总价') || 110" align="right" />
        <el-table-column label="库龄(天)" :width="cw('库龄(天)') || 90" align="center">
          <template #default="{ row }">
            <span :class="ageClass(row)">{{ inventoryAge(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="入库日期" :width="cw('入库日期') || 110">
          <template #default="{ row }">{{ row.inboundDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="过期日期" :width="cw('过期日期') || 110">
          <template #default="{ row }">{{ row.expiryDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="剩余天数" :width="cw('剩余天数') || 100" align="center">
          <template #default="{ row }">
            <span v-if="row.expiryDate" :class="remainDaysClass(row)">{{ remainDays(row) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="质检状态" :width="cw('质检状态') || 100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.qcStatus" :type="qcTagType(row.qcStatus)" size="small">{{ qcStatusLabel(row.qcStatus) }}</el-tag>
            <span v-else class="text-muted">未质检</span>
          </template>
        </el-table-column>
        <el-table-column label="质检单号" :width="cw('质检单号') || 140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.qcInspectionNo">{{ row.qcInspectionNo }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="检测结果" :width="cw('检测结果') || 160" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- v5.32：点击弹窗查看该批次完整质检报告（质检单+检测项，报告单样式可打印） -->
            <span v-if="row.qcResult" class="qc-result-link" title="点击查看质检报告" @click="showQcReport(row)">{{ row.qcResult }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="检验员" :width="cw('检验员') || 90">
          <template #default="{ row }">{{ row.qcInspector || '-' }}</template>
        </el-table-column>
        <el-table-column label="检验日期" :width="cw('检验日期') || 110">
          <template #default="{ row }">{{ row.qcDate || '-' }}</template>
        </el-table-column>
      </p-table>
      <!-- v5.1：分页渲染，避免一次性渲染全量行（200+ 行 × 18 列无虚拟滚动会卡顿） -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchInventory"
          @size-change="onSearch"
        />
      </div>
    </div>

    <!-- 价格走势图弹窗 -->
    <el-dialog v-model="priceDialogVisible" :title="'价格走势 - ' + priceMaterialName" width="900px" destroy-on-close>
      <div v-if="priceLoading" style="text-align:center;padding:40px;color:#999">加载中...</div>
      <div v-else-if="priceData.length === 0" style="text-align:center;padding:40px;color:#999">暂无价格记录</div>
      <SvgLineChart v-else :labels="priceLabels" :series="priceSeries" :height="260" />
    </el-dialog>

    <!-- v5.32：质检报告弹窗（点击检测结果列打开，iframe 渲染完整报告单样式） -->
    <el-dialog v-model="qcReportVisible" title="质检报告单" width="min(900px, 95vw)" top="4vh" destroy-on-close>
      <div v-if="qcReportLoading" style="text-align:center;padding:60px;color:#999">加载质检报告中…</div>
      <iframe v-else ref="qcReportFrame" :srcdoc="qcReportHtml" class="qc-report-frame" title="质检报告单"></iframe>
      <template #footer>
        <el-button @click="qcReportVisible = false">关闭</el-button>
        <el-button type="primary" @click="printQcReport">打印报告</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { statusType as globalStatusType } from '../utils/statusTag'
import { ref, onMounted, computed, watch } from 'vue'
import { loadTaxRate, netOfTax, taxOf, fmtTax } from '../utils/tax'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'
import SvgLineChart from '../components/charts/SvgLineChart.vue'
import { downloadFile } from '../utils/download'
import { buildQcReportHtml } from '../utils/qcPrint'

const { cw, onHeaderDragend } = useColumnResize('inventory')

const taxRate = ref(13)
const keyword = ref('')
const currentWh = ref('')
const rows = ref([])
const total = ref(0)
const warehouses = ref([])
const exporting = ref(false)
// v7.5：仓库→分库二级筛选
const zones = ref([])
const currentZone = ref('')
// v7.5：大类/小类列字典（material_category / material_sub_category 码转中文）
const dicts = ref({})

function dictLabel(type, value) {
  const items = dicts.value[type] || []
  const found = items.find(d => d.value === value)
  return found ? found.label : (value || '—')
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
  } catch (e) { /* 字典拉不到时大类/小类显示原码 */ }
}

// v5.22：显示视图——code=按编码聚合总量 / batch=按批次聚合余量（默认按批次，与原有明细口径最接近）
const view = ref('batch')

// v5.23：导出当前视图与筛选条件下的全量数据
async function doExport() {
  exporting.value = true
  try {
    const params = { view: view.value }
    if (currentWh.value) params.warehouseId = currentWh.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (currentZone.value) params.zoneId = currentZone.value
    await downloadFile('/inventory/export', params, `库存-${view.value === 'code' ? '按编码' : '按批次'}-${new Date().toISOString().slice(0, 10)}.xlsx`)
  } catch (e) { /* downloadFile 内已提示 */ }
  finally { exporting.value = false }
}

// ==================== 期初导入 ====================
const openingImporting = ref(false)

function downloadOpeningTpl() {
  downloadFile('/inventory/opening/template', {}, '期初导入模板.xlsx')
}

async function onOpeningFile(uploadFile) {
  const file = uploadFile?.raw
  if (!file) return
  try {
    await ElMessageBox.confirm(
      '期初导入将新增库存批次；校验失败不会写入任何数据。确定导入？',
      '导入期初数据', { type: 'warning', confirmButtonText: '导入', cancelButtonText: '取消' })
  } catch { return }

  openingImporting.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    // 原生 axios：绕过 api 拦截器，自行处理校验错误清单
    const res = await axios.post('/api/inventory/opening/import', fd, {
      headers: { 'pims-token': localStorage.getItem('pims-token') || '' },
      timeout: 120000
    })
    const d = res.data
    if (d.code === 200) {
      ElMessage.success(`成功导入 ${d.data.count} 个批次`)
      onSearch()
    } else {
      showOpeningErrors(d.msg, d.data)
    }
  } catch (e) {
    const d = e.response?.data
    if (d && d.code !== 200) showOpeningErrors(d.msg, d.data)
    else ElMessage.error(d?.msg || '导入失败，请检查网络')
  } finally { openingImporting.value = false }
}

function showOpeningErrors(msg, errors) {
  // v6.1 安全：同 useExcelImport——服务端错误文本改纯文本渲染防注入
  const lines = Array.isArray(errors) && errors.length
    ? errors.map(x => `第 ${x.row} 行：${x.reason}`).join('\n')
    : '请检查文件内容'
  ElMessageBox.alert(lines, msg || '导入失败', {
    confirmButtonText: '知道了', customStyle: { whiteSpace: 'pre-wrap' }
  }).catch(() => {})
}

// v5.9：后端分页（品名/编码/批号 + 仓库过滤由后端 SQL 完成）
const page = ref(1)
const pageSize = ref(25)

// === 保质期计算 ===
function remainDays(row) {
  if (!row.expiryDate) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const expiry = new Date(row.expiryDate)
  const diff = Math.ceil((expiry - today) / (1000 * 60 * 60 * 24))
  return diff
}

function remainDaysClass(row) {
  const d = remainDays(row)
  if (d === null) return ''
  if (d < 0) return 'days-expired'
  if (d <= 30) return 'days-warning'
  return 'days-normal'
}

// === 库龄计算 ===
function inventoryAge(row) {
  const dateStr = row.inboundDate || (row.createTime ? row.createTime.substring(0, 10) : null)
  if (!dateStr) return '-'
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const inbound = new Date(dateStr)
  return Math.max(0, Math.ceil((today - inbound) / (1000 * 60 * 60 * 24)))
}

function ageClass(row) {
  const age = inventoryAge(row)
  if (age === '-') return 'text-muted'
  if (age > 90) return 'days-expired'
  if (age > 60) return 'days-warning'
  return 'days-normal'
}

// === 质检状态显示 ===
function qcTagType(s) {
  // v6.6 收口：全局 + 质检局部（待检橙、油尾橙）
  return globalStatusType(s, { PENDING: 'warning', TAILING: 'warning', PENDING_QC: 'warning' })
}
function qcStatusLabel(s) {
  return { PASS: '合格', CONCESSION: '让步接收', REJECT: '不合格', TAILING: '油尾', EXPIRED: '已过期', PENDING: '待检', PENDING_QC: '待检' }[s] || '未知'
}

function rowClassName({ row }) {
  const d = remainDays(row)
  if (d !== null && d <= 30) return 'row-expiry-warning'
  return ''
}

// 可用量与库存量不一致时红色高亮，提示数据异常
// v7.4 周转天数配色：≤45 天绿（快）、≥180 天红（呆滞预警）、中间灰
function stockDaysStyle(d) {
  if (d <= 45) return 'color:#16a34a;font-weight:600'
  if (d >= 180) return 'color:#ef4444;font-weight:600'
  return 'color:#475569'
}

function availableClass(row) {
  const q = Number(row.qty) || 0
  const a = Number(row.availableQty) || 0
  if (q !== a) return 'days-expired'
  return ''
}

// v5.9：后端分页查询（仓库 + 关键字），切换仓库/搜索/视图时重新请求
// v5.22：改调 /inventory/summary（按编码聚合 或 按编码+批次聚合）
async function fetchInventory() {
    const params = { view: view.value, page: page.value, pageSize: pageSize.value, warehouseId: currentWh.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (currentZone.value) params.zoneId = currentZone.value
  try {
    const res = await api.get('/inventory/summary', { params })
    rows.value = res.rows
    total.value = res.total
  } catch (e) { /* ignore */ }
}
function onSearch() { page.value = 1; fetchInventory() }

// v5.32：质检报告弹窗——点击检测结果列打开，按质检单+检测项渲染完整报告单样式（可打印）
const qcReportVisible = ref(false)
const qcReportLoading = ref(false)
const qcReportHtml = ref('')
const qcReportFrame = ref(null)
async function showQcReport(row) {
  if (!row.qcInspectionNo) { ElMessage.warning('该批次无质检单号'); return }
  qcReportVisible.value = true
  qcReportLoading.value = true
  qcReportHtml.value = ''
  try {
    const res = await api.get(`/qc/by-no/${row.qcInspectionNo}`)
    const qc = res.qc || {}
    qcReportHtml.value = buildQcReportHtml({ ...qc, warehouseName: whNameById(qc.warehouseId) }, res.items || [])
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '质检单不存在')
    qcReportVisible.value = false
  } finally { qcReportLoading.value = false }
}
function whNameById(id) {
  if (!id) return '-'
  const w = warehouses.value.find(w => String(w.id) === String(id))
  return w ? w.name : String(id)
}
function printQcReport() {
  const frame = qcReportFrame.value
  if (frame && frame.contentWindow) { frame.contentWindow.focus(); frame.contentWindow.print() }
}

// v7.5：切换仓库→重置分库并拉取该仓分库列表（无分库的老仓不显示二级条）
async function loadZones(warehouseId) {
  currentZone.value = ''
  zones.value = []
  if (!warehouseId) return
  try {
    zones.value = (await api.get(`/warehouse/${warehouseId}/zone`)).filter(z => z.enabled !== false)
  } catch (e) { /* ignore */ }
}

function switchZone(z) {
  currentZone.value = z
  page.value = 1
  fetchInventory()
}

async function switchWarehouse(w) {
  currentWh.value = String(w.id)
  await loadZones(currentWh.value)
  page.value = 1
  fetchInventory()
}

onMounted(async () => {
  loadTaxRate(api).then(r => { taxRate.value = r })
  fetchDicts()
  try {
    // v5.32：只显示启用中的仓库（已禁用旧仓不再出现，避免误以为还有库存）
    warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false)
  } catch (e) { /* ignore */ }
  // 默认选中第一个仓库
  if (warehouses.value.length) {
    currentWh.value = String(warehouses.value[0].id)
    await loadZones(currentWh.value)
    fetchInventory()
  }
})

// === 价格走势图 ===
const priceDialogVisible = ref(false)
const priceLoading = ref(false)
const priceMaterialName = ref('')
const priceData = ref([])

const priceLabels = computed(() => priceData.value.map(d => d.date || ''))
const priceSeries = computed(() => [{
  name: '单价',
  values: priceData.value.map(d => Number(d.price) || 0)
}])

async function showPriceTrend(row) {
  priceMaterialName.value = row.materialName || row.materialCode
  priceDialogVisible.value = true
  priceLoading.value = true
  priceData.value = []
  try {
    priceData.value = await api.get(`/inventory/price-trend/${row.materialCode}`)
  } catch (e) { /* ignore */ }
  priceLoading.value = false
}
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
  background: #fafafa;
  border-radius: 8px;
  flex-wrap: wrap;
}
.filter-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
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
.filter-btn:hover {
  border-color: #22c55e;
  color: #15803d;
}
.filter-btn.active {
  background: #f0fdf4;
  border-color: #22c55e;
  color: #15803d;
  font-weight: 600;
}
/* v7.5：分库二级筛选条（比仓库按钮小一号，虚线框区分层级） */
.zone-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 0 0 16px 0;
  padding: 8px 14px;
  background: #fff;
  border: 1px dashed #e2e8f0;
  border-radius: 6px;
}
.zone-label {
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
}
.zone-btn {
  background: #fff;
  border: 1px solid #e5e7eb;
  color: #64748b;
  border-radius: 4px;
  padding: 3px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  outline: none;
}
.zone-btn:hover {
  border-color: #22c55e;
  color: #15803d;
}
.zone-btn.active {
  background: #f0fdf4;
  border-color: #22c55e;
  color: #15803d;
  font-weight: 600;
}
.tab-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}
.search-input {
  width: 160px;
}
.type-count {
  font-size: 13px;
  color: #64748b;
  white-space: nowrap;
}
@media (max-width: 768px) {
  .search-input {
    width: 120px;
  }
}
.price-link {
  color: #6366f1;
  cursor: pointer;
  border-bottom: 1px dashed #6366f1;
}
.price-link:hover {
  color: #4338ca;
  border-bottom-style: solid;
}
.price-header { cursor: default; }
.trend-hint { font-size: 12px; margin-left: 2px; }
.text-muted { color: #9ca3af; font-size: 12px; }

/* v5.32：检测结果列点击链接 + 质检报告弹窗 */
.qc-result-link { color: #2563eb; cursor: pointer; border-bottom: 1px dashed #93c5fd; }
.qc-result-link:hover { color: #1d4ed8; border-bottom-style: solid; }
.qc-report-frame { width: 100%; height: 68vh; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; }
.days-normal { color: #16a34a; font-weight: 500; }
.days-warning { color: #ea580c; font-weight: 700; }
.days-expired { color: #dc2626; font-weight: 700; }
:deep(.row-expiry-warning) {
  background-color: #fff7ed !important;
}
:deep(.row-expiry-warning:hover > td) {
  background-color: #ffedd5 !important;
}
.pagination-bar { display: flex; justify-content: flex-end; padding: 12px 0 4px; }
</style>
