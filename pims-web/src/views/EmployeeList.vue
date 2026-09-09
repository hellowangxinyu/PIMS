<template>
  <div class="page-container">
    <div class="page-header">
      <h2>员工档案</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openDialog(null)" v-if="hasPerm('finance:write')">新增员工</el-button>
      </div>
    </div>

    <div class="table-card">
      <div class="type-tabs"><span class="type-count">共 {{ list.length }} 人 · 部门决定工资计提的费用科目（生产→直接人工，销售→销售费用，其他→管理费用）</span></div>
      <p-table :data="list" stripe border style="width:100%">
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column label="部门" width="90" align="center">
          <template #default="{ row }">{{ deptLabel(row.dept) }}</template>
        </el-table-column>
        <el-table-column prop="position" label="岗位" width="120" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="hireDate" label="入职日期" width="110" />
        <el-table-column prop="leaveDate" label="离职日期" width="110">
          <template #default="{ row }">{{ row.leaveDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="基本工资" width="120" align="right" v-if="hasAmountPerm('employee')">
          <template #default="{ row }">¥{{ fmt(row.baseSalary) }}</template>
        </el-table-column>
        <el-table-column prop="bankCard" label="银行卡" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status === 'ENABLED' ? '在职' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="200" align="center" v-if="hasPerm('finance:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="openDialog(row)">编辑</button>
            <button class="op-btn" :class="row.status === 'ENABLED' ? 'op-btn-warn' : 'op-btn-primary'" @click="toggle(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </button>
            <button class="op-btn op-btn-danger" @click="del(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <el-dialog :title="editing ? '编辑员工' : '新增员工'" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="部门" required>
          <el-select v-model="form.dept" style="width:100%">
            <el-option v-for="d in deptOptions" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位"><el-input v-model="form.position" placeholder="可选" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="入职日期" required>
          <el-date-picker v-model="form.hireDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="离职日期">
          <el-date-picker v-model="form.leaveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="可选，离职后工资单不再带出" />
        </el-form-item>
        <el-form-item label="基本工资" v-if="hasAmountPerm('employee')">
          <el-input-number v-model="form.baseSalary" :min="0" :precision="2" :step="500" style="width:100%" />
        </el-form-item>
        <el-form-item label="银行卡"><el-input v-model="form.bankCard" placeholder="可选" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const list = ref([])
const dicts = ref({})
const perms = ref([])
const visible = ref(false)
const loading = ref(false)
const editing = ref(null)
const form = ref(emptyForm())

function emptyForm() {
  return { name: '', dept: 'PRODUCTION', position: '', phone: '', hireDate: '', leaveDate: '',
    baseSalary: 0, bankCard: '', remark: '' }
}

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
const deptOptions = ref([
  { value: 'PRODUCTION', label: '生产' }, { value: 'SALES', label: '销售' }, { value: 'ADMIN', label: '行政' },
  { value: 'TECH', label: '技术' }, { value: 'QC', label: '质检' }, { value: 'OTHER', label: '其他' }
])
function deptLabel(v) { return deptOptions.value.find(d => d.value === v)?.label || v }

async function fetch() {
  try { list.value = await api.get('/employee') } catch {}
}

function openDialog(row) {
  editing.value = row
  form.value = row
    ? { name: row.name, dept: row.dept, position: row.position || '', phone: row.phone || '',
        hireDate: row.hireDate || '', leaveDate: row.leaveDate || '', baseSalary: Number(row.baseSalary) || 0,
        bankCard: row.bankCard || '', remark: row.remark || '' }
    : emptyForm()
  visible.value = true
}

async function submit() {
  if (!form.value.name) { ElMessage.warning('请填写姓名'); return }
  loading.value = true
  try {
    if (editing.value) await api.put(`/employee/${editing.value.id}`, form.value)
    else await api.post('/employee', form.value)
    ElMessage.success('已保存')
    visible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { loading.value = false }
}

async function toggle(row) {
  try { await api.put(`/employee/${row.id}/toggle`); fetch() }
  catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '操作失败') }
}

async function del(row) {
  try { await ElMessageBox.confirm(`确定删除员工 ${row.name}？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await api.delete(`/employee/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败') }
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
</style>
