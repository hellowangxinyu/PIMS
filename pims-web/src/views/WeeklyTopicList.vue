<template>
  <div class="page-container">
    <div class="page-header">
      <h2>每周议题</h2>
      <div class="header-actions">
        <el-button @click="doExport">导出 Excel</el-button>
        <el-button @click="doImport" :loading="importing" v-if="hasPerm('meeting:write')">导入 Excel</el-button>
        <input ref="fileRef" type="file" accept=".xlsx" style="display:none" @change="onFile" />
        <el-button type="primary" @click="openDialog(null)" v-if="hasPerm('meeting:write')">新增议题</el-button>
      </div>
    </div>

    <div class="summary-cards">
      <div class="summary-card"><div class="sc-label">议题总数</div><div class="sc-value">{{ list.length }}</div></div>
      <div class="summary-card"><div class="sc-value open">{{ openCount }}</div><div class="sc-label">进行中</div></div>
      <div class="summary-card"><div class="sc-value" style="color:#16a34a">{{ closedCount }}</div><div class="sc-label">已结案</div></div>
      <div class="summary-card"><div class="sc-value" style="color:#ef4444">{{ overdueCount }}</div><div class="sc-label">已逾期（未结案且过计划日）</div></div>
    </div>

    <div class="table-card">
      <div class="filters">
        <el-radio-group v-model="statusFilter">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="OPEN">进行中</el-radio-button>
          <el-radio-button value="OVERDUE">逾期</el-radio-button>
          <el-radio-button value="CLOSED">已结案</el-radio-button>
        </el-radio-group>
        <el-select v-model="catFilter" clearable placeholder="分类" style="width:110px">
          <el-option v-for="d in catOptions" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜责任人/事项/结果" clearable style="width:200px" />
      </div>
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="owner" label="责任人" width="90" />
        <el-table-column prop="category" label="分类" width="70" align="center">
          <template #default="{ row }"><el-tag size="small" :type="catTag(row.category)">{{ row.category }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="content" label="待办事项" min-width="260">
          <template #default="{ row }"><div class="cell-full">{{ row.content }}</div></template>
        </el-table-column>
        <el-table-column prop="result" label="结果 / 进展" min-width="260">
          <template #default="{ row }"><div class="cell-full">{{ row.result }}</div></template>
        </el-table-column>
        <el-table-column prop="planDate" label="计划完成" width="105" align="center">
          <template #default="{ row }"><span :class="{ 'overdue-text': st(row) === 'OVERDUE' }">{{ row.planDate || '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="closedDate" label="已结案" width="105" align="center">
          <template #default="{ row }">{{ row.closedDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="st(row) === 'CLOSED' ? 'success' : st(row) === 'OVERDUE' ? 'danger' : 'warning'">
              {{ { OPEN: '进行中', OVERDUE: '逾期', CLOSED: '已结案' }[st(row)] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" v-if="hasPerm('meeting:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="openDialog(row)">编辑</button>
            <button v-if="!row.closedDate" class="op-btn op-btn-success" @click="close(row)">结案</button>
            <button v-else class="op-btn op-btn-warn" @click="close(row)">反结案</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]" :total="filtered.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <el-dialog :title="editing ? '编辑议题' : '新增议题'" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="责任人" required><el-input v-model="form.owner" /></el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" style="width:100%">
            <el-option v-for="d in catOptions" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="待办事项" required><el-input v-model="form.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="结果/进展"><el-input v-model="form.result" type="textarea" :rows="4" placeholder="每周会议更新进展" /></el-form-item>
        <el-form-item label="计划完成"><el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="已结案"><el-date-picker v-model="form.closedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="留空=进行中" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const list = ref([])
const dicts = ref({})
const perms = ref([])
const visible = ref(false)
const loading = ref(false)
const importing = ref(false)
const editing = ref(null)
const statusFilter = ref('')
const catFilter = ref('')
const keyword = ref('')
const fileRef = ref(null)
const form = ref(emptyForm())

function emptyForm() { return { owner: '', category: '生产', content: '', result: '', planDate: '', closedDate: '' } }
function hasPerm(c) { return perms.value.includes(c) }
function catItems() { return dicts.value['weekly_topic_category'] || [] }
const catOptions = computed(() => catItems())
function catTag(c) {
  return { 生产: 'primary', 调色: 'warning', 配方: 'success', 客诉: 'danger', 物料: 'info', 财务: 'info', 物流: 'info', 体系: 'info' }[c] || 'info'
}
function st(row) {
  if (row.closedDate) return 'CLOSED'
  if (row.planDate && row.planDate < today()) return 'OVERDUE'
  return 'OPEN'
}
function today() { return new Date().toISOString().slice(0, 10) }

const filtered = computed(() => {
  let arr = list.value
  if (statusFilter.value) arr = arr.filter(r => st(r) === statusFilter.value)
  if (catFilter.value) arr = arr.filter(r => r.category === catFilter.value)
  const kw = keyword.value.trim().toLowerCase()
  if (kw) arr = arr.filter(r => [r.owner, r.content, r.result, r.category].some(v => v && v.toLowerCase().includes(kw)))
  return arr
})
const openCount = computed(() => list.value.filter(r => st(r) === 'OPEN').length)
const closedCount = computed(() => list.value.filter(r => st(r) === 'CLOSED').length)
const overdueCount = computed(() => list.value.filter(r => st(r) === 'OVERDUE').length)

async function fetch() {
  resetPage()
  try { list.value = await api.get('/meeting/topic') } catch {}
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
  } catch {}
}

function openDialog(row) {
  editing.value = row
  form.value = row ? { owner: row.owner, category: row.category, content: row.content, result: row.result || '',
    planDate: row.planDate || '', closedDate: row.closedDate || '' } : emptyForm()
  visible.value = true
}

async function submit() {
  if (!form.value.owner) { ElMessage.warning('请填责任人'); return }
  if (!form.value.content) { ElMessage.warning('请填待办事项'); return }
  loading.value = true
  try {
    if (editing.value) await api.put(`/meeting/topic/${editing.value.id}`, form.value)
    else await api.post('/meeting/topic', form.value)
    ElMessage.success('保存成功')
    visible.value = false
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败') }
  finally { loading.value = false }
}

async function close(row) {
  try {
    await api.post(`/meeting/topic/${row.id}/close`, null, { params: { close: !row.closedDate } })
    ElMessage.success(row.closedDate ? '已反结案' : '已结案')
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败') }
}

function doImport() { fileRef.value?.click() }

async function doExport() {
  try { await (await import('../utils/download')).downloadFile('/meeting/topic/export', {}, `每周议题-${new Date().toISOString().slice(0, 10)}.xlsx`) } catch {}
}
async function onFile(e) {
  const f = e.target.files?.[0]
  e.target.value = ''
  if (!f) return
  importing.value = true
  try {
    const fd = new FormData()
    fd.append('file', f)
    const msg = await api.post('/meeting/topic/import', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    ElMessage.success(msg || '导入完成')
    fetch()
  } catch (err) { ElMessage.error(err?.response?.data?.msg || err?.message || '导入失败') }
  finally { importing.value = false }
}

const { page, pageSize, pagedRows, resetPage } = usePaging(filtered)

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
  fetchDicts()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 14px 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); text-align: center; }
.sc-label { font-size: 12px; color: #64748b; margin-top: 4px; }
.sc-value { font-size: 22px; font-weight: 700; }
.sc-value.open { color: #f59e0b; }
.table-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.filters { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.overdue-text { color: #ef4444; font-weight: 600; }
.cell-full { white-space: pre-wrap; word-break: break-all; line-height: 1.6; }
</style>
