<template>
  <div class="page-container">
    <div class="page-header">
      <h2>客户投诉</h2>
      <div class="header-actions">
        <el-select v-model="query.status" clearable placeholder="按状态筛选" size="small" style="width:130px" @change="fetch">
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="已处理" value="RESOLVED" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-button type="primary" @click="openCreate" v-if="hasPerm('complaint:write')">登记投诉</el-button>
      </div>
    </div>

    <p-table :data="list" stripe border size="small" @header-dragend="onHeaderDragend">
      <el-table-column prop="complaintNo" label="投诉单号" :width="cw('投诉单号') || 118" />
      <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
      <el-table-column prop="materialName" label="涉及物料" min-width="120" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="批号" :width="cw('批号') || 110" />
      <el-table-column prop="category" label="分类" :width="cw('分类') || 92" align="center" />
      <el-table-column prop="description" label="问题描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="complaintDate" label="投诉日期" :width="cw('投诉日期') || 96" />
      <el-table-column label="状态" :width="cw('状态') || 82" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="{ PROCESSING: 'danger', RESOLVED: 'warning', CLOSED: 'success' }[row.status]">{{ { PROCESSING: '处理中', RESOLVED: '已处理', CLOSED: '已关闭' }[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdBy" label="制单人" :width="cw('制单人') || 78" />
      <el-table-column prop="handler" label="处理人" :width="cw('处理人') || 78" />
      <el-table-column label="操作" width="210" v-if="hasPerm('complaint:write')">
        <template #default="{ row }">
          <button class="op-btn op-btn-primary" v-if="row.status === 'PROCESSING'" @click="openEdit(row)">编辑</button>
          <button class="op-btn op-btn-success" v-if="row.status === 'PROCESSING'" @click="openResolve(row)">处理</button>
          <button class="op-btn op-btn-success" v-if="row.status === 'RESOLVED'" @click="closeRow(row)">关闭</button>
          <button class="op-btn op-btn-trace" v-if="row.batchNo" @click="openTrace(row)">追溯</button>
          <button class="op-btn" @click="openDetail(row)">详情</button>
          <button class="op-btn op-btn-danger" v-if="row.status === 'PROCESSING'" @click="del(row)">删除</button>
        </template>
      </el-table-column>
    </p-table>

    <!-- 登记/编辑 -->
    <el-dialog :title="editing ? '编辑投诉 ' + editing.complaintNo : '登记客户投诉'" v-model="dialogVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="客户" required>
          <el-select v-model="form.customerName" filterable allow-create default-first-option placeholder="选择客户或输入" style="width:100%" @change="onCustChange">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="涉及物料">
          <el-select v-model="form.materialCode" filterable clearable placeholder="选择物料（批号追溯必填）" style="width:100%" @change="onMatChange">
            <el-option v-for="m in materials" :key="m.code" :label="m.code + ' ' + m.name" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="批号">
          <el-input v-model="form.batchNo" placeholder="涉及批次，如 B20260815-001（追溯用）" />
        </el-form-item>
        <el-form-item label="质检单号">
          <el-input v-model="form.qcDocNo" placeholder="可选，关联质检单" />
        </el-form-item>
        <el-form-item label="销售订单号">
          <el-input v-model="form.salesOrderNo" placeholder="可选，关联订单" />
        </el-form-item>
        <el-form-item label="投诉日期">
          <el-date-picker v-model="form.complaintDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width:100%" clearable>
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题描述" required>
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="客户反映的具体问题：色差/性能/结块等" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 处理 -->
    <el-dialog :title="'处理 ' + (opRow?.complaintNo || '')" v-model="resolveVisible" width="min(1100px, 96vw)">
      <el-form label-width="80px">
        <el-form-item label="原因分析" required>
          <el-input v-model="resolve.cause" type="textarea" :rows="3" placeholder="根因：如调色偏差/原料批次问题/储存不当" />
        </el-form-item>
        <el-form-item label="处理措施" required>
          <el-input v-model="resolve.action" type="textarea" :rows="3" placeholder="如：补货 50kg/退货冲减/现场重新调色" />
        </el-form-item>
        <el-form-item label="处理人"><el-input v-model="resolve.handler" /></el-form-item>
        <el-form-item label="处理日期"><el-date-picker v-model="resolve.resolveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible=false">取消</el-button>
        <el-button type="primary" @click="submitResolve">标记已处理</el-button>
      </template>
    </el-dialog>

    <!-- 批次追溯 -->
    <el-drawer :title="'批次追溯 ' + (opRow?.batchNo || '') + '（' + (opRow?.materialCode || '') + '）'" v-model="traceVisible" size="min(760px, 96vw)">
      <el-alert type="info" :closable="false" show-icon title="该批次的全部出入库流水（含已归档），用于定位问题货的去向与剩余库存" style="margin-bottom:12px" />
      <el-table :data="traceList" size="small" border>
        <el-table-column prop="createTime" label="时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="业务类型" width="100">
          <template #default="{ row }">{{ docTypeMap[row.docType] || row.docType }}</template>
        </el-table-column>
        <el-table-column prop="docNo" label="单据号" width="130" />
        <el-table-column label="方向" width="60" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.direction === 'IN' ? 'success' : 'danger'">{{ row.direction === 'IN' ? '入' : '出' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="90" align="right" />
        <el-table-column prop="warehouseId" label="仓库" width="110" />
        <el-table-column prop="locationId" label="库位" width="110" />
        <el-table-column prop="operator" label="操作人" width="80" />
      </el-table>
    </el-drawer>

    <!-- 详情 -->
    <el-drawer :title="'投诉详情 ' + (viewing?.complaintNo || '')" v-model="detailVisible" size="min(560px, 96vw)">
      <el-descriptions :column="1" border size="small" v-if="viewing">
        <el-descriptions-item label="客户">{{ viewing.customerName }}</el-descriptions-item>
        <el-descriptions-item label="涉及物料">{{ viewing.materialCode ? viewing.materialCode + ' ' + (viewing.materialName || '') : '-' }}</el-descriptions-item>
        <el-descriptions-item label="批号">{{ viewing.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="质检单">{{ viewing.qcDocNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="销售订单">{{ viewing.salesOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ viewing.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="问题描述">{{ viewing.description }}</el-descriptions-item>
        <el-descriptions-item label="原因分析">{{ viewing.cause || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理措施">{{ viewing.action || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理人/日期">{{ (viewing.handler || '-') + ' / ' + (viewing.resolveDate || '-') }}</el-descriptions-item>
        <el-descriptions-item label="关闭日期">{{ viewing.closeDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ viewing.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

const CATEGORIES = ['色差', '性能不达标', '结块沉淀', '包装破损', '交期延误', '其他']

const list = ref([])
const customers = ref([])
const materials = ref([])
const query = ref({ status: '' })
const dialogVisible = ref(false)
const editing = ref(null)
const form = ref({})
const opRow = ref(null)
const resolveVisible = ref(false)
const resolve = ref({})
const traceVisible = ref(false)
const traceList = ref([])
const detailVisible = ref(false)
const viewing = ref(null)
const perms = ref([])
const { cw, onHeaderDragend } = useColumnResize('customer_complaint')

function hasPerm(code) { return perms.value.includes(code) }

function fmtTime(t) {
  if (!t) return '-'
  // 24 小时制、显示到分钟（秒不显示）
  return String(t).replace('T', ' ').substring(0, 16)
}

const docTypeMap = {
  PURCHASE_IN: '采购入库', OUTSOURCE_OUT: '委外出库', OUTSOURCE_IN: '委外入库',
  SALES_OUT: '销售出库', PRODUCTION_OUT: '生产领料', OTHER_OUT: '其他出库', REWORK_OUT: '返工领料',   // v6.9.1
  OTHER_IN: '其他入库', TRANSFER: '调拨', ADJUSTMENT: '盘点调整'
}

async function fetch() {
  const params = {}
  if (query.value.status) params.status = query.value.status
  list.value = await api.get('/complaint', { params })
}

function openCreate() {
  editing.value = null
  form.value = { customerName: '', customerId: null, materialCode: '', materialName: '', batchNo: '', qcDocNo: '', salesOrderNo: '', complaintDate: '', category: '', description: '', remark: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  form.value = { customerName: row.customerName, customerId: row.customerId, materialCode: row.materialCode, materialName: row.materialName, batchNo: row.batchNo, qcDocNo: row.qcDocNo, salesOrderNo: row.salesOrderNo, complaintDate: row.complaintDate || '', category: row.category, description: row.description, remark: row.remark }
  dialogVisible.value = true
}

function onCustChange(val) {
  const c = customers.value.find(c => c.name === val)
  form.value.customerId = c ? c.id : null
}

function onMatChange(code) {
  const m = materials.value.find(m => m.code === code)
  form.value.materialName = m ? m.name : ''
}

async function save() {
  if (!form.value.customerName) { ElMessage.warning('请填写客户'); return }
  if (!form.value.description) { ElMessage.warning('请填写问题描述'); return }
  const body = { ...form.value }
  try {
    if (editing.value) await api.put(`/complaint/${editing.value.id}`, body)
    else await api.post('/complaint', body)
    ElMessage.success('已保存')
    dialogVisible.value = false
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

function openResolve(row) { opRow.value = row; resolve.value = { cause: '', action: '', handler: '', resolveDate: '' }; resolveVisible.value = true }
async function submitResolve() {
  try {
    await api.post(`/complaint/${opRow.value.id}/resolve`, resolve.value)
    ElMessage.success('已标记处理')
    resolveVisible.value = false
    fetch()
  } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

async function closeRow(row) {
  await ElMessageBox.confirm(`关闭投诉 ${row.complaintNo}？关闭前请确认客户已认可处理结果。`)
  try { await api.post(`/complaint/${row.id}/close`); ElMessage.success('已关闭'); fetch() } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

async function openTrace(row) {
  opRow.value = row
  traceList.value = []
  traceVisible.value = true
  try { traceList.value = await api.get(`/complaint/${row.id}/trace`) || [] } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

function openDetail(row) { viewing.value = row; detailVisible.value = true }

async function del(row) {
  await ElMessageBox.confirm(`删除投诉登记 ${row.complaintNo}？`)
  try { await api.delete(`/complaint/${row.id}`); ElMessage.success('已删除'); fetch() } catch (e) { if (e && e.message) ElMessage.error(e.message) }
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  try { customers.value = await api.get('/customer', { params: { enabled: true } }) } catch {}
  try {
    const mats = await api.get('/material', { params: { enabled: true } })
    materials.value = mats.map(m => ({ code: m.code, name: m.name }))
  } catch {}
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
</style>
