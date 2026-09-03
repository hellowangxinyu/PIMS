<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">异常订单处理</h2>
      <div class="header-actions">
        <el-tooltip placement="top" :content="ruleTip">
          <span class="rule-tip">口径说明 ⓘ</span>
        </el-tooltip>
        <el-button size="small" @click="doExport" :loading="exporting">导出 Excel</el-button>
        <el-button size="small" @click="loadData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <div v-if="loading" class="report-loading">
      <el-skeleton :rows="8" animated />
    </div>

    <template v-else>
      <div class="stat-cards">
        <div class="stat-card red">
          <div class="stat-num">{{ pendingCount }}</div>
          <div class="stat-label">待处理（投出比 &lt; 95%，需分析处置）</div>
        </div>
        <div class="stat-card warn">
          <div class="stat-num">{{ processingCount }}</div>
          <div class="stat-label">处理中</div>
        </div>
        <div class="stat-card total">
          <div class="stat-num">{{ closedCount }}</div>
          <div class="stat-label">已闭环</div>
        </div>
      </div>

      <div class="chart-card full">
        <div class="type-tabs">
          <button class="type-tab" :class="{ active: activeTab === 'OPEN' }" @click="switchTab('OPEN')">处理中 <span class="tab-badge">{{ openCount }}</span></button>
          <button class="type-tab" :class="{ active: activeTab === 'CLOSED' }" @click="switchTab('CLOSED')">已完结 <span class="tab-badge">{{ closedTabCount }}</span></button>
        </div>
        <div class="chart-card-title">{{ activeTab === 'OPEN' ? '待处置异常订单（投入产出比 &lt; 95%）' : '已处置完结的异常订单' }}</div>
        <p-table v-if="filteredRows.length" :data="pagedRows" size="small" border style="width:100%" :row-class-name="rowClass">
          <el-table-column prop="orderNo" label="订单号" min-width="130" />
          <el-table-column prop="productName" label="产品名称" min-width="150" show-overflow-tooltip />
          <el-table-column label="投入量" width="100" align="right">
            <template #default="{ row }">{{ fmtNum(row.inputQty) }}</template>
          </el-table-column>
          <el-table-column label="产出量" width="100" align="right">
            <template #default="{ row }">{{ fmtNum(row.outputQty) }}</template>
          </el-table-column>
          <el-table-column label="投入产出比" width="120" align="center">
            <template #default="{ row }"><el-tag type="danger" size="small">{{ row.ioRatio }}%</el-tag></template>
          </el-table-column>
          <el-table-column label="处置状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.handleStatus)" size="small">{{ statusLabel(row.handleStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="异常原因" min-width="110" show-overflow-tooltip />
          <el-table-column prop="createdBy" label="制单人" width="90" />
          <el-table-column prop="handler" label="处理人" width="90" />
          <el-table-column label="处理时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.handleTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row }">
              <button class="op-btn" :class="row.handleStatus === 'CLOSED' ? 'op-btn-primary' : 'op-btn-success'" @click="openHandle(row)">{{ row.handleStatus === 'CLOSED' ? '查看' : '处理' }}</button>
            </template>
          </el-table-column>
        </p-table>
        <el-empty v-else :description="activeTab === 'OPEN' ? '暂无待处置异常订单（已完工订单投入产出比均 ≥ 95%）' : '暂无已处置完结的异常订单'" :image-size="60" />
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[25, 50, 100]"
            :total="rows.length"
            layout="total, sizes, prev, pager, next"
          />
        </div>
      </div>
    </template>

    <!-- 异常处置弹窗 -->
    <el-dialog :title="'异常处理 — ' + (handleForm.orderNo || '')" v-model="handleVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="handleForm" label-width="90px">
        <el-form-item label="投出比">
          <el-tag type="danger" size="small">{{ handleForm.ioRatio }}%</el-tag>
          <span style="margin-left:8px;color:#64748b;font-size:12px">投入 {{ fmtNum(handleForm.inputQty) }} kg / 产出 {{ fmtNum(handleForm.outputQty) }} kg</span>
        </el-form-item>
        <el-form-item label="异常原因" required>
          <el-select v-model="handleForm.reason" filterable allow-create default-first-option placeholder="选择或输入异常原因" style="width:100%">
            <el-option label="投料过多" value="投料过多" />
            <el-option label="计量误差" value="计量误差" />
            <el-option label="挥发损耗" value="挥发损耗" />
            <el-option label="设备异常" value="设备异常" />
            <el-option label="原料问题" value="原料问题" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置措施">
          <el-input v-model="handleForm.measure" type="textarea" :rows="2" placeholder="如：补料/报废/记录存档/调整工艺" />
        </el-form-item>
        <el-form-item label="处置结果">
          <el-radio-group v-model="handleForm.status">
            <el-radio-button value="PROCESSING">处理中</el-radio-button>
            <el-radio-button value="CLOSED">已闭环</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="handleForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'
import { downloadFile } from '../utils/download'

const loading = ref(false)
const exporting = ref(false)
const rows = ref([])
const pendingCount = ref(0)
const processingCount = ref(0)
const closedCount = ref(0)

const ruleTip = '投入产出比 = 实际产出量(入库总重) ÷ 实际投入量(领料总重) × 100%。' +
  '已完工订单中低于 95% 视为异常（损耗过大/投料过多/计量误差等），需分析原因并处置闭环。' +
  '本表列出所有异常已完工订单，支持填写异常原因、处置措施并标记处理中/已闭环。'

const handleVisible = ref(false)
const submitting = ref(false)
const handleForm = ref({ orderNo: '', ioRatio: null, inputQty: null, outputQty: null, reason: '', measure: '', status: 'PROCESSING', remark: '' })

function fmtNum(v) {
  if (v === null || v === undefined) return '-'
  const n = Number(v)
  return Number.isInteger(n) ? n.toString() : n.toFixed(3).replace(/\.?0+$/, '')
}
function fmtTime(t) { return t ? String(t).replace('T', ' ').substring(0, 16) : '' }
function statusLabel(s) { return { PENDING: '待处理', PROCESSING: '处理中', CLOSED: '已闭环' }[s] || s }
function statusTagType(s) { return { PENDING: 'danger', PROCESSING: 'warning', CLOSED: 'success' }[s] || 'info' }
function rowClass({ row }) { return row.handleStatus === 'CLOSED' ? 'row-closed' : '' }

async function loadData() {
  resetPage()
  loading.value = true
  try {
    const res = await api.get('/abnormal-order')
    rows.value = res.rows || []
    pendingCount.value = res.pendingCount || 0
    processingCount.value = res.processingCount || 0
    closedCount.value = res.closedCount || 0
  } catch (e) { console.error(e) }
  loading.value = false
}

function curUserName() { try { const u = JSON.parse(localStorage.getItem('user') || '{}'); return u.realName || u.username || '' } catch { return '' } }
function openHandle(row) {
  handleForm.value = {
    orderNo: row.orderNo, ioRatio: row.ioRatio, inputQty: row.inputQty, outputQty: row.outputQty,
    reason: row.reason || '', measure: row.measure || '',
    status: row.handleStatus === 'CLOSED' ? 'CLOSED' : 'PROCESSING', remark: row.remark || '',
    handler: curUserName()
  }
  handleVisible.value = true
}

async function submitHandle() {
  if (!handleForm.value.reason) { ElMessage.warning('请选择/填写异常原因'); return }
  submitting.value = true
  try {
    await api.post('/abnormal-order/handle', {
      orderNo: handleForm.value.orderNo,
      reason: handleForm.value.reason,
      measure: handleForm.value.measure,
      status: handleForm.value.status,
      remark: handleForm.value.remark,
      handler: handleForm.value.handler
    })
    ElMessage.success('已提交异常处理')
    handleVisible.value = false
    loadData()
  } catch (e) { /* api 已提示 */ }
  submitting.value = false
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/abnormal-order/export', {}, `异常订单-${new Date().toISOString().slice(0, 10)}.xlsx`)
  } catch (e) { /* downloadFile 内已提示 */ }
  finally { exporting.value = false }
}

// 处理中 / 已完结 Tab 切换（已闭环订单单独一页）
const activeTab = ref('OPEN')
const filteredRows = computed(() => activeTab.value === 'OPEN'
  ? rows.value.filter(r => r.handleStatus !== 'CLOSED')
  : rows.value.filter(r => r.handleStatus === 'CLOSED'))
const openCount = computed(() => rows.value.filter(r => r.handleStatus !== 'CLOSED').length)
const closedTabCount = computed(() => rows.value.filter(r => r.handleStatus === 'CLOSED').length)
const { page, pageSize, pagedRows, resetPage } = usePaging(filteredRows)
function switchTab(t) { activeTab.value = t; resetPage() }
onMounted(() => { loadData() })
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.rule-tip { font-size: 12px; color: #64748b; cursor: help; border-bottom: 1px dashed #94a3b8; }
.report-loading { padding: 40px 20px; }
.stat-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card { border-radius: 16px; padding: 18px 20px; background: var(--pims-card-bg); box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.stat-num { font-size: 28px; font-weight: 800; line-height: 1.2; }
.stat-label { font-size: 12px; color: #64748b; margin-top: 4px; }
.stat-card.red .stat-num { color: #dc2626; }
.stat-card.warn .stat-num { color: #d97706; }
.stat-card.total .stat-num { color: #16a34a; }
.chart-card { background: var(--pims-card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.chart-card-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 16px; }
.type-tabs { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.type-tab { padding: 6px 16px; font-size: 13px; font-weight: 600; border: 1px solid #e2e8f0; border-radius: 6px; cursor: pointer; background: #fff; color: #64748b; transition: all 0.2s; }
.type-tab:hover { color: #334155; border-color: #cbd5e1; }
.type-tab.active { background: #1d4ed8; color: #fff; border-color: #1d4ed8; }
.tab-badge { display: inline-block; min-width: 18px; padding: 0 5px; margin-left: 4px; font-size: 11px; line-height: 16px; border-radius: 9px; background: rgba(0,0,0,0.08); color: inherit; }
.type-tab.active .tab-badge { background: rgba(255,255,255,0.25); }
:deep(.el-table .row-closed > td) { background: #f8fafc !important; }
@media (max-width: 768px) {
  .stat-cards { grid-template-columns: 1fr; }
  .report-header { flex-direction: column; align-items: flex-start; }
}
</style>
