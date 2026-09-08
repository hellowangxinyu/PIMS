<template>
  <div class="page-container">
    <div class="page-header">
      <h2>打样任务</h2>
      <span class="sub">打样员接收任务 → 打样 → 录入打样配方（保存自动生成成品物料）</span>
    </div>

    <div class="table-card">
      <div class="type-tabs">
        <el-radio-group v-model="tab" @change="onTab">
          <el-radio-button value="ASSIGNED">待接收</el-radio-button>
          <el-radio-button value="COLORING">进行中</el-radio-button>
          <el-radio-button value="FORMULATED">已录配方</el-radio-button>
          <el-radio-button value="">全部</el-radio-button>
        </el-radio-group>
        <label class="mine-toggle">
          <input type="checkbox" v-model="mineOnly" @change="load" /> 只看派给我的
        </label>
        <span class="type-count">共 {{ list.length }} 条</span>
      </div>

      <p-table :data="list" stripe border style="width:100%">
        <el-table-column prop="sampleNo" label="打样单号" width="140" />
        <el-table-column prop="customerName" label="客户/线索" min-width="150" show-overflow-tooltip />
        <el-table-column prop="materialDesc" label="意向产品/颜色要求" min-width="180" show-overflow-tooltip />
        <el-table-column prop="applicant" label="申请人" width="90" />
        <el-table-column prop="assignee" label="打样员" width="90">
          <template #default="{ row }">{{ row.assignee || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="adjustCount" label="轮次" width="70" align="center">
          <template #default="{ row }">第 {{ (row.adjustCount || 0) + 1 }} 轮</template>
        </el-table-column>
        <el-table-column label="打样配方" width="150">
          <template #default="{ row }">
            <span v-if="formulaOf(row.id)">{{ formulaOf(row.id).formulaNo }}</span>
            <span v-else class="text-muted">未录</span>
          </template>
        </el-table-column>
        <el-table-column label="成品编码" width="120">
          <template #default="{ row }">
            <span v-if="formulaOf(row.id)">{{ formulaOf(row.id).materialCode }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <button v-if="row.status === 'ASSIGNED'" class="op-btn op-btn-primary" @click="accept(row)">接收</button>
            <button v-if="canEdit(row)" class="op-btn op-btn-primary" @click="openEditor(row)">
              {{ formulaOf(row.id) ? '编辑配方' : '录入配方' }}
            </button>
            <button v-if="formulaOf(row.id)" class="op-btn" @click="printOne(row)">打印配方单</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <!-- 配方编辑弹窗（居中大窗，v7.7.4 由侧边抽屉改为 dialog） -->
    <el-dialog v-model="editorVisible" :title="editorTitle" width="min(960px, 96vw)" top="4vh" destroy-on-close :close-on-click-modal="false">
      <div v-if="editorRow" class="editor">
        <!-- v7.7.2 复样参考：登记时关联了历史打样则展示其配方，可一键带入作起点 -->
        <div class="ed-sec ref-sec" v-if="refFormula">
          <div class="ed-sec-title">
            <span>参考配方：{{ refFormula.sampleNo }}｜{{ refFormula.materialName || '未录配方' }}{{ refFormula.materialCode ? ' ' + refFormula.materialCode : '' }}</span>
            <el-button size="small" type="primary" plain :disabled="!refFormula.items?.length" @click="applyRef">带入参考配方</el-button>
          </div>
          <table class="ed-table" v-if="refFormula.items?.length">
            <thead><tr><th>物料编码</th><th>品名</th><th style="width:90px">类别</th><th style="width:110px">用量(g)</th></tr></thead>
            <tbody>
              <tr v-for="it in refFormula.items" :key="it.materialCode">
                <td>{{ it.materialCode }}</td><td>{{ it.materialName }}</td>
                <td class="text-center">{{ catLabel(it.category) }}</td>
                <td class="text-right">{{ Number(it.qty).toFixed(1) }}</td>
              </tr>
              <tr class="ref-total"><td colspan="3" class="text-right">合计</td><td class="text-right">{{ Number(refFormula.totalQty).toFixed(1) }}</td></tr>
            </tbody>
          </table>
          <div v-else class="text-muted" style="padding:6px 0">该打样尚未录入配方</div>
        </div>

        <div class="ed-sec">
          <div class="ed-sec-title">成品归属（首次保存后锁定，编码按此自动生成）</div>
          <div class="ed-grid">
            <div class="ed-field">
              <span class="lbl">中文名 *</span>
              <el-input v-model="form.name" :disabled="locked" size="small" maxlength="60" placeholder="给这个配方起个中文名（=成品物料名）" />
            </div>
            <div class="ed-field">
              <span class="lbl">小类 *</span>
              <el-select v-model="form.subCategory" :disabled="locked" size="small" filterable placeholder="漆型小类（编码前 2 位）" @change="form.colorSeries = ''">
                <el-option v-for="d in productSubs" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </div>
            <div class="ed-field">
              <span class="lbl">主材 *</span>
              <el-select v-model="form.mainMaterial" :disabled="locked" size="small" placeholder="树脂体系">
                <el-option v-for="d in dictItems('material_main_material')" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </div>
            <div class="ed-field">
              <span class="lbl">色系 *（成品必填，与面漆/底漆无关）</span>
              <el-select v-model="form.colorSeries" :disabled="locked" size="small" placeholder="色系">
                <el-option v-for="d in dictItems('material_color_series')" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </div>
            <div class="ed-field">
              <span class="lbl">编码预览</span>
              <span class="code-preview">{{ codePrefix ? codePrefix + 'xxxxx（9 位，保存时自动取号）' : '选齐小类/主材/色系后预览' }}</span>
            </div>
            <div class="ed-field">
              <span class="lbl">留样位置</span>
              <el-input v-model="form.sampleLocation" size="small" maxlength="20" placeholder="留样柜号/架位，打印带上" />
            </div>
          </div>
        </div>

        <div class="ed-sec">
          <div class="ed-sec-title">
            <span>用料明细（实际打样用量，按克称量，自由合计不强制 100）</span>
            <el-button size="small" type="primary" @click="openAddItem">+ 添加用料</el-button>
          </div>
          <div class="cost-bar">
            <span class="cost-bar-label">合计：</span>
            <span class="cost-bar-total">{{ totalQty }} g</span>
            <span class="cost-bar-unit">估算成本 ≈ ¥{{ estCost }}/kg</span>
            <span class="cost-bar-hint">材料按库存加权均价（与配方树成本同源），供报价参考</span>
          </div>
          <p-table :data="form.items" row-key="materialCode" border size="small">
            <el-table-column label="序号" width="56" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="品名" min-width="200">
              <template #default="{ row }">
                <el-tag :type="row.category === 'B' ? 'warning' : ''" size="small" style="margin-right:6px">{{ row.category === 'B' ? '色浆' : '原料' }}</el-tag>
                <span>{{ row.materialName || row.materialCode }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="materialCode" label="编码" width="110" />
            <el-table-column label="大类" width="75" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.category" size="small" type="info">{{ catLabel(row.category) }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="用量(g)" width="130" align="right">
              <template #default="{ row }">
                <el-input-number v-model="row.qty" :min="0.1" :precision="1" :step="10" size="small" controls-position="right" style="width:120px" />
              </template>
            </el-table-column>
            <el-table-column label="参考单价" width="100" align="right">
              <template #default="{ row }">
                <span v-if="priceOf(row.materialCode) !== '—'" style="color:#16a34a">￥{{ priceOf(row.materialCode) }}</span>
                <span v-else style="color:#c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <button class="op-btn op-btn-danger" @click="form.items.splice($index, 1)">✕</button>
              </template>
            </el-table-column>
          </p-table>
          <div v-if="!form.items.length" class="text-muted" style="text-align:center;padding:16px 0">暂无用料，点「+ 添加用料」逐条录入</div>
        </div>
      </div>

      <!-- 添加用料弹窗（参照配方管理「添加节点」：类型→搜料→用量→连续添加） -->
      <el-dialog title="添加用料" v-model="addDialogVisible" width="520px" append-to-body destroy-on-close>
        <el-form label-width="80px">
          <el-form-item label="用料类型">
            <el-radio-group v-model="addItemForm.itemType">
              <el-radio value="MATERIAL">原料</el-radio>
              <el-radio value="SEMI">色浆（半成品）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="选择物料" required>
            <el-select v-model="addItemForm.materialCode" filterable placeholder="输入编码或品名搜索" style="width:100%" @change="onAddItemMatChange">
              <el-option v-for="m in addItemMaterials" :key="m.code" :label="m.code + ' ' + (m.name || '')" :value="m.code" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="addItemForm.category" label="物料分类">
            <el-tag size="small" type="info">{{ catLabel(addItemForm.category) }}</el-tag>
            <el-tag v-if="addItemForm.subCategory" size="small" style="margin-left:6px">{{ addItemForm.subCategory }}</el-tag>
          </el-form-item>
          <el-form-item label="用量" required>
            <el-input-number v-model="addItemForm.qty" :min="0.1" :precision="1" :step="10" style="width:160px" />
            <span style="margin-left:8px;color:#64748b">g（克，打样按克称量）</span>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="addDialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="confirmAddItem">添加</el-button>
        </template>
      </el-dialog>
      <template #footer>
        <div class="ed-foot">
          <span v-if="savedCode" class="saved-code">已生成成品物料：<b>{{ savedCode }}</b> {{ savedName }}</span>
          <el-button size="small" @click="editorVisible = false">关闭</el-button>
          <el-button type="primary" size="small" :loading="saving" @click="save">{{ locked ? '更新明细' : '保存（将创建成品物料）' }}</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
/**
 * v7.7 打样任务：打样员视角（接收 → 打样 → 录入配方）。
 * 配方保存口径：用量自由合计；首次保存后端自动创建 C 类成品物料（9 位码）；
 * 估算成本=Σ(用量×移动加权均价)÷总量，价格源与配方树成本同源。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { statusType } from '../utils/statusTag'
import { printSampleFormula } from '../utils/sampleRecipePrint'

const STATUS = { APPLIED: '已申请', ASSIGNED: '已派发', COLORING: '调色中', FORMULATED: '已录配方', SENT: '已寄样', SATISFIED: '客户满意', ADJUST: '需调整', WON: '已转单', LOST: '未成交' }
const CAT_LABEL = { A: '助剂', P: '颜料', F: '填料', R: '树脂', S: '溶剂', B: '半成品', C: '成品' }
const catLabel = c => CAT_LABEL[c] || c || '—'

const tab = ref('ASSIGNED')
const mineOnly = ref(true)
const list = ref([])
const formulas = ref([])
const dicts = ref({})
const materials = ref([])
const priceMap = ref({})
const currentUser = ref('')

function dictItems(type) { return dicts.value[type] || [] }
const productSubs = computed(() => (dicts.value.material_sub_category || []).filter(d => d.value.startsWith('C')))
const pickMaterials = computed(() => materials.value.filter(m => 'APFRSB'.includes(m.category || '')))

function formulaOf(requestId) { return formulas.value.find(f => f.sampleRequestId === requestId) }
function canEdit(row) { return ['COLORING', 'FORMULATED', 'ADJUST'].includes(row.status) }

// ============ 列表 ============
async function load() {
  try {
    const params = {}
    if (tab.value) params.status = tab.value
    list.value = await api.get('/sample', { params })
    if (mineOnly.value && currentUser.value) list.value = list.value.filter(r => r.assignee === currentUser.value)
  } catch { /* ignore */ }
}
function onTab() { load() }

async function accept(row) {
  try {
    await ElMessageBox.confirm(`接收打样任务 ${row.sampleNo}？接收后进入打样中。`, '接收任务', { type: 'info', confirmButtonText: '接收' })
  } catch { return }
  try {
    await api.post(`/sample/${row.id}/accept`)
    ElMessage.success('已接收，开始打样吧')
    load()
  } catch (e) { ElMessage.error(e.response?.data?.msg || '接收失败') }
}

// ============ 配方编辑器 ============
const editorVisible = ref(false)
const editorRow = ref(null)
// v7.7.2 复样参考：登记时关联的历史打样配方
const refFormula = ref(null)
const saving = ref(false)
const savedCode = ref('')
const savedName = ref('')
const form = ref({ name: '', subCategory: '', mainMaterial: '', colorSeries: '', sampleLocation: '', items: [] })
const locked = ref(false)
const editorTitle = computed(() => editorRow.value ? `打样配方 · ${editorRow.value.sampleNo} ${editorRow.value.customerName}` : '打样配方')

// 编码前缀预览（仅展示；取号以后端为准。字母位同 CodingRuleService：CZ→T(v5.86 Z禁用)/CF→F/CE→E/CA→A，BL→BU）
const MAIN_LETTER = { CZ: 'T', CF: 'F', CE: 'E', CA: 'A' }
const COLOR_LETTER = { BK: 'K', WH: 'H', BU: 'U', GN: 'N', GY: 'Y', RD: 'R', YW: 'W' }
const codePrefix = computed(() => {
  const sub = form.value.subCategory || ''
  const main = MAIN_LETTER[form.value.mainMaterial || '']
  const color = COLOR_LETTER[form.value.colorSeries || '']
  if (sub.length < 2 || !main || !color) return ''
  return (sub === 'BL' ? 'BU' : sub) + main + color
})

function priceOf(code) {
  const p = priceMap.value[code]
  return p != null ? Number(p).toFixed(2) : '—'
}
const totalQty = computed(() => form.value.items.reduce((s, it) => s + (Number(it.qty) || 0), 0).toFixed(1))
const estCost = computed(() => {
  let cost = 0, qty = 0
  for (const it of form.value.items) {
    const p = Number(priceMap.value[it.materialCode] || 0)
    cost += (Number(it.qty) || 0) * p
    qty += Number(it.qty) || 0
  }
  return qty > 0 ? (cost / qty).toFixed(2) : '—'
})

async function openEditor(row) {
  editorRow.value = row
  savedCode.value = ''
  savedName.value = ''
  // v7.7.2 复样参考：登记时关联了历史打样则拉其配方展示
  refFormula.value = null
  if (row.refSampleId) {
    try { refFormula.value = await api.get(`/sample/${row.refSampleId}/formula`) } catch { refFormula.value = null }
  }
  try {
    const f = await api.get(`/sample/${row.id}/formula`)
    if (f) {
      form.value = {
        name: f.materialName || '', subCategory: f.subCategory || '', mainMaterial: f.mainMaterial || '',
        colorSeries: f.colorSeries || '', sampleLocation: f.sampleLocation || '',
        items: (f.items || []).map(x => ({ materialCode: x.materialCode, materialName: x.materialName, category: x.category, subCategory: x.subCategory, qty: Number(x.qty) }))
      }
      locked.value = true
      savedCode.value = f.materialCode || ''
      savedName.value = f.materialName || ''
    } else {
      form.value = { name: '', subCategory: '', mainMaterial: '', colorSeries: '', sampleLocation: '', items: [] }
      locked.value = false
    }
  } catch {
    form.value = { name: '', subCategory: '', mainMaterial: '', colorSeries: '', sampleLocation: '', items: [] }
    locked.value = false
  }
  editorVisible.value = true
}

// v7.7.5 添加用料弹窗（参照配方管理「添加节点」方式：类型→搜料→用量→连续添加）
const addDialogVisible = ref(false)
const addItemForm = ref({ itemType: 'MATERIAL', materialCode: '', materialName: '', category: '', subCategory: '', qty: 100 })
const addItemMaterials = computed(() =>
  addItemForm.value.itemType === 'SEMI'
    ? materials.value.filter(m => m.category === 'B')
    : materials.value.filter(m => 'APFRS'.includes(m.category || '')))
function openAddItem() {
  addItemForm.value = { itemType: addItemForm.value.itemType, materialCode: '', materialName: '', category: '', subCategory: '', qty: 100 }
  addDialogVisible.value = true
}
function onAddItemMatChange(code) {
  const m = materials.value.find(x => x.code === code)
  if (m) { addItemForm.value.materialName = m.name; addItemForm.value.category = m.category; addItemForm.value.subCategory = m.subCategory }
}
function confirmAddItem() {
  const f = addItemForm.value
  if (!f.materialCode) { ElMessage.warning('请选择物料'); return }
  if (!f.qty || f.qty <= 0) { ElMessage.warning('用量必须大于 0（克）'); return }
  form.value.items.push({ materialCode: f.materialCode, materialName: f.materialName, category: f.category, subCategory: f.subCategory, qty: f.qty })
  ElMessage.success(`已添加 ${f.materialName}，可继续添加`)
  openAddItem()   // 连续添加：清空物料保类型
}

// v7.7.2 带入参考配方：明细+分类作为起点（中文名留空——复样通常是新颜色/新版本，需起新名防物料重名）
function applyRef() {
  if (!refFormula.value?.items?.length) return
  form.value.items = refFormula.value.items.map(x => ({
    materialCode: x.materialCode, materialName: x.materialName,
    category: x.category, subCategory: x.subCategory, qty: Number(x.qty)
  }))
  if (!locked.value) {
    form.value.subCategory = refFormula.value.subCategory || form.value.subCategory
    form.value.mainMaterial = refFormula.value.mainMaterial || form.value.mainMaterial
    form.value.colorSeries = refFormula.value.colorSeries || form.value.colorSeries
    form.value.sampleLocation = form.value.sampleLocation || refFormula.value.sampleLocation || ''
  }
  ElMessage.success('已带入参考配方，请调整用量/颜色后保存')
}

async function save() {
  if (!form.value.items.length) { ElMessage.warning('请至少录入一行用料明细'); return }
  if (form.value.items.some(it => !it.materialCode)) { ElMessage.warning('存在未选择物料的明细行'); return }
  if (form.value.items.some(it => !it.qty || it.qty <= 0)) { ElMessage.warning('每行用量必须大于 0（克）'); return }
  if (!locked.value && (!form.value.name.trim() || !form.value.subCategory || !form.value.mainMaterial || !form.value.colorSeries)) {
    ElMessage.warning('首次保存需填齐：中文名、小类、主材、色系'); return
  }
  try {
    await ElMessageBox.confirm(
      locked.value ? '覆盖保存当前明细？保存前旧版会自动留档快照。' : `保存将创建成品物料「${form.value.name.trim()}」并自动生成编码，编码属性（小类/主材/色系）此后锁定。确定？`,
      '保存打样配方', { type: 'warning', confirmButtonText: '保存' })
  } catch { return }
  saving.value = true
  try {
    const payload = {
      name: form.value.name, subCategory: form.value.subCategory,
      mainMaterial: form.value.mainMaterial, colorSeries: form.value.colorSeries,
      sampleLocation: form.value.sampleLocation,
      items: form.value.items.map(it => ({ materialCode: it.materialCode, materialName: it.materialName, category: it.category, subCategory: it.subCategory, qty: it.qty }))
    }
    const f = await api.post(`/sample/${editorRow.value.id}/formula`, payload)
    ElMessage.success(`配方已保存${f.materialCode ? '，成品物料 ' + f.materialCode : ''}`)
    locked.value = true
    savedCode.value = f.materialCode || ''
    savedName.value = f.materialName || ''
    loadFormulas()
    load()
  } catch (e) { ElMessage.error(e.response?.data?.msg || '保存失败') }
  finally { saving.value = false }
}

// ============ 打印 ============
async function printOne(row) {
  const f = formulaOf(row.id)
  if (!f) return
  printSampleFormula(f)
  api.post('/print-count', { docType: 'SAMPLE_FORMULA', docNo: f.formulaNo }).catch(() => {})
}

async function loadFormulas() {
  try { formulas.value = await api.get('/sample/formulas') } catch { /* ignore */ }
}

onMounted(async () => {
  try {
    const u = JSON.parse(localStorage.getItem('user') || '{}')
    currentUser.value = u.username || u.realName || ''
  } catch { /* ignore */ }
  loadFormulas()
  load()
  // v7.7.8 权限归口：选料+参考价走打样模块聚合端点（sample:read 即可，不牵连 material:read/recipe:read）
  try {
    const agg = await api.get('/sample/formula/materials')
    materials.value = agg.materials || []
    priceMap.value = agg.prices || {}
  } catch { /* ignore */ }
  try {
    const all = await api.get('/dict')
    const map = {}
    for (const item of all) {
      if (!map[item.type]) map[item.type] = []
      map[item.type].push(item)
    }
    dicts.value = map
  } catch { /* ignore */ }

})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.page-header h2 { margin: 0; }
.sub { font-size: 13px; color: #64748b; }
.table-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.type-tabs { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; flex-wrap: wrap; }
.mine-toggle { font-size: 13px; color: #475569; display: flex; align-items: center; gap: 4px; cursor: pointer; }
.type-count { font-size: 13px; color: #64748b; margin-left: auto; }
.text-muted { color: #9ca3af; font-size: 12px; }
.text-center { text-align: center; }
.text-right { text-align: right; }
.editor { padding: 0 4px; max-height: calc(100vh - 220px); overflow-y: auto; }
.cost-bar { display: flex; align-items: baseline; gap: 8px; padding: 8px 12px; background: #f0fdf4; border-radius: 6px; margin-bottom: 10px; flex-wrap: wrap; }
.cost-bar-label { font-size: 13px; color: #64748b; }
.cost-bar-total { font-size: 16px; font-weight: 700; color: #15803d; }
.cost-bar-unit { font-size: 13px; color: #16a34a; font-weight: 600; }
.cost-bar-hint { font-size: 12px; color: #94a3b8; }
.ref-sec { background: #f8fafc; border: 1px dashed #cbd5e1; border-radius: 8px; padding: 10px 12px; }
.ref-total td { background: #eef2f7; font-weight: 600; }
.ed-sec { margin-bottom: 22px; }
.ed-sec-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 10px; display: flex; align-items: center; justify-content: space-between; }
.ed-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 10px 16px; }
.ed-field { display: flex; flex-direction: column; gap: 4px; }
.lbl { font-size: 12px; color: #64748b; }
.code-preview { font-size: 13px; color: #16a34a; font-weight: 600; line-height: 28px; }
.ed-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.ed-table th, .ed-table td { border: 1px solid #e2e8f0; padding: 6px 8px; }
.ed-table th { background: #f8fafc; color: #475569; font-weight: 600; }
.ed-table tfoot td { background: #f8fafc; }
.ed-foot { display: flex; align-items: center; gap: 12px; }
.saved-code { font-size: 13px; color: #16a34a; margin-right: auto; }
</style>
