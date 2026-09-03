<template>
  <div class="page-container">
    <div class="page-header">
      <h2>其他出库</h2>
      <el-button type="primary" @click="openDialog">新建出库单</el-button>
    </div>
    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ total }} 条记录</span>
          <el-input v-model="searchText" placeholder="搜索品名/编码/批号" clearable size="small" style="width:220px;margin-left:auto"  @keyup.enter="onSearch" @clear="onSearch" /></div>
      <p-table :data="rows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单据号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="物料编码" min-width="130" />
        <el-table-column prop="materialName" label="品名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="qty" label="出库数量" width="100" align="right" />
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
        <el-table-column label="出库仓库" width="120">
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
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-primary" @click="confirm(row)">确认</button>
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

    <el-dialog title="新建其他出库单" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <!-- v5.78 单据式表格布局 -->
      <el-descriptions :column="2" border size="small" class="po-head-table">
        <el-descriptions-item label="仓库 *">
          <el-select v-model="form.warehouseId" placeholder="选择仓库" style="width:100%" @change="onWhChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="String(w.id)" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="物料 *">
          <el-select v-model="form.materialCode" filterable placeholder="搜索物料" style="width:100%" @change="onMatChange">
            <el-option v-for="m in invList" :key="m.id" :label="m.materialCode + ' ' + (m.materialName||'')" :value="m.materialCode" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="品名"><span>{{ form.materialName || '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="批号 *">
          <el-select v-model="form.batchNo" placeholder="选择出库批号" style="width:100%" @change="onBatchChange">
            <el-option v-for="b in batchOpts" :key="b.batchNo" :label="b.batchNo + ' (库存:' + b.qty + ')￥' + (b.unitPrice != null ? Number(b.unitPrice).toFixed(2) : '-')" :value="b.batchNo" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="当前库存"><span>{{ form.currentQty != null ? form.currentQty : '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="出库数量 *">
          <el-input-number v-model="form.qty" :min="0.001" :max="form.currentQty || 99999" :precision="3" :step="1" style="width:150px" />
        </el-descriptions-item>
        <el-descriptions-item label="出库原因">
          <el-select v-model="form.reason" placeholder="请选择" style="width:100%">
            <el-option v-for="d in dicts.outbound_reason || []" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="备注">
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(25)
const warehouses = ref([])
const invList = ref([])
const visible = ref(false)
const loading = ref(false)
const form = ref({ warehouseId: '', materialCode: '', materialName: '', batchNo: '', currentQty: 0, qty: 1, reason: 'OTHER', remark: '' })
// v5.27：出库原因走数据字典
const dicts = ref({})
function dictLabel(type, value) {
  const d = (dicts.value[type] || []).find(d => d.value === value)
  return d ? d.label : (value || '-')
}
const batchOpts = ref([])

function whName(id) { const w = warehouses.value.find(w => String(w.id) === String(id)); return w ? w.name : id }
function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }
function reasonLabel(r) { return dictLabel('outbound_reason', r) }
function statusTagType(s) { return { CONFIRMED: 'success', PENDING_QC: 'warning', REJECTED: 'danger', DRAFT: 'info' }[s] || 'info' }
function statusLabel(s) { return { CONFIRMED: '已确认', PENDING_QC: '待质检', REJECTED: '质检不合格', DRAFT: '草稿' }[s] || '未知' }

async function fetch() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (searchText.value.trim()) params.keyword = searchText.value.trim()
  try {
    const res = await api.get('/outbound/other', { params })
    rows.value = res.rows
    total.value = res.total
  } catch {} }
function onSearch() { page.value = 1; fetch() }
function openDialog() { form.value = { warehouseId: '', materialCode: '', materialName: '', batchNo: '', currentQty: 0, qty: 1, reason: 'OTHER', remark: '' }; invList.value = []; batchOpts.value = []; visible.value = true }
// v5.18 修复：/inventory 已改远程分页（返回 {rows,total}），改为选中仓库后按仓库拉取该仓全部库存
async function onWhChange(whId) {
  form.value.materialCode = ''; form.value.materialName = ''; form.value.batchNo = ''; form.value.currentQty = 0
  batchOpts.value = []
  invList.value = []
  if (!whId) return
  try {
    const res = await api.get('/inventory', { params: { warehouseId: whId, pageSize: 5000 } })
    invList.value = res.rows || []
    if (invList.value.length === 0) ElMessage.info('该仓库暂无库存')
  } catch { /* ignore */ }
}
function onMatChange(code) {
  const rows = invList.value.filter(r => r.materialCode === code)
  const first = rows[0]
  form.value.materialName = first ? (first.materialName || '') : ''
  form.value.batchNo = ''
  // 按批号聚合可选批次（同一批号可能分布在多个库位）
  const map = {}
  rows.forEach(r => {
    if (!r.batchNo) return
    if (!map[r.batchNo]) map[r.batchNo] = { batchNo: r.batchNo, qty: 0, unitPrice: r.unitPrice }
    map[r.batchNo].qty += Number(r.qty) || 0
    if (r.unitPrice != null) map[r.batchNo].unitPrice = r.unitPrice
  })
  batchOpts.value = Object.values(map)
  form.value.currentQty = rows.reduce((s, r) => s + (Number(r.qty) || 0), 0)
}
// 选中批号后可出库存以该批号为准，实际成本按批号单价直取
function onBatchChange(batchNo) {
  const b = batchOpts.value.find(b => b.batchNo === batchNo)
  form.value.currentQty = b ? b.qty : 0
}

async function submit() {
  if (!form.value.warehouseId || !form.value.materialCode) { ElMessage.warning('请选择仓库和物料'); return }
  // 其他出库必须选择批号，实际成本按批号直取
  if (!form.value.batchNo) { ElMessage.warning('请选择出库批号'); return }
  loading.value = true
  try {
    await api.post('/outbound/other', null, { params: { materialCode: form.value.materialCode, materialName: form.value.materialName, batchNo: form.value.batchNo, warehouseId: form.value.warehouseId, qty: form.value.qty, reason: form.value.reason, remark: form.value.remark || undefined } })
    ElMessage.success('单据已提交质检，请到「质量管理」判定，合格后自动出库')
    visible.value = false
    fetch()
  } catch {} finally { loading.value = false }
}

async function confirm(row) {
  try {
    await ElMessageBox.confirm(`确认出库单 ${row.docNo}？\n确认后将扣减库存。`, '确认出库', { type: 'warning' })
    await api.post(`/outbound/other/${row.id}/confirm`)
    ElMessage.success('已确认，库存已扣减')
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
// v5.7：按品名/编码/批号查询（批号全系统可追溯）
const searchText = ref('')

onMounted(async () => {
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch {}
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
.cost-cell { color: #ea580c; font-weight: 600; }

.po-head-table :deep(.el-descriptions__label) { width: 110px; background: #f5f7fa; color: #606266; }
.po-head-table :deep(.el-descriptions__content) { padding: 8px 12px; }
.po-head-table :deep(.el-select-dropdown__item) { max-width: 640px; white-space: normal; height: auto; line-height: 1.5; padding: 4px 12px; }
</style>
