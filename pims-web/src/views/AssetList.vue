<template>
  <div class="page-container">
    <div class="page-header">
      <h2>固定资产</h2>
      <div class="header-actions">
        <el-date-picker v-model="depPeriod" type="month" value-format="YYYY-MM" :clearable="false" style="width:130px" />
        <el-button type="warning" plain @click="depreciate" v-if="hasPerm('finance:write')">月度计提折旧</el-button>
        <el-button @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
        <el-button type="primary" @click="openDialog(null)" v-if="hasPerm('finance:write')">新增资产</el-button>
      </div>
    </div>

    <div class="summary-cards" v-if="hasAmountPerm('asset')">
      <div class="summary-card"><div class="sc-label">在用资产</div><div class="sc-value">{{ inUseCount }} 台</div></div>
      <div class="summary-card"><div class="sc-label">原值合计</div><div class="sc-value">¥{{ fmt(sumOriginal) }}</div></div>
      <div class="summary-card"><div class="sc-label">累计折旧</div><div class="sc-value red">¥{{ fmt(sumDep) }}</div></div>
      <div class="summary-card"><div class="sc-label">净值合计</div><div class="sc-value green">¥{{ fmt(sumOriginal - sumDep) }}</div></div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ list.length }} 张卡片 · 平均年限法：购入次月起提、报废当月照提、提满只提尾差</span></div>
      <p-table :data="list" stripe border style="width:100%">
        <el-table-column prop="docNo" label="编号" width="115" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类别" width="90" align="center">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column prop="purchaseDate" label="购入日期" width="105" />
        <el-table-column label="原值" width="120" align="right" v-if="hasAmountPerm('asset')">
          <template #default="{ row }">¥{{ fmt(row.originalValue) }}</template>
        </el-table-column>
        <el-table-column label="年限(月)" width="80" align="center">
          <template #default="{ row }">{{ row.usefulLifeMonths }}</template>
        </el-table-column>
        <el-table-column label="月折旧" width="110" align="right" v-if="hasAmountPerm('asset')">
          <template #default="{ row }">¥{{ fmt(row.monthlyDep) }}</template>
        </el-table-column>
        <el-table-column label="累计折旧" width="120" align="right" v-if="hasAmountPerm('asset')">
          <template #default="{ row }">¥{{ fmt(row.accumulatedDep) }}</template>
        </el-table-column>
        <el-table-column label="净值" width="120" align="right" v-if="hasAmountPerm('asset')">
          <template #default="{ row }">¥{{ fmt(row.netValue) }}</template>
        </el-table-column>
        <el-table-column label="折旧科目" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.expenseSubject }} {{ row.expenseSubjectName }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'IN_USE' ? 'success' : 'info'" size="small">{{ row.status === 'IN_USE' ? '在用' : '已报废' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="keeper" label="责任人" width="85" />
        <el-table-column label="操作" width="210" align="center" v-if="hasPerm('finance:write')">
          <template #default="{ row }">
            <button class="op-btn" @click="showHistory(row)">折旧记录</button>
            <button class="op-btn op-btn-primary" v-if="row.status === 'IN_USE'" @click="openDialog(row)">编辑</button>
            <button class="op-btn op-btn-danger" v-if="row.status === 'IN_USE'" @click="scrap(row)">报废</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <el-dialog :title="editing ? '编辑资产' : '新增资产'" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required><el-input v-model="form.name" placeholder="如：分散机 F2" /></el-form-item>
        <el-form-item label="类别" required>
          <el-select v-model="form.category" style="width:100%">
            <el-option v-for="c in CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="购入日期">
          <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="原值" required v-if="hasAmountPerm('asset')">
          <el-input-number v-model="form.originalValue" :min="0.01" :precision="2" :step="1000" style="width:100%" />
        </el-form-item>
        <el-form-item label="使用年限" required>
          <el-select v-model="form.usefulLifeMonths" style="width:100%" allow-create filterable placeholder="月数，可直接输入">
            <el-option v-for="y in [12, 24, 36, 48, 60, 120, 180, 240]" :key="y" :label="`${y} 个月（${y / 12} 年）`" :value="y" />
          </el-select>
        </el-form-item>
        <el-form-item label="残值率(%)">
          <el-input-number v-model="form.residualRate" :min="0" :max="50" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="折旧科目">
          <el-select v-model="form.expenseSubject" filterable style="width:100%" placeholder="默认管理费用-折旧费，生产设备选制造费用">
            <el-option v-for="s in subjects" :key="s.code" :label="s.code + ' ' + s.name" :value="s.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="存放位置"><el-input v-model="form.location" /></el-form-item>
        <el-form-item label="责任人"><el-input v-model="form.keeper" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <el-alert v-if="form.originalValue && form.usefulLifeMonths" type="info" :closable="false"
          :title="`月折旧 = 原值 × (1 − 残值率) ÷ 年限 = ¥${fmt(monthlyPreview)}；应提总额 ¥${fmt(totalPreview)}`" />
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer :title="`折旧记录：${historyAsset?.name || ''}`" v-model="historyVisible" size="480px">
      <el-table :data="history" border size="small">
        <el-table-column prop="period" label="期间" width="100" />
        <el-table-column label="月折旧" align="right" v-if="hasAmountPerm('asset')">
          <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="expenseSubject" label="科目" width="100" />
      </el-table>
      <div v-if="!history.length" style="text-align:center;color:#94a3b8;padding:24px">尚无折旧记录</div>
    </el-drawer>
  </div>
</template>

<script setup>
import { todayLocal } from '../utils/date'
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { downloadFile } from '../utils/download'

const list = ref([])
const subjects = ref([])
const perms = ref([])
const visible = ref(false)
const loading = ref(false)
const exporting = ref(false)
const editing = ref(null)
const depPeriod = ref(new Date(Date.now() - 2678400000).toISOString().slice(0, 7))
const historyVisible = ref(false)
const historyAsset = ref(null)
const history = ref([])
const form = ref(emptyForm())

const CATEGORIES = [
  { value: 'BUILDING', label: '房屋建筑' }, { value: 'MACHINE', label: '机器设备' },
  { value: 'VEHICLE', label: '运输工具' }, { value: 'ELECTRONIC', label: '电子设备' },
  { value: 'OTHER', label: '其他' }
]
function categoryLabel(v) { return CATEGORIES.find(c => c.value === v)?.label || v }

function emptyForm() {
  return { name: '', category: 'MACHINE', purchaseDate: '', originalValue: null, usefulLifeMonths: 120,
    residualRate: 5, expenseSubject: '6602.05', location: '', keeper: '', remark: '' }
}

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
const inUseCount = computed(() => list.value.filter(a => a.status === 'IN_USE').length)
const sumOriginal = computed(() => list.value.reduce((s, a) => s + Number(a.originalValue || 0), 0))
const sumDep = computed(() => list.value.reduce((s, a) => s + Number(a.accumulatedDep || 0), 0))
const monthlyPreview = computed(() => calcMonthly(form.value))
const totalPreview = computed(() => {
  const f = form.value
  return Number(f.originalValue || 0) * (1 - Number(f.residualRate || 0) / 100)
})
function calcMonthly(f) {
  if (!f.originalValue || !f.usefulLifeMonths) return 0
  return f.originalValue * (1 - Number(f.residualRate || 0) / 100) / Number(f.usefulLifeMonths)
}

async function fetch() {
  try { list.value = await api.get('/asset') } catch {}
}

function openDialog(row) {
  editing.value = row
  form.value = row
    ? { name: row.name, category: row.category, purchaseDate: row.purchaseDate || '',
        originalValue: Number(row.originalValue) || null, usefulLifeMonths: row.usefulLifeMonths,
        residualRate: Number(row.residualRate) || 0, expenseSubject: row.expenseSubject,
        location: row.location || '', keeper: row.keeper || '', remark: row.remark || '' }
    : emptyForm()
  visible.value = true
}

async function submit() {
  if (!form.value.name) { ElMessage.warning('请填写资产名称'); return }
  if (!form.value.originalValue) { ElMessage.warning('请填写原值'); return }
  loading.value = true
  try {
    if (editing.value) await api.put(`/asset/${editing.value.id}`, form.value)
    else await api.post('/asset', form.value)
    ElMessage.success('已保存')
    visible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

async function scrap(row) {
  try {
    await ElMessageBox.confirm(`报废 ${row.docNo} ${row.name}？报废当月仍计提折旧，次月起停止`, '资产报废', { type: 'warning' })
  } catch { return }
  try {
    await api.put(`/asset/${row.id}/scrap`, {})
    ElMessage.success('已报废')
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败') }
}

async function depreciate() {
  try {
    await ElMessageBox.confirm(`计提 ${depPeriod.value} 折旧？将按卡片逐张计算并生成计提凭证（草稿，需到会计凭证页记账）`, '月度计提', { type: 'info' })
  } catch { return }
  try {
    const r = await api.post('/asset/depreciate', { period: depPeriod.value })
    ElMessage.success(`已计提 ${r.count} 张卡片共 ¥${fmt(r.total)}，凭证 ${r.voucher.docNo} 已生成（草稿）`)
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '计提失败') }
}

async function showHistory(row) {
  historyAsset.value = row
  try { history.value = await api.get(`/asset/${row.id}/history`) } catch {}
  historyVisible.value = true
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/asset/export', {}, `固定资产-${todayLocal()}.xlsx`)
  } catch {} finally { exporting.value = false }
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
  try { subjects.value = (await api.get('/account-subject')).filter(s => s.status === 'ENABLED') } catch {}
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary-card { background: var(--pims-card-bg, #fff); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid var(--pims-card-border, #e2e8f0); }
.sc-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.sc-value { font-size: 20px; font-weight: 700; }
.sc-value.red { color: #ef4444; }
.sc-value.green { color: #16a34a; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
</style>
