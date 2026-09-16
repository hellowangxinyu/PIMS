<template>
  <div class="page-container">
    <!-- ==================== 列表模式 ==================== -->
    <template v-if="mode === 'list'">
      <div class="page-header">
        <h2>质检模板</h2>
      <div class="header-actions">
          <el-button v-if="hasPerm('qc:write')" type="primary" @click="openCreate">+ 新建模板</el-button>
      </div>
      </div>

      <div class="qc-tip">
        按物料大类区分质检检测内容：材料细分 溶剂/树脂/助剂/颜料/填料，半成品、成品各一套；
        质检单创建时自动套用对应类别的<b>默认模板</b>（同类别可建多套，仅默认模板生效）。
      </div>

      <div class="type-tabs">
        <button class="type-tab" :class="{ active: filter === '' }" @click="setFilter('')">全部</button>
        <button v-for="c in CATEGORY_OPTIONS" :key="c.value" class="type-tab" :class="{ active: filter === c.value }" @click="setFilter(c.value)">{{ c.label }}</button>
      </div>

      <p-table :data="filteredTemplates" border stripe style="width:100%">
        <el-table-column label="模板名称" min-width="220">
          <template #default="{ row }">
            <span class="tpl-name">{{ row.name }}</span>
            <span v-if="row.isDefault" class="default-star" title="该类别默认模板">★</span>
            <el-tag v-if="!row.enabled" size="small" type="info" style="margin-left:6px">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="适用类别" width="100" align="center">
          <template #default="{ row }">{{ catLabel(row.applyCategory) }}</template>
        </el-table-column>
        <el-table-column label="检测项数" width="90" prop="itemCount" align="center" />
        <el-table-column label="创建人" width="90" align="center">
          <template #default="{ row }">{{ row.createdBy || '—' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.updateTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column v-if="hasPerm('qc:write')" label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="openEdit(row)">编辑</button>
            <button class="op-btn" @click="previewItems(row.items, row)">预览</button>
            <button v-if="!row.isDefault" class="op-btn op-btn-success" @click="makeDefault(row)">设默认</button>
            <button class="op-btn op-btn-danger" @click="removeTpl(row)">删除</button>
          </template>
        </el-table-column>
        <el-table-column v-else label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <button class="op-btn" @click="previewItems(row.items, row)">预览</button>
          </template>
        </el-table-column>
      </p-table>
      <el-empty v-if="!filteredTemplates.length" description="暂无质检模板，点右上方新建" :image-size="70" />
    </template>

    <!-- ==================== 编辑模式 ==================== -->
    <template v-else>
      <div class="page-header">
        <h2 class="edit-title">
          <el-button text @click="backToList">← 返回列表</el-button>
          {{ editingId ? '编辑质检模板' : '新建质检模板' }}
        </h2>
        <div>
          <el-button @click="previewEditing" style="margin-right:10px">预览效果</el-button>
          <el-button type="primary" @click="save" :loading="saving">保存模板</el-button>
        </div>
      </div>

      <div class="meta-row">
        <span class="meta-label">模板名称：</span>
        <el-input v-model="tpl.name" style="width:280px" placeholder="如：溶剂来料检验模板" />
        <span class="meta-label">适用类别：</span>
        <el-select v-model="tpl.applyCategory" style="width:150px">
          <el-option v-for="c in CATEGORY_OPTIONS" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
        <el-checkbox v-model="tpl.isDefault" style="margin-left:16px">设为默认模板</el-checkbox>
        <el-checkbox v-model="tpl.enabled">启用</el-checkbox>
      </div>

      <!-- v5.90 匹配小类全类可用（原料按小类、半成品按小类+主材、成品按小类+主材+色系）；留空=不限 -->
      <div class="meta-row">
        <span class="meta-label">匹配小类：</span>
        <el-select v-model="tpl.subCategory" clearable placeholder="留空=不限" style="width:170px">
          <el-option v-for="d in subCategoryOptions" :key="d.value" :label="d.label + '（' + d.value + '）'" :value="d.value" />
        </el-select>
        <template v-if="tpl.applyCategory === 'C' || tpl.applyCategory === 'B'">
          <span class="meta-label">主材体系：</span>
          <el-select v-model="tpl.mainMaterial" clearable placeholder="留空=不限" style="width:150px">
            <el-option v-for="d in dictItems('material_main_material')" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </template>
        <template v-if="tpl.applyCategory === 'C'">
          <span class="meta-label">色系：</span>
          <el-select v-model="tpl.colorSeries" clearable placeholder="留空=不限" style="width:150px">
            <el-option v-for="d in dictItems('material_color_series')" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </template>
      </div>

      <div class="pack-row">
        <span class="pack-label">备注：</span>
        <el-input v-model="tpl.remark" type="textarea" :rows="2" placeholder="如：适用 XX 系列产品，指标按 2024 版采购标准（选填）" />
      </div>

      <div class="tip">
        质检单创建时按 小类/主材/色系 匹配最具体模板（全空=大类通用），快照后修改不影响已建质检单；
        标准要求可填具体指标（如 ≤40μm）或"按产品标准"。
      </div>

      <div class="items-card">
        <div class="sub-title">检测项</div>
        <div class="item-row head-row">
          <span class="col-idx">#</span>
          <span class="col-name">检测项目 <b style="color:#b56a5c">*</b></span>
          <span class="col-standard">标准要求 <b style="color:#b56a5c">*</b></span>
          <span class="col-unit">单位</span>
          <span class="col-method">检验方法/依据</span>
          <span class="col-op"></span>
        </div>
        <div class="item-row" v-for="(it, i) in tpl.items" :key="i">
          <span class="col-idx">{{ i + 1 }}</span>
          <el-input v-model="it.name" size="small" class="col-name" placeholder="如：细度" />
          <el-input v-model="it.standard" size="small" class="col-standard" placeholder="如：≤40μm / 按产品标准" />
          <el-input v-model="it.unit" size="small" class="col-unit" placeholder="μm / % / KU" />
          <el-input v-model="it.method" size="small" class="col-method" placeholder="如：GB/T 1724 刮板细度计" />
          <el-button type="danger" text size="small" class="col-op" @click="removeItem(i)">✕</el-button>
        </div>
        <el-button plain size="small" class="add-line" @click="addItem">+ 添加检测项</el-button>
        <el-empty v-if="!tpl.items.length" description="暂无检测项，点上方添加" :image-size="60" />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { buildQcReportHtml } from '../utils/qcPrint'

// 适用物料大类（对齐 material_category 字典：材料细分 5 类 + 半成品 + 成品）
const CATEGORY_OPTIONS = [
  { value: 'S', label: '溶剂' },
  { value: 'R', label: '树脂' },
  { value: 'A', label: '助剂' },
  { value: 'P', label: '颜料' },
  { value: 'F', label: '填料' },
  { value: 'B', label: '半成品' },
  { value: 'C', label: '成品' }
]
function catLabel(code) {
  return CATEGORY_OPTIONS.find(c => c.value === code)?.label || code || '-'
}

// 权限
let perms = []
function hasPerm(code) { return perms.includes(code) }

// ==================== 列表模式 ====================
const dicts = ref({})
function dictItems(type) { return dicts.value[type] || [] }
// v5.81.1 小类按大类前缀过滤（B/C 的子小类）
const subCategoryOptions = computed(() => {
  if (!tpl.value || !tpl.value.applyCategory) return dictItems('material_sub_category')
  return dictItems('material_sub_category').filter(d => (d.value || '').startsWith(tpl.value.applyCategory))
})
async function loadDicts() {
  try {
    const all = await api.get('/dict')
    const map = {}
    for (const item of all || []) {
      if (!map[item.type]) map[item.type] = []
      map[item.type].push(item)
    }
    dicts.value = map
  } catch {}
}
const mode = ref('list')
const filter = ref('')
const templates = ref([])

const filteredTemplates = computed(() =>
  filter.value ? templates.value.filter(t => t.applyCategory === filter.value) : templates.value
)

async function fetchTemplates() {
  try {
    templates.value = await api.get('/qc-template/list') || []
  } catch { templates.value = [] }
}
function setFilter(f) { filter.value = f }
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '' }

async function makeDefault(row) {
  try {
    await api.post(`/qc-template/${row.id}/set-default`)
    ElMessage.success(`已将「${row.name}」设为${catLabel(row.applyCategory)}类默认模板`)
    fetchTemplates()
  } catch {}
}
async function removeTpl(row) {
  try {
    await ElMessageBox.confirm(
      row.isDefault ? '默认模板不能删除，请先将默认设置转移到其他模板。' : `确定删除模板「${row.name}」？已创建的质检单不受影响（检测项已快照）。`,
      '删除质检模板',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonDisabled: row.isDefault }
    )
  } catch { return }
  try {
    await api.delete(`/qc-template/${row.id}`)
    ElMessage.success('已删除')
    fetchTemplates()
  } catch {}
}

// ==================== 编辑模式 ====================
const editingId = ref(null)
const tpl = ref({ name: '', applyCategory: 'S', isDefault: false, enabled: true, remark: '', items: [] })
const saving = ref(false)

function openCreate() {
  editingId.value = null
  // 新建时沿用当前筛选类别，且该类别已有默认时不再勾默认
  const cat = filter.value || 'S'
  tpl.value = { name: '', applyCategory: cat, isDefault: !hasDefault(cat), enabled: true, remark: '', items: [] }
  mode.value = 'edit'
}
function hasDefault(cat) {
  return templates.value.some(t => t.applyCategory === cat && t.isDefault)
}
async function openEdit(row) {
  try {
    const data = await api.get(`/qc-template/${row.id}`)
    editingId.value = row.id
    tpl.value = {
      name: data.name || '',
      applyCategory: data.applyCategory || 'S',
      isDefault: !!data.isDefault,
      enabled: data.enabled !== false,
      remark: data.remark || '',
      items: (data.items || []).map(i => ({ name: i.name || '', standard: i.standard || '', unit: i.unit || '', method: i.method || '' }))
    }
    mode.value = 'edit'
  } catch {}
}
function backToList() { mode.value = 'list'; fetchTemplates() }

async function save() {
  if (!tpl.value.name || !tpl.value.name.trim()) { ElMessage.warning('请填写模板名称'); return }
  // v5.99.1 检测项全必填：名称+标准要求缺一不可；空行也拦（不再静默过滤）
  const items = tpl.value.items
  if (!items.length) { ElMessage.warning('请至少添加一个检测项'); return }
  for (let i = 0; i < items.length; i++) {
    const it = items[i]
    if (!it.name || !it.name.trim()) { ElMessage.warning(`第 ${i + 1} 行的检测项目名称不能为空`); return }
    if (!it.standard || !it.standard.trim()) { ElMessage.warning(`检测项「${it.name}」的标准要求不能为空`); return }
  }
  saving.value = true
  try {
    if (editingId.value) {
      await api.put(`/qc-template/${editingId.value}`, { ...tpl.value, items })
    } else {
      await api.post('/qc-template', { ...tpl.value, items })
    }
    ElMessage.success('质检模板已保存')
    backToList()
  } catch {} finally { saving.value = false }
}

function addItem() { tpl.value.items.push({ name: '', standard: '', unit: '', method: '' }) }
function removeItem(i) { tpl.value.items.splice(i, 1) }

// ==================== 模板预览（v5.32：按模板生成质检报告样式，实测值留空由检验员填） ====================
function previewItems(items, tplRow) {
  const list = (items || []).filter(i => i.name && i.name.trim())
  if (!list.length) { ElMessage.warning('模板暂无检测项，请先添加'); return }
  // 模拟质检单信息——仓库/批号等留空，报告编号按预览场景显示
  const mock = {
    inspectionNo: tplRow ? `预览 · ${tplRow.name}` : '预览（未保存模板）',
    refDocNo: '', materialCode: '', materialName: tplRow ? `${catLabel(tplRow.applyCategory)}类物料（模板预览）` : `${catLabel(tpl.value.applyCategory)}类物料（模板预览）`,
    batchNo: '', qty: '', unit: 'kg', warehouseName: '',
    produceDate: '', inspector: '', inspectDate: '', status: '', resultRemark: ''
  }
  const win = window.open('', '_blank')
  if (!win) { ElMessage.warning('浏览器拦截了弹窗，请允许本站弹出窗口'); return }
  win.document.write(buildQcReportHtml(mock, list, { preview: true }))
  win.document.close()
  win.focus()
}
// 编辑模式：预览当前编辑中的检测项（保存前即可看效果）
function previewEditing() { previewItems(tpl.value.items, null) }

onMounted(() => {
  loadDicts()
  try { perms = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch { perms = [] }
  fetchTemplates()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.edit-title { display: flex; align-items: center; gap: 8px; }
.qc-tip {
  background: #eef0f6; color: #5d5f85; border-radius: 6px;
  padding: 8px 14px; font-size: 13px; margin-bottom: 12px;
  border: 1px solid #ccd3e2;
}
.type-tabs { display: flex; gap: 0; margin-bottom: 14px; background: #f1f5f9; border-radius: 8px; padding: 3px; width: fit-content; }
.type-tab { padding: 8px 20px; font-size: 13px; font-weight: 600; border: none; border-radius: 6px; cursor: pointer; background: transparent; color: #64748b; transition: all 0.2s; }
.type-tab:hover { color: #334155; }
.type-tab.active { background: #fff; color: #4a6785; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.tpl-name { font-weight: 600; }
.default-star { color: #c2a069; margin-left: 4px; }
.meta-row { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.meta-label { font-size: 13px; font-weight: 600; color: #475569; white-space: nowrap; margin-left: 8px; }
.pack-row { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 14px; }
.pack-label { font-size: 13px; font-weight: 600; color: #475569; padding-top: 6px; white-space: nowrap; }
.pack-row .el-input { flex: 1; }
.tip { font-size: 13px; color: #475569; background: #eff4f7; border: 1px solid #c2d5de; border-radius: 6px; padding: 10px 14px; margin-bottom: 16px; }
.items-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 14px 16px; }
.sub-title { font-size: 13px; font-weight: 600; color: #334155; margin-bottom: 10px; }
.item-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.head-row { font-size: 12px; font-weight: 600; color: #64748b; padding-bottom: 6px; border-bottom: 1px solid #e2e8f0; }
.col-idx { width: 28px; text-align: center; color: #94a3b8; flex-shrink: 0; }
.col-name { width: 180px; flex-shrink: 0; }
.col-standard { width: 220px; flex-shrink: 0; }
.col-unit { width: 90px; flex-shrink: 0; }
.col-method { flex: 1; min-width: 0; }
.col-op { width: 36px; flex-shrink: 0; text-align: center; }
.add-line { margin: 8px 0 4px; }
</style>
