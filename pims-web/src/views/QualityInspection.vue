<template>
  <div class="page-container">
    <div class="page-header">
      <h2>质检管理</h2>
      <div class="header-actions">
        <el-button size="small" @click="doExport" :loading="exporting">导出 Excel</el-button>
      </div>
    </div>

    <!-- 顶部说明：所有质检均为入库前质检 -->
    <div class="qc-tip">入库前质检：采购入库 / 生产入库 / 委外入库 / 其他入库 均需质检合格方可入库；按被检物料类型分 材料 / 半成品 / 成品 三类</div>

    <!-- 分类 Tab：材料 / 半成品 / 成品 -->
    <el-tabs v-model="activeCategory" @tab-change="handleCategoryChange" class="qc-tabs">
      <el-tab-pane v-for="cat in CATEGORIES" :key="cat.code" :name="cat.code">
        <template #label>
          {{ cat.label }}
          <span v-if="pendingCount[cat.code]" class="badge">{{ pendingCount[cat.code] }}</span>
        </template>

        <!-- 分类内部：待质检 / 已质检 -->
        <el-tabs v-model="catStates[cat.code].activeTab" @tab-change="name => handleInnerTabChange(cat.code, name)" class="inner-tabs">
          <!-- ===== 待质检 ===== -->
          <el-tab-pane label="待质检" name="pending">
            <div class="table-card">
              <p-table :data="catStates[cat.code].pending.list" stripe border v-loading="catStates[cat.code].pending.loading" style="width:100%">
                <el-table-column prop="inspectionNo" label="质检单号" min-width="150" show-overflow-tooltip />
                <el-table-column prop="refDocNo" label="关联单号" min-width="140" show-overflow-tooltip />
                <el-table-column prop="refDocType" label="来源" width="110">
                  <template #default="{ row }">{{ sourceLabel(row.refDocType) }}</template>
                </el-table-column>
                <el-table-column prop="materialName" label="品名" min-width="150" show-overflow-tooltip />
                <el-table-column prop="materialCode" label="编码" min-width="120" show-overflow-tooltip />
                <el-table-column prop="qty" label="数量" width="100" align="right" />
                <el-table-column prop="batchNo" label="批次" width="110" show-overflow-tooltip />
                <el-table-column label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdBy" label="制单人" width="90" />
                <el-table-column prop="createTime" label="创建时间" min-width="150" show-overflow-tooltip>
                  <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center" fixed="right">
                  <template #default="{ row }">
                    <button v-if="row.status === 'PENDING'" class="op-btn op-btn-primary" @click="openJudge(row)">质检判定</button>
                    <span v-else class="text-muted">已判定</span>
                  </template>
                </el-table-column>
              </p-table>
              <div class="pagination-bar">
                <el-pagination
                  v-model:current-page="catStates[cat.code].pending.page"
                  v-model:page-size="catStates[cat.code].pending.size"
                  :page-sizes="[25, 50, 100]"
                  :total="catStates[cat.code].pending.total"
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="fetchPending(cat.code)"
                  @current-change="fetchPending(cat.code)"
                />
              </div>
            </div>
          </el-tab-pane>

          <!-- ===== 已质检（历史） ===== -->
          <el-tab-pane label="已质检" name="history">
            <div class="search-card">
              <el-form :inline="true" :model="catStates[cat.code].history.search" size="small">
                <el-form-item label="质检单号"><el-input v-model="catStates[cat.code].history.search.inspectionNo" placeholder="质检单号" clearable @keyup.enter="onSearch(cat.code)" /></el-form-item>
                <el-form-item label="关联单号"><el-input v-model="catStates[cat.code].history.search.refDocNo" placeholder="关联单号" clearable @keyup.enter="onSearch(cat.code)" /></el-form-item>
                <el-form-item label="物料编码"><el-input v-model="catStates[cat.code].history.search.materialCode" placeholder="物料编码" clearable @keyup.enter="onSearch(cat.code)" /></el-form-item>
                <el-form-item label="品名"><el-input v-model="catStates[cat.code].history.search.materialName" placeholder="物料品名" clearable @keyup.enter="onSearch(cat.code)" /></el-form-item>
                <el-form-item label="批次"><el-input v-model="catStates[cat.code].history.search.batchNo" placeholder="批次" clearable @keyup.enter="onSearch(cat.code)" /></el-form-item>
                <el-form-item label="检验员"><el-input v-model="catStates[cat.code].history.search.inspector" placeholder="检验员" clearable @keyup.enter="onSearch(cat.code)" /></el-form-item>
                <el-form-item label="判定结果">
                  <el-select v-model="catStates[cat.code].history.search.status" placeholder="判定结果" clearable style="width:120px">
                    <el-option label="合格" value="PASS" />
                    <el-option label="让步接收" value="CONCESSION" />
                    <el-option label="不合格" value="REJECT" />
                  </el-select>
                </el-form-item>
                <el-form-item label="检验日期">
                  <el-date-picker v-model="catStates[cat.code].history.search.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="onSearch(cat.code)">查询</el-button>
                  <el-button @click="onReset(cat.code)">重置</el-button>
                </el-form-item>
              </el-form>
            </div>
            <div class="table-card">
              <p-table :data="catStates[cat.code].history.list" stripe border v-loading="catStates[cat.code].history.loading" style="width:100%">
                <el-table-column prop="inspectionNo" label="质检单号" min-width="150" show-overflow-tooltip />
                <el-table-column prop="refDocNo" label="关联单号" min-width="140" show-overflow-tooltip />
                <el-table-column prop="materialName" label="品名" min-width="150" show-overflow-tooltip />
                <el-table-column prop="materialCode" label="编码" min-width="120" show-overflow-tooltip />
                <el-table-column prop="qty" label="数量" width="100" align="right" />
                <el-table-column prop="batchNo" label="批次" width="110" show-overflow-tooltip />
                <el-table-column label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="inspector" label="检验员" width="90" />
                <el-table-column prop="printCount" label="打印次数" width="90" align="center" />
                <el-table-column prop="inspectDate" label="检验日期" width="110" />
                <el-table-column prop="resultRemark" label="检测结果" min-width="140" show-overflow-tooltip />
                <el-table-column label="操作" width="90" align="center" fixed="right">
                  <template #default="{ row }">
                    <button v-if="row.status !== 'PENDING'" class="op-btn op-btn-primary" @click="printQc(row)">打印</button>
                    <span v-else class="text-muted">-</span>
                  </template>
                </el-table-column>
              </p-table>
              <div class="pagination-bar">
                <el-pagination
                  v-model:current-page="catStates[cat.code].history.page"
                  v-model:page-size="catStates[cat.code].history.size"
                  :page-sizes="[25, 50, 100]"
                  :total="catStates[cat.code].history.total"
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="fetchHistory(cat.code)"
                  @current-change="fetchHistory(cat.code)"
                />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>
    </el-tabs>

    <!-- 质检判定弹窗（v5.32：加宽容纳检测项表格） -->
    <el-dialog title="质检判定" v-model="judgeVisible" width="min(840px, 94vw)" destroy-on-close>
      <el-form :model="judgeForm" label-width="90px">
        <el-form-item label="质检单号">
          <el-input :value="judgeForm.inspectionNo" disabled />
        </el-form-item>
        <el-form-item label="关联单号">
          <el-input :value="judgeForm.refDocNo" disabled />
        </el-form-item>
        <el-form-item label="品名">
          <el-input :value="judgeForm.materialName" disabled />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="judgeForm.batchNo" placeholder="默认到货批号；未带出时请按实物包装批号补填" maxlength="30" clearable />
          <div class="form-tip">判定合格入库时台账沿用此批号；留空则自动生成</div>
        </el-form-item>
        <el-form-item label="数量">
          <el-input :value="judgeForm.qty" disabled />
        </el-form-item>
        <!-- v5.32：检测项实测值（按物料大类质检模板快照，逐项填写） -->
        <el-form-item v-if="judgeItems.length" label="检测项">
          <table class="qc-items">
            <thead>
              <tr>
                <th style="width:32px">#</th>
                <th>检验项目</th>
                <th style="width:140px">标准要求</th>
                <th style="width:52px">单位</th>
                <th style="width:120px">实测值</th>
                <th style="width:96px">单项判定</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(it, i) in judgeItems" :key="it.id">
                <td class="c">{{ i + 1 }}</td>
                <td>{{ it.name }}<div v-if="it.method" class="m">{{ it.method }}</div></td>
                <td>{{ it.standard || '-' }}</td>
                <td class="c">{{ it.unit || '-' }}</td>
                <td><el-input v-model="it.measuredValue" size="small" placeholder="实测值" /></td>
                <td>
                  <el-select v-model="it.itemResult" size="small" placeholder="—" clearable>
                    <el-option label="合格" value="PASS" />
                    <el-option label="不合格" value="FAIL" />
                  </el-select>
                </td>
              </tr>
            </tbody>
          </table>
        </el-form-item>
        <el-form-item label="判定结果" required>
          <el-radio-group v-model="judgeForm.result">
            <el-radio value="PASS">{{ isReinspection ? '合格（恢复可用）' : '合格' }}</el-radio>
            <el-radio value="CONCESSION">{{ isReinspection ? '让步接收（恢复可用）' : '让步接收' }}</el-radio>
            <el-radio value="REJECT">{{ isReinspection ? '不合格（维持隔离）' : '不合格' }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- v5.37：过期复检合格/让步时填写新的有效期——更新台账过期日期即自动恢复出库 -->
        <el-form-item v-if="isReinspection && judgeForm.result !== 'REJECT'" label="复检有效期" required>
          <el-date-picker v-model="judgeForm.reexpiryDate" type="date" value-format="YYYY-MM-DD"
            placeholder="复检合格后批次可使用至该日期" style="width:100%" />
          <div class="form-tip">填写复检后新的到期日：到期日前批次恢复可用（可正常出库），到期后再次自动隔离</div>
        </el-form-item>
        <!-- v5.31：不合格品精确到库位——判定不合格时选择不合格品库的实际存放库位 -->
        <!-- v5.97.1 仅生产/委外/其他入库的不合格才入隔离库（要选库位）；来料采购不合格直接生成退货单，无库存无账 -->
        <el-form-item v-if="judgeForm.result === 'REJECT' && ['PRODUCTION_INBOUND','OUTSOURCE_INBOUND','OTHER_INBOUND'].includes(judgeForm.refDocType)" label="存放库位">
          <el-select v-model="judgeForm.unqualifiedLocationId" clearable placeholder="留空=自动分配" style="width:100%">
            <el-option v-for="l in unqualifiedLocations" :key="l.id" :label="l.name" :value="String(l.id)" />
          </el-select>
          <div class="form-tip">已自动分配默认库位，无需修改；仅当实物堆放在其他库位时才需要改选（留空=自动）</div>
        </el-form-item>
        <div v-if="judgeForm.result === 'REJECT' && judgeForm.refDocType === 'PURCHASE'" class="form-tip reinspect-tip">
          来料不合格：判定后自动生成采购退货单（货未入库、无库存账务），由采购员审核后退回供应商
        </div>
        <div v-if="isReinspection && judgeForm.result === 'REJECT'" class="form-tip reinspect-tip">
          复检不合格：批次维持过期隔离（禁止正常出库），请通过「其他出库-报废」处理
        </div>
        <el-form-item label="检测结果" :required="!hasMeasuredInput">
          <el-input v-model="judgeForm.resultRemark" type="textarea" :rows="3"
            :placeholder="judgeItems.length ? '选填：已填检测项实测值时留空将自动按实测值汇总' : '请填写检测结果（必填），如：外观正常、粘度合格等'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="judgeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitJudge" :loading="submitting">确认判定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { todayLocal } from '../utils/date'
import { statusType as globalStatusType } from '../utils/statusTag'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { buildQcReportHtml } from '../utils/qcPrint'
import { downloadFile } from '../utils/download'

// v5.23：导出当前分类 Tab 下的质检记录——待质检导出全部 PENDING；已质检导出当前搜索条件全量
const exporting = ref(false)
async function doExport() {
  exporting.value = true
  try {
    const params = { category: activeCategory.value }
    const st = catStates[activeCategory.value]
    if (st && st.activeTab === 'pending') {
      params.status = 'PENDING'
    } else if (st) {
      const s = st.history.search
      if (s.status) params.status = s.status
      if (s.inspectionNo) params.inspectionNo = s.inspectionNo
      if (s.refDocNo) params.refDocNo = s.refDocNo
      if (s.materialCode) params.materialCode = s.materialCode
      if (s.materialName) params.materialName = s.materialName
      if (s.batchNo) params.batchNo = s.batchNo
      if (s.inspector) params.inspector = s.inspector
      if (s.dateRange?.[0]) params.startDate = s.dateRange[0]
      if (s.dateRange?.[1]) params.endDate = s.dateRange[1]
    }
    await downloadFile('/qc/export', params, `质检记录-${todayLocal()}.xlsx`)
  } catch (e) { /* downloadFile 内已提示 */ }
  finally { exporting.value = false }
}

// ==================== 质检单打印（v5.24，正式质检报告样式） ====================

// 仓库 ID → 名称映射（打印报告用，加载失败时兜底显示 ID）
const warehouses = ref([])
function whName(id) {
  if (!id) return '-'
  const w = warehouses.value.find(w => String(w.id) === String(id))
  return w ? w.name : String(id)
}

// 生成质检报告打印 HTML（v5.32：抽到 utils/qcPrint.js 与质检模板预览共用）
// 打开打印窗口（仿配方打印：新窗口渲染 + window.print + 自动关闭）
async function printQc(row) {
  // v5.26：记录打印次数（失败不阻断打印）
  api.post('/print-count', { docType: 'QUALITY_INSPECTION', docNo: row.inspectionNo })
    .then(n => { row.printCount = n }).catch(() => {})
  // v5.32：取检测项快照（已判定单含实测值；失败退回空白表手填）
  let items = []
  try { items = await api.get(`/qc/${row.id}/items`) || [] } catch { /* 空白表兜底 */ }
  const win = window.open('', '_blank')
  if (!win) { ElMessage.warning('浏览器拦截了弹窗，请允许本站弹出窗口'); return }
  win.document.write(buildQcReportHtml({ ...row, warehouseName: whName(row.warehouseId) }, items))
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); win.close() }, 200)
}

// ===== 分类定义（v5.0：材料 / 半成品 / 成品） =====
const CATEGORIES = [
  { code: 'A,P,F,R,S', label: '材料质检', catLabel: '材料' },
  { code: 'B', label: '半成品质检', catLabel: '半成品' },
  { code: 'C', label: '成品质检', catLabel: '成品' }
]
const activeCategory = ref(CATEGORIES[0].code)

// 每个分类的待检数量徽标（进入页面时并行统计）
const pendingCount = reactive({})
CATEGORIES.forEach(c => { pendingCount[c.code] = 0 })

// 每个分类独立的 待质检/已质检 状态
// 注意：reactive 会自动解包嵌套 ref，此处用纯对象 + reactive 深响应（避免 .value 失效）
function newCatState() {
  return {
    activeTab: 'pending',
    pending: { list: [], page: 1, size: 20, total: 0, loading: false },
    history: {
      list: [], page: 1, size: 20, total: 0, loading: false,
      search: { inspectionNo: '', refDocNo: '', materialCode: '', materialName: '', batchNo: '', inspector: '', status: '', dateRange: null }
    }
  }
}
const catStates = reactive({})
CATEGORIES.forEach(c => { catStates[c.code] = newCatState() })

// ===== 判定弹窗 =====
const judgeVisible = ref(false)
const submitting = ref(false)
const judgeForm = ref({ id: null, inspectionNo: '', materialName: '', qty: '', result: 'PASS', resultRemark: '' })
// v5.32：检测项（判定弹窗逐项填实测值；后端按物料大类模板快照，无模板时为空数组走纯文本判定）
const judgeItems = ref([])
const hasMeasuredInput = computed(() => judgeItems.value.some(i => i.measuredValue && String(i.measuredValue).trim()))

// 状态文案/标签颜色
// v6.6 收口：全局映射（PENDING 待检橙/PASS 绿/CONCESSION 让步橙/REJECT 红，与全局 MAP 一致）
const statusTagType = globalStatusType;
function statusLabel(s) {
  const map = { PENDING: '待检', PASS: '合格', CONCESSION: '让步接收', REJECT: '不合格' }
  return map[s] || '未知'
}
// 质检来源文案
function sourceLabel(t) {
  const map = { PURCHASE: '采购到货', PRODUCTION_INBOUND: '生产入库', OUTSOURCE_INBOUND: '委外入库', OTHER_INBOUND: '其他入库', REINSPECTION: '过期复检' }
  return map[t] || t || '-'
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

// ===== 待质检查询（按分类） =====
async function fetchPending(catCode) {
  const st = catStates[catCode].pending
  st.loading = true
  try {
    const res = await api.get('/qc/search', {
      params: { status: 'PENDING', category: catCode, page: st.page - 1, size: st.size }
    })
    st.list = res.content || []
    st.total = res.totalElements || 0
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '加载待质检列表失败')
  } finally { st.loading = false }
}

// ===== 已质检查询（按分类，多条件） =====
async function fetchHistory(catCode) {
  const st = catStates[catCode].history
  st.loading = true
  try {
    const s = st.search
    const res = await api.get('/qc/search', {
      params: {
        category: catCode,
        page: st.page - 1,
        size: st.size,
        inspectionNo: s.inspectionNo || undefined,
        refDocNo: s.refDocNo || undefined,
        materialCode: s.materialCode || undefined,
        materialName: s.materialName || undefined,
        batchNo: s.batchNo || undefined,
        inspector: s.inspector || undefined,
        status: s.status || undefined,
        startDate: s.dateRange?.[0] || undefined,
        endDate: s.dateRange?.[1] || undefined,
      }
    })
    st.list = res.content || []
    st.total = res.totalElements || 0
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '加载已质检列表失败')
  } finally { st.loading = false }
}

// ===== 待检数量徽标 =====
async function refreshPendingCount(catCode) {
  try {
    const res = await api.get('/qc/search', {
      params: { status: 'PENDING', category: catCode, page: 0, size: 1 }
    })
    pendingCount[catCode] = res.totalElements || 0
  } catch { pendingCount[catCode] = 0 }
}

// ===== Tab 切换：懒加载 =====
function handleCategoryChange(catCode) {
  const st = catStates[catCode]
  if (st.pending.list.length === 0) fetchPending(catCode)
  if (st.activeTab === 'history' && st.history.list.length === 0) fetchHistory(catCode)
}

function handleInnerTabChange(catCode, name) {
  const st = catStates[catCode]
  if (name === 'history' && st.history.list.length === 0) fetchHistory(catCode)
  if (name === 'pending' && st.pending.list.length === 0) fetchPending(catCode)
}

// ===== 搜索/重置 =====
function onSearch(catCode) { catStates[catCode].history.page = 1; fetchHistory(catCode) }
function onReset(catCode) {
  catStates[catCode].history.search = {
    inspectionNo: '', refDocNo: '', materialCode: '', materialName: '',
    batchNo: '', inspector: '', status: '', dateRange: null
  }
  catStates[catCode].history.page = 1
  fetchHistory(catCode)
}

// ===== 判定 =====
// v5.31：不合格品库的可用库位（判定不合格时选择实际存放库位，精确到库位）
const unqualifiedLocations = ref([])
// v5.38.3：不合格品库按物料大类分三个——原材料(A/P/F/R/S)/半成品(B)/成品(C)各一个，按质检单类别+来源仓取库位
function unqZoneType(category) {
  if (category === 'B') return 'UNQUALIFIED_SEMI'
  if (category === 'C') return 'UNQUALIFIED_FIN'
  return 'UNQUALIFIED_RAW'
}
async function fetchUnqualifiedLocations(warehouseId, materialCategory) {
  try {
    unqualifiedLocations.value = await api.get('/warehouse/isolated-locations',
      { params: { type: unqZoneType(materialCategory), ...(warehouseId ? { warehouseId } : {}) } }) || []
  } catch { unqualifiedLocations.value = [] }
}

function openJudge(row) {
  judgeForm.value = { id: row.id, inspectionNo: row.inspectionNo, refDocNo: row.refDocNo, materialName: row.materialName, batchNo: row.batchNo, qty: row.qty, refDocType: row.refDocType, warehouseId: row.warehouseId, materialCategory: row.materialCategory, result: 'PASS', resultRemark: '', unqualifiedLocationId: '', reexpiryDate: '' }
  if (row.refDocType !== 'REINSPECTION') {
    fetchUnqualifiedLocations(row.warehouseId, row.materialCategory).then(() => {
      // 默认选中第一个库位（隔离区·不合格品位）
      if (unqualifiedLocations.value.length) judgeForm.value.unqualifiedLocationId = String(unqualifiedLocations.value[0].id)
    })
  }
  // v5.32：加载检测项（存量待检单无快照时后端按当前默认模板补建）
  judgeItems.value = []
  api.get(`/qc/${row.id}/items`).then(list => { judgeItems.value = list || [] }).catch(() => { judgeItems.value = [] })
  judgeVisible.value = true
}

// v5.37：过期复检单（只更新原台账，不涉及不合格品库位）
const isReinspection = computed(() => judgeForm.value.refDocType === 'REINSPECTION')

async function submitJudge() {
  if (!judgeForm.value.result) { ElMessage.warning('请选择判定结果'); return }
  // v5.32：填了任一检测项实测值时检测结果可不填（后端自动按实测值汇总）
  if (!hasMeasuredInput.value && (!judgeForm.value.resultRemark || !judgeForm.value.resultRemark.trim())) {
    ElMessage.warning('请填写检测结果，或至少填写一项检测项实测值'); return
  }
  if (isReinspection.value && judgeForm.value.result !== 'REJECT' && !judgeForm.value.reexpiryDate) {
    ElMessage.warning('复检合格/让步必须填写复检后有效期'); return
  }
  if (!isReinspection.value && judgeForm.value.result === 'REJECT' && unqualifiedLocations.value.length && !judgeForm.value.unqualifiedLocationId) {
    ElMessage.warning('请选择不合格品的存放库位'); return
  }
  submitting.value = true
  try {
    const body = {
      result: judgeForm.value.result,
      resultRemark: judgeForm.value.resultRemark || '',
      batchNo: judgeForm.value.batchNo || '',
      items: judgeItems.value.map(i => ({ id: i.id, measuredValue: i.measuredValue || '', itemResult: i.itemResult || '' }))
    }
    if (judgeForm.value.result === 'REJECT') body.unqualifiedLocationId = judgeForm.value.unqualifiedLocationId || ''
    if (isReinspection.value && judgeForm.value.result !== 'REJECT') body.reexpiryDate = judgeForm.value.reexpiryDate
    await api.post(`/qc/${judgeForm.value.id}/judge`, body)
    // 判定联动提示
    if (isReinspection.value) {
      ElMessage.success(judgeForm.value.result === 'REJECT'
        ? '复检不合格：批次维持过期隔离，请走「其他出库-报废」处理'
        : `复检合格：批次已恢复可用，有效期更新至 ${judgeForm.value.reexpiryDate}`)
    } else if (judgeForm.value.result === 'REJECT') {
      if (judgeForm.value.refDocType === 'PURCHASE') {
        ElMessage.success('已判定不合格，已生成采购退货单（草稿），货物将退回供应商')
      } else if (['PRODUCTION_INBOUND', 'OUTSOURCE_INBOUND', 'OTHER_INBOUND'].includes(judgeForm.value.refDocType)) {
        ElMessage.success('已判定不合格，货物已转入「不合格品库」，可走其他出库-报废/退货处理')
      } else {
        ElMessage.success('判定完成')
      }
    } else {
      ElMessage.success('判定成功')
    }
    judgeVisible.value = false
    // 刷新当前分类的待检列表与徽标
    fetchPending(activeCategory.value)
    refreshPendingCount(activeCategory.value)
  } catch (e) { /* ignore */ }
  submitting.value = false
}

onMounted(async () => {
  // 并行统计三个分类的待检数量徽标
  CATEGORIES.forEach(c => refreshPendingCount(c.code))
  // 默认加载第一个分类（材料质检）的待检列表
  fetchPending(activeCategory.value)
  // v5.24：加载仓库列表用于打印报告时 ID→名称映射（失败静默，打印兜底显示 ID）
  try { warehouses.value = (await api.get('/warehouse')).filter(w => w.enabled !== false) } catch {}
})
</script>

<style scoped>
.page-container { width: 100%; }
.qc-tip {
  background: #eef2ff; color: #4338ca; border-radius: 6px;
  padding: 8px 14px; font-size: 13px; margin-bottom: 12px;
  border: 1px solid #c7d2fe;
}
.badge {
  display: inline-block; min-width: 18px; height: 18px; line-height: 18px;
  border-radius: 9px; background: #ef4444; color: #fff; font-size: 11px;
  text-align: center; padding: 0 5px; margin-left: 4px; vertical-align: middle;
}
.inner-tabs { margin-top: 4px; }
.search-card { background: #fff; padding: 12px 16px 0; border-radius: 8px; margin-bottom: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.04); }
.pagination-bar { display: flex; justify-content: flex-end; padding: 12px 0 4px; }
.text-muted { color: #9ca3af; font-size: 12px; }
.form-tip { font-size: 12px; color: #94a3b8; line-height: 1.5; margin-top: 2px; }
.reinspect-tip { color: #b45309; background: #fffbeb; border: 1px solid #fde68a; border-radius: 6px; padding: 8px 10px; margin: 4px 0 12px; }
/* v5.32：判定弹窗检测项表格 */
.qc-items { width: 100%; border-collapse: collapse; }
.qc-items th, .qc-items td { border: 1px solid #e2e8f0; padding: 5px 8px; font-size: 13px; vertical-align: middle; }
.qc-items th { background: #f8fafc; color: #475569; font-weight: 600; text-align: left; }
.qc-items .c { text-align: center; color: #64748b; }
.qc-items .m { font-size: 11px; color: #94a3b8; margin-top: 1px; }
.qc-items tbody tr:hover { background: #f8fafc; }
</style>
