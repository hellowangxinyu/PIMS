<template>
  <div class="page-container">
    <div class="page-header">
      <h2>报价单</h2>
      <div class="header-actions">
        <el-select v-model="query.customerId" filterable clearable placeholder="按客户筛选" size="small" style="width:200px" @change="fetch">
          <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="按状态筛选" size="small" style="width:130px" @change="fetch">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已报价" value="QUOTED" />
          <el-option label="已转订单" value="ACCEPTED" />
          <el-option label="未接受" value="REJECTED" />
        </el-select>
        <el-button type="primary" @click="openCreate" v-if="hasPerm('sales:write')">新增报价</el-button>
      </div>
    </div>

    <p-table :data="filtered" stripe border size="small" @header-dragend="onHeaderDragend">
      <el-table-column prop="quoteNo" label="报价单号" :width="cw('报价单号') || 120" />
      <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
      <el-table-column prop="materialNames" label="报价内容" min-width="180" show-overflow-tooltip />
      <el-table-column prop="quoteDate" label="报价日期" :width="cw('报价日期') || 100" />
      <el-table-column prop="validUntil" label="有效期至" :width="cw('有效期至') || 100" />
      <el-table-column label="金额" align="right" :width="cw('金额') || 110">
        <template #default="{ row }">￥{{ fmt(row.totalAmount || 0) }}</template>
      </el-table-column>
      <el-table-column label="状态" :width="cw('状态') || 92" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row).type">{{ statusTag(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="salesOrderNo" label="转出订单" :width="cw('转出订单') || 120" />
      <el-table-column prop="createdBy" label="制单人" :width="cw('制单人') || 90" />
      <el-table-column label="操作" width="240" v-if="hasPerm('sales:write')">
        <template #default="{ row }">
          <button class="op-btn op-btn-primary" v-if="row.status === 'DRAFT'" @click="openEdit(row)">编辑</button>
          <button class="op-btn op-btn-success" v-if="row.status === 'DRAFT'" @click="submit(row)">提交报价</button>
          <button class="op-btn op-btn-primary" v-if="row.status === 'QUOTED' && !row.expired" @click="toOrder(row)">转订单</button>
          <button class="op-btn op-btn-warning" v-if="row.status === 'QUOTED'" @click="reject(row)">未接受</button>
          <button class="op-btn" @click="viewDetail(row)">明细</button>
          <button class="op-btn op-btn-danger" v-if="row.status === 'DRAFT'" @click="del(row)">删除</button>
        </template>
      </el-table-column>
    </p-table>
    <div class="pagination-wrap">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="total, prev, pager, next" background />
    </div>

    <!-- 新增/编辑报价 -->
    <el-dialog :title="editing ? '编辑报价 ' + editing.quoteNo : '新增报价'" v-model="dialogVisible" width="min(880px, 94vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="客户" required>
          <el-select v-model="form.customerId" filterable placeholder="选择客户" style="width:100%" @change="onCustChange">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="form.validUntil" type="date" value-format="YYYY-MM-DD" placeholder="默认30天" style="width:100%" />
        </el-form-item>
        <el-form-item label="报价明细" required>
          <div style="width:100%">
            <p-table :data="form.items" border size="small" style="width:100%">
              <el-table-column label="物料" min-width="200">
                <template #default="{ row }">
                  <el-select v-model="row.materialCode" filterable size="small" placeholder="搜索物料" style="width:100%" @change="onItemMatChange(row)">
                    <el-option v-for="m in materials" :key="m.code" :label="m.code + ' ' + m.name" :value="m.code" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.qty" :min="0.001" :precision="3" size="small" controls-position="right" style="width:115px" />
                </template>
              </el-table-column>
              <el-table-column label="单位" width="80">
                <template #default="{ row }">{{ row.unit || 'kg' }}</template>
              </el-table-column>
              <el-table-column label="报价单价" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" controls-position="right" style="width:115px" />
                </template>
              </el-table-column>
              <el-table-column label="小计" width="110" align="right">
                <template #default="{ row }">￥{{ ((Number(row.qty) || 0) * (Number(row.unitPrice) || 0)).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column width="50" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" size="small" @click="form.items.splice($index, 1)">删</el-button>
                </template>
              </el-table-column>
            </p-table>
            <el-button size="small" style="margin-top:6px" @click="addItem">+ 添加明细</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <div class="dialog-total">报价合计：￥{{ totalAmount }}</div>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 查看明细 -->
    <el-dialog :title="'报价明细 ' + (viewing?.quoteNo || '')" v-model="viewVisible" width="min(1100px, 96vw)">
      <p-table :data="viewItems" border size="small">
        <el-table-column prop="materialCode" label="编码" width="110" />
        <el-table-column prop="materialName" label="物料" min-width="160" show-overflow-tooltip />
        <el-table-column prop="qty" label="数量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }">{{ row.unitPrice != null ? '￥' + fmt(row.unitPrice) : '-' }}</template>
        </el-table-column>
        <el-table-column label="小计" width="120" align="right">
          <template #default="{ row }">￥{{ fmt(row.amount || 0) }}</template>
        </el-table-column>
      </p-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

const list = ref([])
const customers = ref([])
const materials = ref([])
const query = ref({ customerId: null, status: '' })
const page = ref(1)
const pageSize = 20
const dialogVisible = ref(false)
const editing = ref(null)
const form = ref({})
const viewVisible = ref(false)
const viewing = ref(null)
const viewItems = ref([])
const perms = ref([])
const { cw, onHeaderDragend } = useColumnResize('quotation')

function hasPerm(code) { return perms.value.includes(code) }

const filtered = computed(() => {
  const kw = ''
  return list.value
})

const totalAmount = computed(() => form.value.items.reduce((s, it) => s + (Number(it.qty) || 0) * (Number(it.unitPrice) || 0), 0).toFixed(2))

function statusTag(row) {
  if (row.status === 'DRAFT') return { label: '草稿', type: 'info' }
  if (row.status === 'ACCEPTED') return { label: '已转订单', type: 'success' }
  if (row.status === 'REJECTED') return { label: '未接受', type: 'danger' }
  if (row.expired) return { label: '已失效', type: 'warning' }
  return { label: '已报价', type: 'primary' }
}

async function fetch() {
  const params = {}
  if (query.value.customerId) params.customerId = query.value.customerId
  if (query.value.status) params.status = query.value.status
  list.value = await api.get('/quotation', { params })
}

function emptyItem() { return { materialCode: '', materialName: '', qty: 1, unit: 'kg', unitPrice: null } }

function openCreate() {
  editing.value = null
  form.value = { customerId: null, validUntil: '', remark: '', items: [emptyItem()] }
  dialogVisible.value = true
}

async function openEdit(row) {
  try {
    const items = await api.get(`/quotation/${row.id}/items`) || []
    form.value = {
      customerId: row.customerId, validUntil: row.validUntil || '', remark: row.remark || '',
      items: items.map(it => ({ materialCode: it.materialCode, materialName: it.materialName, qty: Number(it.qty), unit: it.unit, unitPrice: it.unitPrice != null ? Number(it.unitPrice) : null }))
    }
    if (!form.value.items.length) form.value.items = [emptyItem()]
    editing.value = row
    dialogVisible.value = true
  } catch {}
}

function addItem() { form.value.items.push(emptyItem()) }

function onCustChange() {
  // 切客户后已选物料的价格提示失效，简单起见不动明细
}

// v5.52 选物料自动带出该客户最近成交价（防报错价）
async function onItemMatChange(row) {
  const m = materials.value.find(m => m.code === row.materialCode)
  if (m) { row.materialName = m.name || ''; if (!row.unit && m.unit) row.unit = m.unit }
  if (!form.value.customerId || !row.materialCode) return
  try {
    const r = await api.get('/sales-order/recent-price', { params: { customerId: form.value.customerId, materialCode: row.materialCode } })
    if (r && r.unitPrice != null) {
      row.unitPrice = Number(r.unitPrice)
      ElMessage.info(`已带出最近成交价 ￥${fmt(r.unitPrice)}（${r.orderNo}），可修改`)
    }
  } catch {}
}

async function save() {
  if (!form.value.customerId) { ElMessage.warning('请选择客户'); return }
  const items = form.value.items.filter(it => it.materialCode)
  if (!items.length) { ElMessage.warning('请至少添加一条报价明细'); return }
  const body = {
    customerId: form.value.customerId,
    customerName: customers.value.find(c => c.id === form.value.customerId)?.name,
    validUntil: form.value.validUntil || undefined,
    remark: form.value.remark || undefined,
    items: items.map(it => ({ materialCode: it.materialCode, materialName: it.materialName, qty: Number(it.qty), unit: it.unit, unitPrice: it.unitPrice != null ? Number(it.unitPrice) : null }))
  }
  try {
    if (editing.value) await api.put(`/quotation/${editing.value.id}`, body)
    else await api.post('/quotation', body)
    ElMessage.success('已保存')
    dialogVisible.value = false
    fetch()
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  }
}

async function submit(row) {
  await ElMessageBox.confirm(`提交报价 ${row.quoteNo}？提交后开始计算有效期，仅草稿可编辑。`)
  try {
    await api.post(`/quotation/${row.id}/submit`)
    ElMessage.success('已提交报价')
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

async function toOrder(row) {
  await ElMessageBox.confirm(`将报价 ${row.quoteNo} 转为销售订单？转出后订单为草稿状态，需另行确认发货。`)
  try {
    const q = await api.post(`/quotation/${row.id}/to-order`)
    ElMessage.success(`已生成销售订单 ${q.salesOrderNo}（草稿）`)
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

async function reject(row) {
  await ElMessageBox.confirm(`标记报价 ${row.quoteNo} 为客户未接受？`)
  try {
    await api.post(`/quotation/${row.id}/reject`)
    ElMessage.success('已标记未接受')
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

async function del(row) {
  await ElMessageBox.confirm(`删除草稿报价 ${row.quoteNo}？`)
  try {
    await api.delete(`/quotation/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

async function viewDetail(row) {
  viewing.value = row
  viewItems.value = []
  viewVisible.value = true
  try { viewItems.value = await api.get(`/quotation/${row.id}/items`) || [] } catch {}
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  try { customers.value = await api.get('/customer', { params: { enabled: true } }) } catch {}
  try {
    const mats = await api.get('/material', { params: { enabled: true } })
    materials.value = mats.map(m => ({ code: m.code, name: m.name, unit: m.unit, category: m.category }))
  } catch {}
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
.dialog-total { text-align: right; font-weight: 600; padding: 4px 8px; color: var(--pims-text); }
</style>
