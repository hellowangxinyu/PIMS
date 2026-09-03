<template>
  <div class="page-container">
    <div class="page-header">
      <h2>数据字典</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showDialog(null)" v-if="hasPerm('dict:write')">新增字典项</el-button>
      </div>
    </div>

    <!-- 按类型分组显示 -->
    <div v-for="group in grouped" :key="group.type" class="dict-group">
      <div class="dict-group-header">
        <h3>{{ typeLabels[group.type] || group.type }}</h3>
        <el-tag size="small" type="info">{{ group.items.length }} 项</el-tag>
      </div>
      <p-table :data="group.items" stripe border size="small" @header-dragend="onHeaderDragend">
        <el-table-column prop="value" label="值" :width="cw('值') || 180" />
        <el-table-column prop="label" label="显示名称" min-width="150" />
        <el-table-column prop="sortOrder" label="排序" :width="cw('排序') || 80" />
        <el-table-column label="操作" width="130" v-if="hasPerm('dict:write')">
          <template #default="{row}">
            <button class="op-btn op-btn-primary" @click="showDialog(row)">编辑</button>
            <button class="op-btn op-btn-danger" @click="del(row.id)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <el-dialog :title="form.id?'编辑字典项':'新增字典项'" v-model="dialogVisible" width="min(1100px, 96vw)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%">
            <el-option v-for="t in types" :key="t" :label="typeLabels[t]||t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示名称"><el-input v-model="form.label" /></el-form-item>
        <el-form-item label="值"><el-input v-model="form.value" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

const list = ref([])
const dialogVisible = ref(false)
const form = ref({})
const perms = ref([])
const { cw, onHeaderDragend } = useColumnResize('dict')

const typeLabels = {
  material_category: '物料大类',
  material_sub_category: '物料小类',
  payment_terms: '付款条件',
  payment_method: '付款方式',
  outbound_reason: '其他出库原因',
  inbound_reason: '其他入库原因',
  customer_level: '客户等级',
  strace_category: '质量追溯问题类型',
  strace_result_type: '质量追溯处理结果类型'
}

const types = computed(() => [...new Set(list.value.map(i => i.type))])

const grouped = computed(() => {
  const map = {}
  for (const item of list.value) {
    if (!map[item.type]) map[item.type] = []
    map[item.type].push(item)
  }
  return Object.entries(map).map(([type, items]) => ({ type, items }))
})

function hasPerm(code) { return perms.value.includes(code) }

async function fetch() {
  list.value = await api.get('/dict')
}

function showDialog(row) {
  form.value = row ? { ...row } : { type: 'material_category', sortOrder: 0 }
  dialogVisible.value = true
}

async function save() {
  if (form.value.id) {
    await api.put(`/dict/${form.value.id}`, form.value)
  } else {
    await api.post('/dict', form.value)
  }
  dialogVisible.value = false
  ElMessage.success('保存成功')
  fetch()
}

async function del(id) {
  await ElMessageBox.confirm('确定删除？')
  await api.delete(`/dict/${id}`)
  ElMessage.success('已删除')
  fetch()
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  fetch()
})
</script>

<style scoped>
.page-container { width: 100%; }
.dict-group { margin-bottom: 24px; }
.dict-group-header {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 10px;
}
.dict-group-header h3 {
  margin: 0; font-size: 15px; font-weight: 600;
  color: var(--pims-text);
}
</style>
