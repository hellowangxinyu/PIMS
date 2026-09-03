<template>
  <div class="page-container">
    <div class="page-header">
      <h2>包装标准</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openForm(null)" v-if="hasPerm('dict:write')">新增包装标准</el-button>
      </div>
    </div>

    <div class="table-card">
      <p-table :data="rows" stripe border v-loading="loading">
        <el-table-column prop="name" label="名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="组合明细" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.items && row.items.length">
              {{ row.items.map(i => i.name + '×' + Number(i.qty)).join(' + ') }}
            </span>
            <span v-else>{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="套容量(kg)" width="95" align="right">
          <template #default="{ row }">{{ row.capacityKg ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="套单价(元)" width="100" align="right">
          <template #default="{ row }">{{ Number(row.setPrice || row.unitPrice || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="75" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="openForm(row)">编辑</button>
            <button class="op-btn op-btn-danger" @click="del(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <!-- v5.82 组合包装：主信息 + 明细表 -->
    <el-dialog :title="form.id ? '编辑包装标准' : '新增包装标准'" v-model="visible" width="min(1100px, 96vw)" top="16px" destroy-on-close>
      <el-descriptions :column="3" border size="small" class="po-head-table">
        <el-descriptions-item label="名称 *">
          <el-input v-model="form.name" placeholder="如：20L 铁桶套装（桶+衬袋）" style="width:100%" />
        </el-descriptions-item>
        <el-descriptions-item label="套容量(kg)">
          <el-input-number v-model="form.capacityKg" :min="0" :precision="3" :step="1" style="width:130px" />
          <span style="margin-left:6px;font-size:12px;color:#888">一套能装多少kg；成本=⌈批量÷套容量⌉×套单价</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" />
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">
          <el-input v-model="form.remark" type="textarea" :rows="1" />
        </el-descriptions-item>
      </el-descriptions>

      <div class="batch-header" style="margin:12px 0 8px">
        <span class="batch-title">包装组合明细（套单价 = Σ 数量×单价 = <strong>￥{{ setTotal.toFixed(2) }}</strong>）</span>
        <button class="op-btn op-btn-add" type="button" @click="addItem">+ 添加包装物料</button>
      </div>
      <p-table :data="form.items" border size="small" style="width:100%">
        <el-table-column label="物料名称" min-width="180">
          <template #default="{ row }"><el-input v-model="row.name" placeholder="如 20L铁桶 / 内衬袋 / 木托盘" size="small" /></template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-select v-model="row.packType" size="small" clearable placeholder="选填">
              <el-option v-for="(label, value) in TYPE_MAP" :key="value" :label="label" :value="value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="规格" min-width="120">
          <template #default="{ row }"><el-input v-model="row.spec" placeholder="20L / 1.2m×1m" size="small" /></template>
        </el-table-column>
        <el-table-column label="每套数量" width="130" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0.001" :precision="3" :step="1" size="small" style="width:110px" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="单价(元)" width="130" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :step="1" size="small" style="width:110px" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="小计" width="100" align="right">
          <template #default="{ row }"><span class="amount-cell">￥{{ (Number(row.qty||0)*Number(row.unitPrice||0)).toFixed(2) }}</span></template>
        </el-table-column>
        <el-table-column label="" width="48" align="center">
          <template #default="{ $index }">
            <button class="op-btn op-btn-del" @click="form.items.splice($index,1)">✕</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="form-tip" style="margin-top:8px">托盘等共享物料用小数数量（如每 20 桶 1 托盘 → 数量 0.05）；配方绑定后理论成本自动按套计算。</div>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const TYPE_MAP = { IRON_DRUM: '铁桶', PLASTIC_DRUM: '塑料桶', IBC: '吨桶', BAG: '编织袋', PALLET: '托盘', OTHER: '其他' }

const rows = ref([])
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const perms = ref([])
const form = ref({ items: [] })

const setTotal = computed(() => (form.value.items || []).reduce((s, i) => s + Number(i.qty||0) * Number(i.unitPrice||0), 0))
function hasPerm(c) { return perms.value.includes(c) }

async function fetch() {
  loading.value = true
  try { rows.value = await api.get('/packaging-standard') } catch {}
  finally { loading.value = false }
}

function openForm(row) {
  form.value = row
    ? { ...row, items: (row.items || []).map(i => ({ ...i })) }
    : { name: '', capacityKg: null, remark: '', enabled: true, items: [{ name: '', packType: '', spec: '', qty: 1, unitPrice: null }] }
  visible.value = true
}

function addItem() { form.value.items.push({ name: '', packType: '', spec: '', qty: 1, unitPrice: null }) }

async function save() {
  const items = (form.value.items || []).filter(i => i.name && i.name.trim())
  if (!items.length) { ElMessage.warning('请至少添加一个包装物料（桶/袋/托盘等组合）'); return }
  saving.value = true
  try {
    const payload = { ...form.value, items }
    if (form.value.id) await api.put(`/packaging-standard/${form.value.id}`, payload)
    else await api.post('/packaging-standard', payload)
    ElMessage.success('保存成功')
    visible.value = false
    fetch()
  } catch {} finally { saving.value = false }
}

async function del(row) {
  await ElMessageBox.confirm(`确认删除「${row.name}」？已被配方引用的删除后配方将失去包装成本`, '提示', { type: 'warning' })
  await api.delete(`/packaging-standard/${row.id}`)
  ElMessage.success('已删除')
  fetch()
}

onMounted(() => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
})
</script>

<style scoped>
.po-head-table :deep(.el-descriptions__label) { width: 110px; background: #f5f7fa; color: #606266; }
.po-head-table :deep(.el-descriptions__content) { padding: 8px 12px; }
.batch-header { display: flex; align-items: center; justify-content: space-between; }
.batch-title { font-weight: 600; }
</style>
