<template>
  <div class="page-container">
    <div class="page-header">
      <h2>任务督办</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate" v-if="hasPerm('task:write')">安排任务</el-button>
      </div>
    </div>

    <div class="summary-cards">
      <div class="summary-card"><div class="sc-label">我的待办</div><div class="sc-value">{{ myOpen }}</div></div>
      <div class="summary-card"><div class="sc-label">逾期</div><div class="sc-value red">{{ myOverdue }}</div></div>
      <div class="summary-card"><div class="sc-label">全部进行中</div><div class="sc-value">{{ inProgress }}</div></div>
      <div class="summary-card"><div class="sc-label">本周完成</div><div class="sc-value green">{{ thisWeekDone }}</div></div>
    </div>

    <div class="table-card">
      <div class="filter-bar">
        <el-radio-group v-model="filterTab" size="small">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="mine">我的任务</el-radio-button>
          <el-radio-button value="PENDING">待接收</el-radio-button>
          <el-radio-button value="IN_PROGRESS">进行中</el-radio-button>
          <el-radio-button value="COMPLETED">已完成</el-radio-button>
          <el-radio-button value="CANCELLED">已取消</el-radio-button>
          <el-radio-button value="overdue">逾期</el-radio-button>
        </el-radio-group>
        <el-select v-model="filterPriority" clearable placeholder="优先级" style="width:100px">
          <el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" />
        </el-select>
        <el-input v-model="keyword" placeholder="标题/执行人/创建人" clearable style="width:180px" />
      </div>

      <p-table :data="pagedRows" stripe border style="width:100%" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="task-detail-expand">
              <div class="tde-desc" v-if="row.description">{{ row.description }}</div>
              <div class="tde-timeline" v-if="row.progressList && row.progressList.length">
                <div v-for="p in row.progressList" :key="p.id" class="timeline-item">
                  <span class="tl-time">{{ fmtTime(p.createTime) }}</span>
                  <span class="tl-reporter">{{ p.reporter }}</span>
                  <el-tag size="small" :type="progressTag(p.actionType)">{{ progressLabel(p.actionType) }}</el-tag>
                  <span class="tl-content">{{ p.content }}</span>
                </div>
              </div>
              <div v-else class="tde-empty">暂无进度记录</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="docNo" label="任务号" width="155" show-overflow-tooltip />
        <el-table-column label="任务标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ fontWeight: row.priority === 'HIGH' ? 600 : 400 }">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.priority === 'HIGH' ? 'danger' : row.priority === 'LOW' ? 'info' : 'warning'" size="small">
              {{ row.priority === 'HIGH' ? '高' : row.priority === 'LOW' ? '低' : '中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row)" size="small">{{ statusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行人" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ displayName(row.owner) }}<span v-if="row.collaborators" class="collab">＋{{ row.collaborators.split(',').map(displayName).join('、') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="截止" width="105" align="center">
          <template #default="{ row }">
            <span :class="{ overdue: isOverdue(row) }">{{ row.dueDate || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建人" width="85">
          <template #default="{ row }">{{ row.createdBy || '—' }}</template>
        </el-table-column>
        <el-table-column label="最新进度" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.progressList && row.progressList.length" class="latest-progress">
              {{ row.progressList[row.progressList.length - 1].content }}
            </span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center">
          <template #default="{ row }">
            <button v-if="row.status === 'PENDING' && canOperate(row)" class="op-btn op-btn-success" @click="start(row)">开始</button>
            <button v-if="row.status === 'IN_PROGRESS' && canOperate(row)" class="op-btn op-btn-primary" @click="openReport(row)">汇报</button>
            <button v-if="row.status === 'IN_PROGRESS' && hasPerm('task:write')" class="op-btn op-btn-success" @click="openComplete(row)">确认完成</button>
            <button v-if="hasPerm('task:write') && (row.status === 'PENDING' || row.status === 'IN_PROGRESS')" class="op-btn op-btn-warn" @click="cancel(row)">取消</button>
            <button v-if="hasPerm('task:write') && (row.status === 'COMPLETED' || row.status === 'CANCELLED')" class="op-btn" @click="reopen(row)">重开</button>
            <button v-if="hasPerm('task:write') && row.status === 'PENDING'" class="op-btn op-btn-primary" @click="openEdit(row)">编辑</button>
            <button v-if="hasPerm('task:write') && row.status === 'PENDING'" class="op-btn op-btn-danger" @click="del(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]" :total="filtered.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <!-- 安排/编辑任务 -->
    <el-dialog :title="editing ? '编辑任务 ' + editing.docNo : '安排任务'" v-model="createVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="任务标题" required>
          <el-input v-model="form.title" placeholder="简明任务描述（如：调配 500kg 白漆）" maxlength="200" />
        </el-form-item>
        <el-form-item label="任务详情">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选：具体要求、注意事项" />
        </el-form-item>
        <el-form-item label="主执行人" required>
          <el-select v-model="form.owner" filterable style="width:100%" placeholder="搜索选择（姓名/用户名）">
            <el-option v-for="u in userList" :key="u.username" :label="u.label" :value="u.username" />
          </el-select>
        </el-form-item>
        <el-form-item label="协作人">
          <el-select v-model="form.collaboratorList" multiple filterable style="width:100%" placeholder="可选多人">
            <el-option v-for="u in userList" :key="u.username" :label="u.label" :value="u.username" :disabled="u.username === form.owner" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio value="HIGH">高（急）</el-radio>
            <el-radio value="MEDIUM">中</el-radio>
            <el-radio value="LOW">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :clearable="true" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTask" :loading="loading">保存</el-button>
      </template>
    </el-dialog>

    <!-- 汇报进度 -->
    <el-dialog :title="'汇报进度 · ' + (reportRow?.title || '')" v-model="reportVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-input v-model="reportContent" type="textarea" :rows="4" placeholder="进度描述（如：已完成 60%，预计明天上午完成）" />
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReport" :loading="loading">提交汇报</el-button>
      </template>
    </el-dialog>

    <!-- 完成任务 -->
    <el-dialog :title="'完成任务 · ' + (completeRow?.title || '')" v-model="completeVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-input v-model="completeNote" type="textarea" :rows="3" placeholder="完成结果说明（可选）" />
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="success" @click="submitComplete" :loading="loading">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { statusType as globalStatusType } from '../utils/statusTag'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const list = ref([])
const userList = ref([])
const perms = ref([])
const currentUser = ref('')
const createVisible = ref(false)
const reportVisible = ref(false)
const completeVisible = ref(false)
const loading = ref(false)
const editing = ref(null)
const reportRow = ref(null)
const completeRow = ref(null)
const reportContent = ref('')
const completeNote = ref('')
const filterTab = ref('')
const filterPriority = ref('')
const keyword = ref('')
const form = ref(emptyForm())

function emptyForm() {
  return { title: '', description: '', owner: '', collaboratorList: [], priority: 'MEDIUM', dueDate: '' }
}

function hasPerm(c) { return perms.value.includes(c) }
function fmtTime(t) {
  if (!t) return ''
  if (typeof t === 'number') return new Date(t).toLocaleString('zh-CN', { hour12: false })
  return String(t).replace('T', ' ').slice(0, 16)
}
function displayName(username) {
  const u = userList.value.find(x => x.username === username)
  return u ? u.realName : username
}
function today() { return new Date().toISOString().slice(0, 10) }
function isOverdue(row) {
  return row.dueDate && row.dueDate < today() && row.status !== 'COMPLETED' && row.status !== 'CANCELLED'
}
function statusLabel(row) {
  if (isOverdue(row)) return '逾期'
  return { PENDING: '待接收', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' }[row.status] || row.status
}
// v6.4 状态色统一：全局 + 任务域局部（逾期红优先）
function statusType(row) {
  if (isOverdue(row)) return 'danger'
  return globalStatusType(row.status, { PENDING: 'warning', IN_PROGRESS: 'primary', CANCELLED: 'info' })
}
function progressLabel(t) {
  return { CREATE: '创建', START: '开始', UPDATE: '汇报', COMPLETE: '完成', CANCEL: '取消', REOPEN: '重开' }[t] || t
}
function progressTag(t) {
  return { CREATE: 'info', START: 'primary', UPDATE: 'primary', COMPLETE: 'success', CANCEL: 'warning', REOPEN: 'warning' }[t] || 'info'
}
function canOperate(row) {
  if (hasPerm('task:write')) return true
  return currentUser.value === row.owner || (row.collaborators || '').split(',').includes(currentUser.value)
}

const filtered = computed(() => {
  let arr = list.value
  if (filterTab.value === 'mine') {
    arr = arr.filter(r => currentUser.value === r.owner || (r.collaborators || '').split(',').includes(currentUser.value))
  } else if (filterTab.value === 'overdue') {
    arr = arr.filter(r => isOverdue(r))
  } else if (filterTab.value) {
    arr = arr.filter(r => r.status === filterTab.value)
  }
  if (filterPriority.value) arr = arr.filter(r => r.priority === filterPriority.value)
  if (keyword.value) {
    const kw = keyword.value.trim().toLowerCase()
    arr = arr.filter(r => [r.title, r.owner, r.createdBy, r.docNo].some(v => (v || '').toLowerCase().includes(kw)))
  }
  return arr
})
const myOpen = computed(() => list.value.filter(r => canOperate(r) && r.status !== 'COMPLETED' && r.status !== 'CANCELLED'
  && (currentUser.value === r.owner || (r.collaborators || '').split(',').includes(currentUser.value))).length)
const myOverdue = computed(() => list.value.filter(r => isOverdue(r)
  && (currentUser.value === r.owner || (r.collaborators || '').split(',').includes(currentUser.value))).length)
const inProgress = computed(() => list.value.filter(r => r.status === 'IN_PROGRESS').length)
const thisWeekDone = computed(() => {
  const weekAgo = new Date(Date.now() - 7 * 86400000).toISOString()
  return list.value.filter(r => r.status === 'COMPLETED' && (r.completedAt || '') >= weekAgo).length
})

async function fetch() {
  try { list.value = await api.get('/task') } catch {}
}

function openCreate() {
  editing.value = null
  form.value = emptyForm()
  createVisible.value = true
}
function openEdit(row) {
  editing.value = row
  form.value = {
    title: row.title, description: row.description || '', owner: row.owner,
    collaboratorList: row.collaborators ? row.collaborators.split(',') : [],
    priority: row.priority, dueDate: row.dueDate || ''
  }
  createVisible.value = true
}

async function submitTask() {
  if (!form.value.title) { ElMessage.warning('请填任务标题'); return }
  if (!form.value.owner) { ElMessage.warning('请选主执行人'); return }
  loading.value = true
  const payload = { ...form.value, collaborators: form.value.collaboratorList.join(',') || null }
  delete payload.collaboratorList
  try {
    if (editing.value) await api.put(`/task/${editing.value.id}`, payload)
    else await api.post('/task', payload)
    ElMessage.success('已保存')
    createVisible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

async function start(row) {
  try { await api.post(`/task/${row.id}/start`); ElMessage.success('已开始执行'); fetch() }
  catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败') }
}
function openReport(row) {
  reportRow.value = row
  reportContent.value = ''
  reportVisible.value = true
}
async function submitReport() {
  if (!reportContent.value.trim()) { ElMessage.warning('请填进度内容'); return }
  loading.value = true
  try {
    await api.post(`/task/${reportRow.value.id}/report`, { content: reportContent.value })
    ElMessage.success('已提交汇报')
    reportVisible.value = false
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '汇报失败') }
  finally { loading.value = false }
}
function openComplete(row) {
  completeRow.value = row
  completeNote.value = ''
  completeVisible.value = true
}
async function submitComplete() {
  loading.value = true
  try {
    await api.post(`/task/${completeRow.value.id}/complete`, { note: completeNote.value })
    ElMessage.success('任务已完成')
    completeVisible.value = false
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败') }
  finally { loading.value = false }
}
async function cancel(row) {
  try {
    const { value } = await ElMessageBox.prompt('取消原因（可选）', `取消任务：${row.title}`, { type: 'warning', inputValue: '' })
    await api.post(`/task/${row.id}/cancel`, { reason: value })
    ElMessage.success('已取消')
    fetch()
  } catch (e) { if (e !== 'cancel' && e?.message !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '') }
}
async function reopen(row) {
  try { await api.post(`/task/${row.id}/reopen`); ElMessage.success('已重开'); fetch() }
  catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败') }
}
async function del(row) {
  try { await ElMessageBox.confirm(`确定删除任务「${row.title}」？`, '提示', { type: 'warning' }) } catch { return }
  try { await api.delete(`/task/${row.id}`); ElMessage.success('已删除'); fetch() }
  catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败') }
}

const { page, pageSize, pagedRows, resetPage } = usePaging(filtered)

onMounted(async () => {
  try {
    const u = JSON.parse(localStorage.getItem('user') || '{}')
    perms.value = u.permissions || []
    currentUser.value = u.username || ''
  } catch {}
  fetch()
  try { userList.value = await api.get('/task/users') } catch {}
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 14px 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.sc-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.sc-value { font-size: 22px; font-weight: 700; }
.sc-value.red { color: #ef4444; }
.sc-value.green { color: #16a34a; }
.filter-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; flex-wrap: wrap; }
.collab { color: #64748b; font-size: 12px; }
.overdue { color: #ef4444; font-weight: 600; }
.latest-progress { color: #475569; font-size: 12px; }
.text-muted { color: #94a3b8; }
.task-detail-expand { padding: 8px 24px; background: #fafbfc; }
.tde-desc { font-size: 13px; color: #475569; margin-bottom: 10px; padding: 8px 12px; background: #f1f5f9; border-radius: 6px; }
.tde-timeline { border-left: 2px solid #e2e8f0; padding-left: 14px; }
.timeline-item { display: flex; align-items: baseline; gap: 8px; margin-bottom: 8px; font-size: 12px; }
.tl-time { color: #94a3b8; white-space: nowrap; }
.tl-reporter { color: #334155; font-weight: 500; white-space: nowrap; }
.tl-content { color: #475569; }
.tde-empty { color: #94a3b8; font-size: 12px; padding: 8px 0; }
</style>
