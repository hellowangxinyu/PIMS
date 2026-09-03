<template>
  <div class="page-container">
    <div class="page-header">
      <h2>请购单</h2>
      <span class="sub">MRP 建议与手工请购统一在此（草稿可删；确认内容后转采购下单）</span>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate" v-if="hasPerm('purchase:write')">新增请购单</el-button>
      </div>
    </div>

    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索单号 / 备注" clearable style="width:220px" />
      <el-select v-model="statusFilter" placeholder="状态" clearable style="width:130px">
        <el-option v-for="(label, s) in PO_STATUS_MAP" :key="s" :label="label" :value="s" />
      </el-select>
      <span class="count-tip">共 {{ paged.length }} 条（第 {{ page }} / {{ totalPages }} 页）</span>
    </div>

    <div class="table-card">
      <p-table :data="paged" stripe border @header-dragend="onHeaderDragend">
        <el-table-column prop="orderNo" label="单号" :width="cw('单号') || 150" />
        <el-table-column label="供应商" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ supplierName(row) }}</template>
        </el-table-column>
        <el-table-column prop="orderDate" label="订单日期" :width="cw('订单日期') || 110" align="center" />
        <el-table-column prop="totalAmount" label="金额" :width="cw('金额') || 130" align="right" v-if="hasAmountPerm">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" :width="cw('状态') || 100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="poStatusType(row.status)">{{ poStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" :width="cw('创建时间') || 165">
          <template #default="{ row }">{{ String(row.createTime || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <button class="op-btn" @click="showItems(row)">明细</button>
            <button v-if="row.status === 'DRAFT' && hasPerm('purchase:write')" class="op-btn op-btn-danger" @click="del(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pager" v-if="totalPages > 1">
        <el-pagination layout="prev, pager, next" :total="filtered.length" :page-size="pageSize" v-model:current-page="page" />
      </div>
    </div>

    <!-- 明细弹窗 -->
    <el-dialog :title="`请购明细 ${viewRow?.orderNo || ''}`" v-model="itemsVisible" width="640px">
      <p-table :data="viewItems" border size="small" style="width:100%">
        <el-table-column prop="materialCode" label="物料编码" min-width="130" />
        <el-table-column label="物料名称" min-width="160">
          <template #default="{ row }">{{ matName(row.materialCode) }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="receivedQty" label="已到货" width="90" align="right" />
      </p-table>
    </el-dialog>

    <!-- 新增弹窗 -->
    <el-dialog title="新增请购单" v-model="createVisible" width="720px">
      <el-form :model="form" label-width="90px" inline>
        <el-form-item label="供应商">
          <el-select v-model="form.supplierId" filterable clearable placeholder="可空（待定）" style="width:220px">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单日期"><el-date-picker v-model="form.orderDate" value-format="YYYY-MM-DD" style="width:150px" /></el-form-item>
        <el-form-item label="交货日期"><el-date-picker v-model="form.expectedDeliveryDate" value-format="YYYY-MM-DD" style="width:150px" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" style="width:240px" /></el-form-item>
      </el-form>
      <el-divider style="margin:8px 0" />
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
        <b style="font-size:13px">请购明细</b>
        <el-button size="small" @click="addItem">+ 添加物料</el-button>
      </div>
      <el-table :data="form.items" border size="small" style="width:100%">
        <el-table-column label="物料" min-width="240">
          <template #default="{ row }">
            <el-select v-model="row.materialCode" filterable size="small" style="width:100%">
              <el-option v-for="m in materials" :key="m.code" :label="m.code + ' ' + (m.name || '')" :value="m.code" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="150">
          <template #default="{ row }"><el-input-number v-model="row.qty" :min="0.001" :precision="3" size="small" style="width:120px" /></template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }"><el-button size="small" link type="danger" @click="form.items.splice($index, 1)">删</el-button></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
// v6.4 补齐：搜索/筛选/分页/明细查看/手工创建/草稿删除（原 56 行半成品页，alert 占位）
import { statusType } from '../utils/statusTag'
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

const list = ref([])
const perms = ref([])
const keyword = ref('')
const statusFilter = ref('')
const page = ref(1)
const pageSize = 20
const suppliers = ref([])
const materials = ref([])
const itemsVisible = ref(false)
const viewRow = ref(null)
const viewItems = ref([])
const createVisible = ref(false)
const form = ref({ supplierId: null, orderDate: new Date().toISOString().slice(0, 10), expectedDeliveryDate: null, remark: '', items: [] })
const { cw, onHeaderDragend } = useColumnResize('purchase_order')

function hasPerm(c) { return perms.value.includes(c) }
const hasAmountPerm = computed(() => perms.value.includes('purchase:amount') || perms.value.includes('finance:amount'))
const PO_STATUS_MAP = { DRAFT: '草稿', APPROVED: '已审核', RECEIVED: '已到货', CLOSED: '已关闭', CANCELLED: '已取消' }
function poStatusLabel(s) { return s ? (PO_STATUS_MAP[s] || '未知') : '-' }
// v6.4 状态色统一（原 APPROVED=橙与采购他页绿相反，误导操作判断）
const poStatusType = statusType

const filtered = computed(() => {
  let rows = list.value
  if (keyword.value) {
    const k = keyword.value.toLowerCase()
    rows = rows.filter(r => (r.orderNo || '').toLowerCase().includes(k) || (r.remark || '').includes(keyword.value))
  }
  if (statusFilter.value) rows = rows.filter(r => r.status === statusFilter.value)
  return rows
})
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

function supplierName(row) {
  if (row.supplierId == null) return '待定'
  const s = suppliers.value.find(x => x.id === row.supplierId)
  return s ? s.name : '供应商#' + row.supplierId
}
function matName(code) {
  const m = materials.value.find(x => x.code === code)
  return m ? (m.name || code) : code
}

async function fetch() {
  try { list.value = await api.get('/purchase-order') } catch {}
}

async function showItems(row) {
  viewRow.value = row
  try { viewItems.value = await api.get(`/purchase-order/${row.id}/items`) } catch { viewItems.value = [] }
  itemsVisible.value = true
}

function openCreate() {
  form.value = { supplierId: null, orderDate: new Date().toISOString().slice(0, 10), expectedDeliveryDate: null, remark: '', items: [{ materialCode: '', qty: 1 }] }
  createVisible.value = true
}
function addItem() { form.value.items.push({ materialCode: '', qty: 1 }) }

async function save() {
  const items = form.value.items.filter(it => it.materialCode && it.qty > 0)
  if (!items.length) { ElMessage.warning('请至少添加一条明细'); return }
  try {
    await api.post('/purchase-order', { ...form.value, items })
    ElMessage.success('请购单已保存（草稿）')
    createVisible.value = false
    fetch()
  } catch (e) {}
}

async function del(row) {
  try {
    await ElMessageBox.confirm(`删除草稿请购单 ${row.orderNo}？`, '删除', { type: 'warning' })
    await api.delete(`/purchase-order/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) {}
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
  try { suppliers.value = await api.get('/supplier', { params: { enabled: true } }) } catch {}
  try { const m = await api.get('/material', { params: { page: 1, size: 500 } }); materials.value = m.rows || (Array.isArray(m) ? m : []) } catch {}
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.page-header h2 { margin: 0; font-size: 20px; }
.sub { font-size: 12px; color: #94a3b8; }
.header-actions { margin-left: auto; }
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.count-tip { font-size: 12px; color: #94a3b8; }
.pager { display: flex; justify-content: flex-end; margin-top: 10px; }
</style>
