<template>
  <div class="page-container">
    <div class="page-header">
      <h2>工资管理</h2>
      <div class="header-actions">
        <el-button @click="doExport" :disabled="exporting || !list.length">{{ exporting ? '导出中…' : '导出 Excel' }}</el-button>
        <el-button type="primary" @click="openCreate" v-if="hasPerm('finance:write')">新建工资单</el-button>
      </div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ list.length }} 期 · 流程：新建（自动带出在职员工）→ 编辑构成 → 确认 → 生成计提/发放凭证 → 到会计凭证页记账</span></div>
      <p-table :data="list" stripe border style="width:100%">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="entry-expand">
              <table class="entry-table">
                <thead><tr>
                  <th>姓名</th><th>部门</th><th>基本</th><th>奖金补贴</th><th>计件</th><th>扣款</th>
                  <th>应发</th><th>代扣社保</th><th>代扣个税</th><th>实发</th>
                </tr></thead>
                <tbody>
                  <tr v-for="i in row.items" :key="i.id">
                    <td>{{ i.employeeName }}</td><td>{{ deptLabel(i.dept) }}</td>
                    <td class="amt">{{ fmt(i.base) }}</td><td class="amt">{{ fmt(i.bonus) }}</td>
                    <td class="amt">{{ fmt(i.piecework) }}</td><td class="amt">{{ fmt(i.deduction) }}</td>
                    <td class="amt"><b>{{ fmt(i.gross) }}</b></td><td class="amt">{{ fmt(i.socialIns) }}</td>
                    <td class="amt">{{ fmt(i.incomeTax) }}</td><td class="amt"><b>{{ fmt(i.net) }}</b></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="docNo" label="单号" width="150" />
        <el-table-column prop="period" label="期间" width="100" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'info'" size="small">{{ row.status === 'CONFIRMED' ? '已确认' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="90" />
        <el-table-column prop="confirmedBy" label="确认人" width="90" />
        <el-table-column label="应发合计" width="130" align="right" v-if="hasAmountPerm('salary')">
          <template #default="{ row }">¥{{ fmt(row.totalGross) }}</template>
        </el-table-column>
        <el-table-column label="实发合计" width="130" align="right" v-if="hasAmountPerm('salary')">
          <template #default="{ row }">¥{{ fmt(row.totalNet) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip />
        <el-table-column label="操作" width="330" align="center" v-if="hasPerm('finance:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" v-if="row.status === 'DRAFT'" @click="openEdit(row)">编辑</button>
            <button class="op-btn op-btn-success" v-if="row.status === 'DRAFT'" @click="confirm(row)">确认</button>
            <button class="op-btn" v-if="row.status === 'CONFIRMED'" @click="genVoucher(row, 'accrual')">计提凭证</button>
            <button class="op-btn" v-if="row.status === 'CONFIRMED'" @click="genVoucher(row, 'pay')">发放凭证</button>
            <button class="op-btn op-btn-danger" v-if="row.status === 'DRAFT'" @click="del(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <!-- 新建弹窗：选期间 -->
    <el-dialog title="新建工资单" v-model="createVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="期间" required>
          <el-date-picker v-model="newPeriod" type="month" value-format="YYYY-MM" :clearable="false" style="width:100%" />
        </el-form-item>
        <el-alert type="info" :closable="false" title="将自动带出全部在职员工（基本工资预填），计件/奖金/扣款等在编辑里填" />
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="doCreate" :loading="loading">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑明细 -->
    <el-dialog :title="`编辑工资单 ${editing?.docNo || ''}（${editing?.period || ''}）`" v-model="editVisible"
      width="min(1060px, 96vw)" destroy-on-close :close-on-click-modal="false">
      <el-table :data="items" border size="small" style="width:100%">
        <el-table-column prop="employeeName" label="姓名" width="100" />
        <el-table-column label="部门" width="80" align="center">
          <template #default="{ row }">{{ deptLabel(row.dept) }}</template>
        </el-table-column>
        <el-table-column label="基本工资" width="120" align="right">
          <template #default="{ row }"><el-input-number v-model="row.base" :precision="2" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="奖金补贴" width="120" align="right">
          <template #default="{ row }"><el-input-number v-model="row.bonus" :precision="2" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="计件工资" width="120" align="right">
          <template #default="{ row }"><el-input-number v-model="row.piecework" :precision="2" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="扣款" width="110" align="right">
          <template #default="{ row }"><el-input-number v-model="row.deduction" :precision="2" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="应发" width="110" align="right">
          <template #default="{ row }"><b>{{ fmt(rowGross(row)) }}</b></template>
        </el-table-column>
        <el-table-column label="代扣社保" width="120" align="right">
          <template #default="{ row }"><el-input-number v-model="row.socialIns" :precision="2" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="代扣个税" width="120" align="right">
          <template #default="{ row }"><el-input-number v-model="row.incomeTax" :precision="2" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="实发" width="110" align="right">
          <template #default="{ row }"><b>{{ fmt(rowGross(row) - Number(row.socialIns || 0) - Number(row.incomeTax || 0)) }}</b></template>
        </el-table-column>
        <el-table-column label="" width="50" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="items.splice($index, 1)">✕</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="entry-footer">
        <span class="type-count">{{ items.length }} 人</span>
        <div class="total-bar">
          <span>应发合计：<b>¥{{ fmt(sumGross) }}</b></span>
          <span>实发合计：<b>¥{{ fmt(sumNet) }}</b></span>
        </div>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveItems" :loading="loading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { downloadFile } from '../utils/download'

const list = ref([])
const perms = ref([])
const loading = ref(false)
const exporting = ref(false)
const createVisible = ref(false)
const editVisible = ref(false)
const editing = ref(null)
const newPeriod = ref(new Date(Date.now() - 2678400000).toISOString().slice(0, 7))   // 默认上个月
const items = ref([])

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function rowGross(r) { return Number(r.base || 0) + Number(r.bonus || 0) + Number(r.piecework || 0) - Number(r.deduction || 0) }
const DEPTS = { PRODUCTION: '生产', SALES: '销售', ADMIN: '行政', TECH: '技术', QC: '质检', OTHER: '其他' }
function deptLabel(v) { return DEPTS[v] || v }

const sumGross = computed(() => items.value.reduce((s, r) => s + rowGross(r), 0))
const sumNet = computed(() => items.value.reduce((s, r) => s + rowGross(r) - Number(r.socialIns || 0) - Number(r.incomeTax || 0), 0))

async function fetch() {
  try { list.value = await api.get('/salary') } catch {}
}

function openCreate() {
  newPeriod.value = new Date(Date.now() - 2678400000).toISOString().slice(0, 7)
  createVisible.value = true
}

async function doCreate() {
  loading.value = true
  try {
    await api.post('/salary', { period: newPeriod.value })
    ElMessage.success('工资单已创建，请编辑构成项')
    createVisible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '创建失败')
  } finally { loading.value = false }
}

function openEdit(row) {
  editing.value = row
  items.value = (row.items || []).map(i => ({ ...i }))
  editVisible.value = true
}

async function saveItems() {
  loading.value = true
  try {
    await api.put(`/salary/${editing.value.id}`, { items: items.value })
    ElMessage.success('已保存')
    editVisible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

async function confirm(row) {
  try {
    await ElMessageBox.confirm(`确认 ${row.period} 工资单？确认后锁定不可修改，可生成计提/发放凭证`, '确认工资单', { type: 'warning' })
  } catch { return }
  try {
    await api.put(`/salary/${row.id}/confirm`)
    ElMessage.success('已确认')
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '确认失败') }
}

async function genVoucher(row, kind) {
  const label = kind === 'accrual' ? '计提' : '发放'
  try {
    await ElMessageBox.confirm(`生成 ${row.period} 工资${label}凭证（草稿，需到会计凭证页记账）？`, label + '凭证', { type: 'info' })
  } catch { return }
  try {
    const v = await api.post(`/salary/${row.id}/${kind}-voucher`)
    ElMessage.success(`凭证 ${v.docNo} 已生成，请到「会计凭证」页记账`)
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '生成失败') }
}

async function del(row) {
  try { await ElMessageBox.confirm(`确定删除 ${row.period} 工资单？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await api.delete(`/salary/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败') }
}

async function doExport() {
  const period = list.value[0]?.period
  if (!period) return
  exporting.value = true
  try {
    await downloadFile('/salary/export', { period }, `工资表-${period}.xlsx`)
  } catch {} finally { exporting.value = false }
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.type-tabs { display: flex; align-items: center; margin-bottom: 16px; padding: 12px 16px; background: #fafafa; border-radius: 8px; }
.type-count { font-size: 13px; color: #64748b; }
.entry-expand { padding: 4px 24px 8px; background: #fafbfc; }
.entry-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.entry-table th, .entry-table td { border: 1px solid #e4e7ed; padding: 5px 10px; }
.entry-table th { background: #f5f7fa; color: #606266; font-weight: 500; }
.entry-table .amt { text-align: right; font-variant-numeric: tabular-nums; }
.entry-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }
.total-bar { display: flex; align-items: center; gap: 22px; font-size: 13px; color: #303133; }
</style>
