<template>
  <div class="page">
    <div class="report-header">
      <h2 class="report-title">销售价格政策</h2>
      <span class="report-sub">按物料/大类维护阶梯价（量大优惠），下单自动带出</span>
    </div>

    <div class="toolbar">
      <el-button type="primary" @click="openEdit(null)" v-if="hasPerm('sales:write')">新增价格档</el-button>
    </div>

    <p-table :data="rows" stripe border style="width:100%">
      <el-table-column label="适用" min-width="180">
        <template #default="{ row }">
          <el-tag v-if="row.materialCode" type="primary" size="small">{{ row.materialCode }} {{ row.materialName }}</el-tag>
          <el-tag v-else type="warning" size="small">大类兜底 · {{ catLabel(row.materialCategory) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="minQty" label="数量≥" width="100" align="center" />
      <el-table-column prop="unitPrice" label="单价(元/kg)" width="130" align="right">
        <template #default="{ row }">¥{{ Number(row.unitPrice).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="effectiveDate" label="生效日" width="110" align="center" />
      <el-table-column prop="expiryDate" label="失效日" width="110" align="center">
        <template #default="{ row }">{{ row.expiryDate || '长期' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="150" align="center" v-if="hasPerm('sales:write')">
        <template #default="{ row }">
          <button class="op-btn" @click="openEdit(row)">编辑</button>
          <button class="op-btn op-btn-danger" @click="del(row)">删除</button>
        </template>
      </el-table-column>
    </p-table>

    <el-dialog :title="form.id ? '编辑价格档' : '新增价格档'" v-model="dlg" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="定价方式">
          <el-radio-group v-model="kind">
            <el-radio value="material">按物料</el-radio>
            <el-radio value="category">按大类兜底</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="物料" v-if="kind === 'material'">
          <el-select v-model="form.materialCode" filterable placeholder="选择物料" style="width:100%">
            <el-option v-for="m in materials" :key="m.code" :label="m.code + ' ' + m.name" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料大类" v-if="kind === 'category'">
          <el-select v-model="form.materialCategory" placeholder="选择大类" style="width:100%">
            <el-option label="A 原料" value="A" /><el-option label="B 半成品" value="B" /><el-option label="C 成品" value="C" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量≥"><el-input-number v-model="form.minQty" :min="1" style="width:100%" /></el-form-item>
        <el-form-item label="单价(元/kg)"><el-input-number v-model="form.unitPrice" :min="0.01" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="生效日"><el-date-picker v-model="form.effectiveDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="失效日"><el-date-picker v-model="form.expiryDate" value-format="YYYY-MM-DD" placeholder="空=长期" style="width:100%" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-value="ENABLED" inactive-value="DISABLED" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { todayLocal } from '../utils/date'
// v6.3 第二批：销售价格政策维护（物料精确档 > 大类兜底档；min_qty 阶梯=量大优惠）
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const rows = ref([])
const materials = ref([])
const dlg = ref(false)
const kind = ref('material')
const form = ref({})
const perms = ref([])
function hasPerm(c) { return perms.value.includes(c) }
function catLabel(c) { return { A: '原料', B: '半成品', C: '成品' }[c] || c }

async function fetch() {
  try { rows.value = await api.get('/price-policy') } catch {}
}

function openEdit(row) {
  kind.value = row && !row.materialCode ? 'category' : 'material'
  form.value = row
    ? { ...row }
    : { materialCode: null, materialCategory: null, minQty: 1, unitPrice: null, effectiveDate: todayLocal(), expiryDate: null, status: 'ENABLED', remark: '' }
  dlg.value = true
}

async function save() {
  const f = { ...form.value }
  if (kind.value === 'material') f.materialCategory = null
  else { f.materialCode = null }
  if (!f.unitPrice) { ElMessage.warning('请填写单价'); return }
  try {
    if (f.id) await api.put(`/price-policy/${f.id}`, f)
    else await api.post('/price-policy', f)
    ElMessage.success('已保存')
    dlg.value = false
    fetch()
  } catch (e) {}
}

async function del(row) {
  try {
    await ElMessageBox.confirm(`删除该价格档（${row.materialCode || catLabel(row.materialCategory)} ≥${row.minQty}）？`, '删除', { type: 'warning' })
    await api.delete(`/price-policy/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) {}
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
  try { materials.value = (await api.get('/material', { params: { page: 1, size: 500 } })).rows || [] } catch { materials.value = [] }
})
</script>

<style scoped>
.page { width: 100%; }
.report-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.report-title { margin: 0; font-size: 20px; }
.report-sub { font-size: 12px; color: #94a3b8; }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
</style>
