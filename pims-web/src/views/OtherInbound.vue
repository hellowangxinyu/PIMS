<template>
  <div class="page-container">
    <div class="page-header">
      <h2>其他入库</h2>
      <el-button type="primary" @click="openDialog">新建入库单</el-button>
    </div>
    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ total }} 条记录</span>
          <el-input v-model="searchText" placeholder="搜索品名/编码/批号" clearable size="small" style="width:220px;margin-left:auto" @keyup.enter="onSearch" @clear="onSearch" /></div>
      <p-table :data="rows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单据号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="物料编码" min-width="130" />
        <el-table-column prop="materialName" label="品名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="qty" label="入库数量" width="100" align="right" />
        <el-table-column prop="price" label="单价(含税)" width="105" align="right">
          <template #default="{ row }">{{ row.price != null ? Number(row.price).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column label="不含税单价" width="100" align="right">
          <template #default="{ row }">{{ fmtTax(netOfTax(row.price, taxRate)) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="85" align="right">
          <template #default="{ row }">{{ fmtTax(taxOf(row.price, taxRate)) }}</template>
        </el-table-column>
        <el-table-column label="入库仓库" width="120">
          <template #default="{ row }">{{ whName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column label="原因" width="80" align="center">
          <template #default="{ row }">{{ reasonLabel(row.reason) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="90" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
      </p-table>
      <!-- 分页（v5.9 后端分页） -->
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

    <el-dialog title="新建其他入库单" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <!-- v5.78 单据式表格布局 -->
      <el-descriptions :column="2" border size="small" class="po-head-table">
        <el-descriptions-item label="仓库 *">
          <el-select v-model="form.warehouseId" placeholder="选择仓库" style="width:100%" @change="onWhChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="分库 *">
          <el-select v-model="form.zoneId" placeholder="请先选择仓库" style="width:100%" :disabled="!form.warehouseId" @change="onZoneChange">
            <el-option v-for="z in zones" :key="z.id" :label="z.name" :value="String(z.id)" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="库位 *">
          <el-select v-model="form.locationId" placeholder="请先选择分库" style="width:100%" :disabled="!form.zoneId">
            <el-option v-for="l in locations" :key="l.id" :label="l.name || l.code" :value="String(l.id)" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="物料 *">
          <el-select v-model="form.materialCode" filterable placeholder="搜索物料" style="width:100%" @change="onMatChange">
            <el-option v-for="m in materials" :key="m.id" :label="m.code + ' ' + (m.name||'')" :value="m.code" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="品名"><span>{{ form.materialName || '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="批次号"><span>系统自动生成</span></el-descriptions-item>
        <el-descriptions-item label="入库数量 *">
          <el-input-number v-model="form.qty" :min="0.001" :precision="3" :step="1" style="width:150px" />
        </el-descriptions-item>
        <el-descriptions-item label="税率(%)">
          <el-input-number v-model="form.taxRate" :min="0" :max="17" :precision="2" :step="1" style="width:130px" />
          <span style="margin-left:6px;font-size:12px;color:#888">默认13%</span>
        </el-descriptions-item>
        <el-descriptions-item label="单价(含税)">
          <el-input-number v-model="form.price" :min="0" :precision="4" :step="0.1" style="width:150px" placeholder="可选" />
          <span v-if="form.price != null" style="margin-left:6px;font-size:12px;color:#67c23a">不含税￥{{ fmtTax(netOfTax(form.price, form.taxRate ?? taxRate)) }}　税额￥{{ fmtTax(taxOf(form.price, form.taxRate ?? taxRate)) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="入库原因">
          <el-select v-model="form.reason" placeholder="请选择" style="width:100%">
            <el-option v-for="d in dicts.inbound_reason || []" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          <el-input v-model="form.remark" type="textarea" :rows="1" />
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存单据</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { loadTaxRate, netOfTax, taxOf, fmtTax } from '../utils/tax'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const taxRate = ref(13)
loadTaxRate(api).then(r => { taxRate.value = r })
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(25)
const warehouses = ref([])
const materials = ref([])
const zones = ref([])
const locations = ref([])
const visible = ref(false)
const loading = ref(false)
const form = ref({ warehouseId: '', zoneId: '', locationId: '', materialCode: '', materialName: '', batchNo: '', qty: 1, price: null, taxRate: 13, reason: 'OTHER', remark: '' })

function whName(id) { const w = warehouses.value.find(w => String(w.id) === String(id)); return w ? w.name : id }
function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }
function reasonLabel(r) { return dictLabel('inbound_reason', r) }
// v5.27：入库原因走数据字典
const dicts = ref({})
function dictLabel(type, value) {
  const d = (dicts.value[type] || []).find(d => d.value === value)
  return d ? d.label : (value || '-')
}
function statusTagType(s) { return { DONE: 'success', CONFIRMED: 'success', PENDING_QC: 'warning', REJECTED: 'danger', DRAFT: 'info' }[s] || 'info' }
function statusLabel(s) { return { DONE: '已入库', CONFIRMED: '已入库', PENDING_QC: '待质检', REJECTED: '已入不合格库', DRAFT: '草稿' }[s] || '未知' }

async function fetch() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (searchText.value.trim()) params.keyword = searchText.value.trim()
  try {
    const res = await api.get('/outbound/other-inbound', { params })
    rows.value = res.rows
    total.value = res.total
  } catch {} }
function onSearch() { page.value = 1; fetch() }
function openDialog() { form.value = { warehouseId: '', zoneId: '', locationId: '', materialCode: '', materialName: '', batchNo: '', qty: 1, price: null, taxRate: 13, reason: 'OTHER', remark: '' }; zones.value = []; locations.value = []; visible.value = true }

async function onWhChange(whId) {
  form.value.zoneId = ''
  form.value.locationId = ''
  zones.value = []
  locations.value = []
  if (!whId) return
  try { zones.value = await api.get(`/warehouse/${whId}/zone`) } catch {}
}

async function onZoneChange(zoneId) {
  form.value.locationId = ''
  locations.value = []
  if (!zoneId) return
  try { locations.value = await api.get(`/warehouse/zone/${zoneId}/location`) } catch {}
}

function onMatChange(code) { const m = materials.value.find(m => m.code === code); if (m) { form.value.materialName = m.name || '' } }

async function submit() {
  if (!form.value.warehouseId || !form.value.zoneId || !form.value.locationId) { ElMessage.warning('请选择仓库、分库和库位'); return }
  if (!form.value.materialCode) { ElMessage.warning('请选择物料'); return }
  loading.value = true
  try {
    await api.post('/outbound/other-inbound', null, { params: {
      materialCode: form.value.materialCode,
      materialName: form.value.materialName || undefined,
      batchNo: form.value.batchNo || undefined,
      warehouseId: form.value.warehouseId,
      locationId: form.value.locationId || undefined,
      qty: form.value.qty,
      price: form.value.price != null ? form.value.price : undefined,

      taxRate: form.value.taxRate ?? taxRate.value,
      reason: form.value.reason,
      remark: form.value.remark || undefined
    }})
    ElMessage.success('单据已提交质检，请到「质量管理」判定，合格后自动入库')
    visible.value = false
    fetch()
  } catch {} finally { loading.value = false }
}

async function confirm(row) {
  // 其他入库已改为创建即提交质检，QC判定合格后自动入库，无需手动确认
  ElMessage.info('该单据已提交质检，请到「质量管理」判定，合格后自动入库')
}


// ===== 分页（v5.9 后端分页）：搜索关键字传后端模糊匹配 =====
const searchText = ref('')

onMounted(async () => {
  loadTaxRate(api).then(r => { taxRate.value = r })
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch {}
  try { materials.value = await api.get('/material', { params: { enabled: true } }) } catch {}
  try {
    const all = await api.get('/dict')
    const map = {}
    for (const item of all) {
      if (!map[item.type]) map[item.type] = []
      map[item.type].push(item)
    }
    dicts.value = map
  } catch {}
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

.po-head-table :deep(.el-descriptions__label) { width: 110px; background: #f5f7fa; color: #606266; }
.po-head-table :deep(.el-descriptions__content) { padding: 8px 12px; }
.po-head-table :deep(.el-select-dropdown__item) { max-width: 640px; white-space: normal; height: auto; line-height: 1.5; padding: 4px 12px; }
</style>
