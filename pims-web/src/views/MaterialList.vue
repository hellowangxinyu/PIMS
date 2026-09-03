<template>
  <div class="page-container">
    <div class="page-header">
      <h2>物料管理</h2>
      <div class="header-actions">
        <el-input v-model="keyword" placeholder="搜索编码/名称" clearable @keyup.enter="fetch" style="width:200px" />
        <el-button @click="fetch">搜索</el-button>
        <el-button @click="doExport" :loading="exporting">导出 Excel</el-button>
        <el-button @click="downloadTpl" v-if="hasPerm('material:write')">下载导入模板</el-button>
        <el-upload v-if="hasPerm('material:write')" :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" :on-change="onImportFile" style="display:inline-block">
          <el-button type="primary" :loading="importing">导入Excel</el-button>
        </el-upload>
        <el-button type="primary" @click="showDialog(null)" v-if="hasPerm('material:write')">新增物料</el-button>
      </div>
    </div>

    <!-- 大类筛选标签页 -->
    <div class="category-tabs">
      <el-radio-group v-model="categoryFilter" @change="fetch">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button v-for="d in dicts.material_category" :key="d.value" :value="d.value">
          {{ d.label }}
        </el-radio-button>
      </el-radio-group>
      <el-radio-group v-model="enabledFilter" @change="fetch" style="margin-left:16px">
        <el-radio-button value="true">启用中</el-radio-button>
        <el-radio-button value="false">已禁用</el-radio-button>
        <el-radio-button value="">全部状态</el-radio-button>
      </el-radio-group>
      <span class="category-count">共 {{ list.length }} 条</span>
    </div>
    <div class="table-card">
      <p-table :data="pagedRows" stripe border @header-dragend="onHeaderDragend">
        <el-table-column prop="code" label="编码" :width="cw('编码') || 130" />
        <el-table-column prop="name" label="品名" min-width="180" show-overflow-tooltip>
          <template #default="{row}"><span :style="row.enabled ? '' : 'color:#94a3b8;text-decoration:line-through'">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="brand" label="牌号" :width="cw('牌号') || 120" />
        <el-table-column label="大类" :width="cw('大类') || 90" align="center">
          <template #default="{row}">{{ dictLabel('material_category', row.category) }}</template>
        </el-table-column>
        <el-table-column label="小类" :width="cw('小类') || 90" align="center">
          <template #default="{ row }">{{ dictLabel('material_sub_category', row.subCategory) }}</template>
        </el-table-column>
        <!-- 品牌归属：仅成品(C)物料显示，其他分类不显示该列内容 -->
        <el-table-column label="主材" :width="cw('主材') || 90" align="center">
          <template #default="{ row }">
            <span v-if="row.category === 'C' || row.category === 'B'">{{ mainMaterialLabel(row.mainMaterial) }}</span>
            <span v-else style="color:#94a3b8">—</span>
          </template>
        </el-table-column>
        <el-table-column label="色系" :width="cw('色系') || 80" align="center">
          <template #default="{ row }">
            <span v-if="row.category === 'C'">{{ colorSeriesLabel(row.colorSeries) }}</span>
            <span v-else style="color:#94a3b8">—</span>
          </template>
        </el-table-column>
        <el-table-column label="品牌归属" :width="cw('品牌归属') || 120" align="center">
          <template #default="{ row }">
            <span v-if="row.category === 'C' && row.brandOwner">{{ row.brandOwner }}</span>
            <span v-else style="color:#94a3b8">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="shelfLifeDays" label="质保期(天)" :width="cw('质保期(天)') || 100" align="center">
          <template #default="{ row }">{{ row.shelfLifeDays || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" v-if="hasPerm('material:write')||hasPerm('material:delete')">
          <template #default="{row}">
            <button class="op-btn op-btn-primary" @click="showDialog(row)">编辑</button>
            <button v-if="row.enabled" class="op-btn op-btn-warn" @click="disable(row.id)">禁用</button>
            <button v-if="row.enabled" class="op-btn op-btn-danger" @click="del(row.id)">删除</button>
            <button v-else class="op-btn op-btn-success" @click="enable(row.id)">启用</button>
          </template>
        </el-table-column>
      </p-table>
      <!-- 分页（v5.18） -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]"
          :total="list.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>
    <el-dialog :title="form.id?'编辑物料':'新增物料'" v-model="dialogVisible" width="min(1100px, 96vw)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="编码" v-if="form.code">
          <!-- v5.91 编码修改需单独权限（默认锁定） -->
          <el-input v-model="form.code" :disabled="!hasPerm('material:code-edit')" />
          <div class="form-tip" v-if="hasPerm('material:code-edit')">你已获授权可修改编码：新码须符合该类编码规则且未被占用</div>
          <div class="form-tip">6 位 = 小类码 + 序号（如 AC0001），首位字母即物料大类，看码识类</div>
        </el-form-item>
        <el-form-item label="编码" v-else>
          <el-input value="保存时自动分配" disabled />
          <div class="form-tip">选好大类/小类后保存，系统自动分配全局唯一编码（中途放弃不占号）</div>
        </el-form-item>
        <el-form-item label="品名" required><el-input v-model="form.name" placeholder="必填" /></el-form-item>
        <el-form-item label="牌号" required><el-input v-model="form.brand" placeholder="必填，无牌号可填-或自定型号" /></el-form-item>
        <el-form-item label="大类" required>
          <el-select v-model="form.category" style="width:100%" clearable @change="onCategoryChange">
            <el-option v-for="d in dicts.material_category" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="小类" required>
          <el-select v-model="form.subCategory" style="width:100%" clearable @change="onSubCategoryChange">
            <el-option v-for="d in filteredSubCats" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <!-- 主材/色系（v5.17）：成品三维分类——角色（小类）+ 主材（树脂体系）+ 色系 -->
        <!-- v5.65：主材/色系同时是 B/C 新编码的属性段——B 类必选主材、C 类必选主材+色系（自动取号需要） -->
        <el-form-item label="主材" v-if="form.category === 'C' || form.category === 'B'" required>
          <el-select v-model="form.mainMaterial" style="width:100%" clearable placeholder="选择树脂体系（聚酯/氟碳/环氧/丙烯酸；新编码属性段）">
            <el-option v-for="d in dictItems('material_main_material')" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="色系" v-if="form.category === 'C'" required>
          <el-select v-model="form.colorSeries" style="width:100%" clearable placeholder="选择色系（新编码属性段，如 CWZH0001 的 H=白）">
            <el-option v-for="d in dictItems('material_color_series')" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <!-- 品牌归属（v5.16）：成品默认=芃远（自产）；仅选择成品供应商（外购）时才变为所选供应商名称 -->
        <el-form-item label="品牌归属" v-if="form.category === 'C'" required>
          <div class="brand-owner-row">
            <el-input :model-value="form.brandOwner || '芃远'" disabled class="brand-owner-input" />
            <el-select
              v-model="brandOwnerSupplier"
              placeholder="选择成品供应商（外购时）"
              filterable
              clearable
              class="brand-owner-select"
              @change="onBrandOwnerSupplierChange"
            >
              <el-option v-for="s in finishedSuppliers" :key="s.id" :label="s.name" :value="s.name" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="质保期(天)" required>
          <el-input-number v-model="form.shelfLifeDays" :min="0" :step="30" controls-position="right" placeholder="必填，0 表示不限" style="width:100%" />
        </el-form-item>
        <!-- 平替物料（v5.1）：仅原材料大类 A/P/F/R/S 可配置，配方树中可直接一键切换 -->
        <el-form-item label="平替物料" v-if="isRawMaterial(form.category)">
          <el-select v-model="form.alternativeCodes" multiple filterable clearable placeholder="选择可完美替代的原材料（可多选）" style="width:100%">
            <el-option v-for="m in alternativeOptions" :key="m.code" :label="m.code + ' ' + m.name + (m.brand ? ' [' + m.brand + ']' : '')" :value="m.code" />
          </el-select>
          <div class="form-tip">配置后自动建立双向平替关系（对方物料的平替列表也会自动包含本物料），配方树中可来回切换</div>
        </el-form-item>
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
import { usePaging } from '../composables/usePaging'
import { downloadFile } from '../utils/download'
import { useExcelImport } from '../composables/useExcelImport'

const { importing, downloadTpl, onFile: onImportFile } = useExcelImport('/material', '物料导入模板.xlsx', '物料', fetch)

const list = ref([])
const keyword = ref('')
const categoryFilter = ref('')
const enabledFilter = ref('true')
const dialogVisible = ref(false)
const form = ref({})
const perms = ref([])
const dicts = ref({})
const codingRules = ref([])
const exporting = ref(false)

// v5.23：导出当前筛选条件（关键字/大类/启用状态）下的物料档案全量
async function doExport() {
  exporting.value = true
  try {
    const params = {}
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (categoryFilter.value) params.category = categoryFilter.value
    if (enabledFilter.value !== '') params.enabled = enabledFilter.value
    await downloadFile('/material/export', params, `物料档案-${new Date().toISOString().slice(0, 10)}.xlsx`)
  } catch (e) { /* downloadFile 内已提示 */ }
  finally { exporting.value = false }
}
// 成品供应商列表（用于品牌归属下拉选择）
const finishedSuppliers = ref([])
// 品牌归属下拉的辅助绑定值（仅用于回填 form.brandOwner，不参与提交）
const brandOwnerSupplier = ref('')
function mainMaterialLabel(code) {
  const d = dictItems('material_main_material').find(x => x.value === code)
  return d ? d.label : (code || '—')
}
function colorSeriesLabel(code) {
  const d = dictItems('material_color_series').find(x => x.value === code)
  return d ? d.label : (code || '—')
}
// v5.1：平替物料——全部原材料列表（供多选下拉，排除自身）
const allRawMaterials = ref([])
const alternativeOptions = computed(() => allRawMaterials.value.filter(m => m.code !== form.value.code))

function isRawMaterial(category) { return !!category && 'APFRS'.includes(category) }

// 表格列宽拖拽与持久化
const { cw, onHeaderDragend } = useColumnResize('material')

function hasPerm(code) { return perms.value.includes(code) }

function dictLabel(type, value) {
  const items = dicts.value[type] || []
  const found = items.find(d => d.value === value)
  return found ? found.label : (value || '—')
}

const filteredSubCats = computed(() => {
  if (!form.value.category) return dicts.value.material_sub_category || []
  return (dicts.value.material_sub_category || []).filter(d => d.value.startsWith(form.value.category))
})

function onCategoryChange() {
  form.value.subCategory = ''
  form.value.code = ''
  // v5.16：成品默认品牌归属=芃远（自产）；其他大类清空
  brandOwnerSupplier.value = ''
  if (form.value.category === 'C') form.value.brandOwner = '芃远'
  else form.value.brandOwner = ''
}

function onSubCategoryChange() {
  // v5.42.2：不再预生成编码——保存成功那一刻才取号，中途放弃/取消不占全局连续号
  form.value.code = ''
}


function dictItems(type) { return dicts.value[type] || [] }
async function fetchDicts() {
  const all = await api.get('/dict')
  const map = {}
  for (const item of all) {
    if (!map[item.type]) map[item.type] = []
    map[item.type].push(item)
  }
  dicts.value = map
}

// 获取成品供应商列表，用于品牌归属下拉选择
async function fetchFinishedSuppliers() {
  try {
    finishedSuppliers.value = await api.get('/supplier', { params: { type: 'FINISHED', enabled: true } })
  } catch {
    finishedSuppliers.value = []
  }
}

// 从下拉选择成品供应商后，将名称回填到品牌归属输入框
function onBrandOwnerSupplierChange(name) {
  // 选了成品供应商（外购）→ 品牌归属=供应商；清除/未选 → 默认芃远（自产）
  form.value.brandOwner = name || '芃远'
}

// v5.18：分页（50/100/200），筛选/搜索变化回第一页
const { page, pageSize, pagedRows, resetPage } = usePaging(list)
async function fetch() {
  resetPage()
  const params = {}
  if (keyword.value) params.keyword = keyword.value
  if (categoryFilter.value) params.category = categoryFilter.value
  if (enabledFilter.value) params.enabled = enabledFilter.value
  list.value = await api.get('/material', { params })
}

function showDialog(row) {
  form.value = row ? { ...row } : {}
  // 编辑时同步品牌归属下拉辅助值（若与某成品供应商名称一致则回显）
  brandOwnerSupplier.value = row?.brandOwner || ''
  // v5.1：平替物料 逗号分隔字符串 → 数组（供多选下拉绑定）
  if (form.value.alternativeCodes) {
    form.value.alternativeCodes = String(form.value.alternativeCodes).split(',').map(s => s.trim()).filter(Boolean)
  } else {
    form.value.alternativeCodes = []
  }
  dialogVisible.value = true
}

async function save() {
  // v5.42.2：编码由后端保存时自动分配（新建时前端不传编码）
  if (!form.value.id && !form.value.code) form.value.code = undefined
  // v5.16：所有字段必填
  if (!form.value.name || !form.value.name.trim()) { ElMessage.warning('请填写品名'); return }
  if (!form.value.brand || !form.value.brand.trim()) { ElMessage.warning('请填写牌号'); return }
  if (!form.value.category) { ElMessage.warning('请选择大类'); return }
  if (!form.value.subCategory) { ElMessage.warning('请选择小类'); return }
  if (form.value.shelfLifeDays === null || form.value.shelfLifeDays === undefined || form.value.shelfLifeDays === '') {
    ElMessage.warning('请填写质保期（无限制填 0）'); return
  }
  if ((form.value.category === 'C' || form.value.category === 'B') && !form.value.mainMaterial) {
    ElMessage.warning('请选择主材（聚酯/氟碳/环氧/丙烯酸）——新编码的属性段'); return
  }
  if (form.value.category === 'C' && !form.value.colorSeries) {
    ElMessage.warning('成品请选择色系——新编码的属性段（如 CWZH0001 的 H=白）'); return
  }
  if (form.value.category === 'C' && !form.value.brandOwner) form.value.brandOwner = '芃远'
  // v5.1：平替物料 数组 → 逗号分隔字符串（存储格式）
  const payload = { ...form.value }
  if (Array.isArray(payload.alternativeCodes)) {
    payload.alternativeCodes = payload.alternativeCodes.filter(Boolean).join(',')
  }
  if (payload.id) {
    await api.put(`/material/${payload.id}`, payload)
    ElMessage.success('保存成功')
  } else {
    const saved = await api.post('/material', payload)
    ElMessage.success(`保存成功，物料编码：${saved?.code || ''}`)
  }
  dialogVisible.value = false
  fetch()
}

// v5.43.1 禁用：被单据引用删不掉的物料走禁用——禁用后不可再用（各单据选料下拉不再显示）、不可采购/销售下单
async function disable(id) {
  await ElMessageBox.confirm(
    '确定禁用该物料？\n\n· 禁用后不能再被新单据选用（选料下拉不再显示）\n· 不能再采购、销售下单\n· 已有库存的出入库不受影响（可正常消化或报废）',
    '禁用物料', { type: 'warning', confirmButtonText: '禁用', cancelButtonText: '取消' })
  await api.put(`/material/${id}/enabled`, null, { params: { value: false } })
  ElMessage.success('已禁用')
  fetch()
}

async function enable(id) {
  await ElMessageBox.confirm('确定恢复启用该物料？', '启用', { type: 'info' })
  try {
    await api.put(`/material/${id}/enabled`, null, { params: { value: true } })
    ElMessage.success('已启用')
    fetch()
  } catch (e) {
    ElMessage.error(e.message || '启用失败')
  }
  fetch()
}
async function del(id) {
  // v5.43：真删除——被单据引用/有台账的物料后端会拒绝；无引用的删除后编码回收，新建物料时复用
  await ElMessageBox.confirm(
    '确定删除该物料？\n\n· 被单据使用或有库存记录的物料无法删除\n· 确实无用的物料删除后，其编码将被回收，新建物料时自动复用',
    '删除物料', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  await api.delete(`/material/${id}`)
  ElMessage.success('已删除，编码已回收')
  fetch()
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  await fetchDicts()
  await fetch()
  fetchFinishedSuppliers()
  // v5.1：加载全部原材料，供平替物料多选下拉
  try {
    const all = await api.get('/material')
    allRawMaterials.value = all.filter(m => m.enabled !== false && isRawMaterial(m.category))
  } catch {}
})
</script>

<style scoped>
.page-container { width: 100%; }
.category-tabs {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
}
.category-count {
  font-size: 13px;
  color: #64748b;
  margin-left: auto;
}
/* 品牌归属：输入框与下拉并排布局 */
.brand-owner-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.brand-owner-input {
  flex: 1;
}
.brand-owner-select {
  width: 180px;
  flex-shrink: 0;
}
.form-tip { font-size: 12px; color: #94a3b8; line-height: 1.5; margin-top: 4px; }
.pagination-bar { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
