<template>
  <div class="page-container">
    <!-- ==================== 列表模式 ==================== -->
    <template v-if="mode === 'list'">
      <div class="page-header">
        <h2>工艺路线</h2>
      <div class="header-actions">
          <el-button type="primary" @click="openCreate">+ 新建路线</el-button>
      </div>
      </div>

      <div class="type-tabs">
        <button class="type-tab" :class="{ active: filter === '' }" @click="setFilter('')">全部</button>
        <button class="type-tab" :class="{ active: filter === 'GRINDING' }" @click="setFilter('GRINDING')">制浆</button>
        <button class="type-tab" :class="{ active: filter === 'TINTING' }" @click="setFilter('TINTING')">制漆</button>
      </div>

      <p-table :data="routes" border stripe style="width:100%">
        <el-table-column label="路线名称" min-width="220">
          <template #default="{ row }">
            <span class="route-name">{{ row.name }}</span>
            <span v-if="row.isDefault" class="default-star" title="该类型默认路线">★</span>
            <el-tag v-if="!row.enabled" size="small" type="info" style="margin-left:6px">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">{{ row.recipeType === 'GRINDING' ? '制浆' : '制漆' }}</template>
        </el-table-column>
        <el-table-column label="工序数" width="80" prop="stageCount" align="center" />
        <el-table-column label="绑定配方" width="90" prop="recipeCount" align="center" />
        <el-table-column label="创建人" width="90" align="center">
          <template #default="{ row }">{{ row.createdBy || '—' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="openEdit(row)">编辑</button>
            <button class="op-btn op-btn-primary" @click="copyRoute(row)">复制</button>
            <button v-if="!row.isDefault" class="op-btn op-btn-success" @click="makeDefault(row)">设默认</button>
            <button class="op-btn op-btn-danger" @click="removeRoute(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
      <el-empty v-if="!routes.length" description="暂无工艺路线，点右上方新建" :image-size="70" />
    </template>

    <!-- ==================== 编辑模式 ==================== -->
    <template v-else>
      <div class="page-header">
        <h2 class="edit-title">
          <el-button text @click="backToList">← 返回列表</el-button>
          {{ editingId ? '编辑工艺路线' : '新建工艺路线' }}
        </h2>
        <el-button type="primary" @click="save" :loading="saving">保存路线</el-button>
      </div>

      <div class="meta-row">
        <span class="meta-label">路线名称：</span>
        <el-input v-model="tpl.name" style="width:280px" placeholder="如：制浆标准工艺V6" />
        <span class="meta-label">类型：</span>
        <el-select v-model="tpl.recipeType" style="width:150px">
          <el-option label="制浆（GRINDING）" value="GRINDING" />
          <el-option label="制漆（TINTING）" value="TINTING" />
        </el-select>
        <el-checkbox v-model="tpl.enabled" style="margin-left:16px">启用</el-checkbox>
      </div>

      <div class="tip">
        步骤描述里可用 <code v-pre>{{1}}</code> <code v-pre>{{2}}</code> 等占位符代表配方物料的投料顺序（按配方树排序第 N 项），
        在配方页展示和打印工艺指导单时会自动替换为对应物料名称/编码。
      </div>

      <div class="pack-row">
        <span class="pack-label">包装要求：</span>
        <el-input v-model="tpl.packingRequirement" type="textarea" :rows="2" placeholder="如：成品用 20L 铁桶灌装，净重 20kg/桶，桶身贴标签（名称/批号/生产日期），码放托盘并用缠绕膜固定…" />
      </div>

      <div class="stages">
        <div class="stage-card" v-for="(stg, si) in tpl.stages" :key="si">
          <div class="stage-head">
            <el-input v-model="stg.stageNo" size="small" style="width:70px" placeholder="01" />
            <el-input v-model="stg.stageName" size="small" style="width:200px" placeholder="工序名称（如：预混）" />
            <el-input v-model="stg.roleHint" size="small" style="width:150px" placeholder="责任岗位（如：配料人）" />
            <span class="stage-spacer"></span>
            <el-button type="danger" text size="small" @click="removeStage(si)">删除工序</el-button>
          </div>

          <div class="sub-title">操作步骤</div>
          <div class="step-row" v-for="(stp, ti) in stg.steps" :key="ti">
            <el-input v-model="stp.stepCode" size="small" style="width:56px" placeholder="A" />
            <el-input v-model="stp.description" size="small" style="flex:1;min-width:0" placeholder="操作描述（可用 {{1}} {{2}} 占位物料）" />
            <el-input v-model="stp.params" size="small" style="width:190px;flex-shrink:0" placeholder="参数（如 500-600rpm、30min）" />
            <el-button type="danger" text size="small" @click="removeStep(si, ti)">✕</el-button>
          </div>
          <el-button plain size="small" class="add-line" @click="addStep(si)">+ 添加步骤</el-button>

          <div class="sub-title">质检项</div>
          <div class="qc-row" v-for="(qc, qi) in stg.qcItems" :key="qi">
            <el-input v-model="qc.name" size="small" style="width:150px" placeholder="名称（如：细度）" />
            <el-input v-model="qc.standard" size="small" style="width:150px" placeholder="标准（如：≤10μm）" />
            <span class="row-label">检测次数</span>
            <el-input-number v-model="qc.testTimes" :min="1" :max="20" size="small" style="width:100px" />
            <el-input v-model="qc.unit" size="small" style="width:80px" placeholder="单位" />
            <el-button type="danger" text size="small" @click="removeQc(si, qi)">✕</el-button>
          </div>
          <el-button plain size="small" class="add-line" @click="addQc(si)">+ 添加质检项</el-button>
        </div>
      </div>

      <el-button type="primary" plain class="add-stage-btn" @click="addStage">+ 添加工序</el-button>
      <el-empty v-if="!tpl.stages || !tpl.stages.length" description="暂无工序，点上方添加" :image-size="70" />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

// ==================== 列表模式 ====================
const mode = ref('list')
const filter = ref('')
const routes = ref([])

async function fetchRoutes() {
  try {
    routes.value = await api.get('/process/routes' + (filter.value ? '?recipeType=' + filter.value : ''))
  } catch { routes.value = [] }
}
function setFilter(f) { filter.value = f; fetchRoutes() }
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '' }

async function copyRoute(row) {
  try {
    await api.post(`/process/route/${row.id}/copy`)
    ElMessage.success('已复制为新路线')
    fetchRoutes()
  } catch {}
}
async function makeDefault(row) {
  try {
    await api.post(`/process/route/${row.id}/set-default`)
    ElMessage.success('已设为默认路线')
    fetchRoutes()
  } catch {}
}
async function removeRoute(row) {
  try {
    await ElMessageBox.confirm(
      row.recipeCount > 0 ? `该路线被 ${row.recipeCount} 个配方使用，无法删除。` : `确定删除路线「${row.name}」？删除后不可恢复。`,
      '删除工艺路线',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonDisabled: row.recipeCount > 0 }
    )
  } catch { return }
  try {
    await api.delete(`/process/route/${row.id}`)
    ElMessage.success('已删除')
    fetchRoutes()
  } catch {}
}

// ==================== 编辑模式 ====================
const editingId = ref(null)
const tpl = ref({ name: '', recipeType: 'GRINDING', packingRequirement: '', enabled: true, stages: [] })
const saving = ref(false)

function openCreate() {
  editingId.value = null
  tpl.value = { name: '', recipeType: filter.value || 'GRINDING', packingRequirement: '', enabled: true, stages: [] }
  mode.value = 'edit'
}
async function openEdit(row) {
  try {
    const data = await api.get(`/process/route/${row.id}`)
    editingId.value = row.id
    tpl.value = {
      name: data.name || '',
      recipeType: data.recipeType || 'GRINDING',
      packingRequirement: data.packingRequirement || '',
      enabled: data.enabled !== false,
      stages: data.stages || []
    }
    mode.value = 'edit'
  } catch {}
}
function backToList() { mode.value = 'list'; fetchRoutes() }

async function save() {
  if (!tpl.value.name || !tpl.value.name.trim()) { ElMessage.warning('请填写路线名称'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await api.put(`/process/route/${editingId.value}`, tpl.value)
    } else {
      await api.post('/process/route', tpl.value)
    }
    ElMessage.success('工艺路线已保存')
    backToList()
  } catch {} finally { saving.value = false }
}

// ==================== 工序编辑（复用原逻辑） ====================
function addStage() {
  const no = String(tpl.value.stages.length + 1).padStart(2, '0')
  tpl.value.stages.push({ stageNo: no, stageName: '', roleHint: '', sortOrder: tpl.value.stages.length, steps: [], qcItems: [] })
}
function removeStage(i) { tpl.value.stages.splice(i, 1) }
function addStep(si) {
  const steps = tpl.value.stages[si].steps || (tpl.value.stages[si].steps = [])
  steps.push({ stepCode: String.fromCharCode(65 + steps.length), description: '', params: '', sortOrder: steps.length })
}
function removeStep(si, ti) { tpl.value.stages[si].steps.splice(ti, 1) }
function addQc(si) {
  const qcs = tpl.value.stages[si].qcItems || (tpl.value.stages[si].qcItems = [])
  qcs.push({ name: '', standard: '', testTimes: 1, unit: '', method: '', sortOrder: qcs.length })
}
function removeQc(si, qi) { tpl.value.stages[si].qcItems.splice(qi, 1) }

onMounted(fetchRoutes)
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.edit-title { display: flex; align-items: center; gap: 8px; }
.type-tabs { display: flex; gap: 0; margin-bottom: 14px; background: #f1f5f9; border-radius: 8px; padding: 3px; width: fit-content; }
.type-tab { padding: 8px 24px; font-size: 13px; font-weight: 600; border: none; border-radius: 6px; cursor: pointer; background: transparent; color: #64748b; transition: all 0.2s; }
.type-tab:hover { color: #334155; }
.type-tab.active { background: #fff; color: #1d4ed8; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.route-name { font-weight: 600; }
.default-star { color: #f59e0b; margin-left: 4px; }
.meta-row { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.meta-label { font-size: 13px; font-weight: 600; color: #475569; white-space: nowrap; margin-left: 8px; }
.tip { font-size: 13px; color: #475569; background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 6px; padding: 10px 14px; margin-bottom: 16px; }
.tip code { background: #e0f2fe; color: #0369a1; padding: 1px 6px; border-radius: 3px; font-family: Consolas, monospace; }
.pack-row { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 16px; }
.pack-label { font-size: 13px; font-weight: 600; color: #475569; padding-top: 6px; white-space: nowrap; }
.stages { display: flex; flex-direction: column; gap: 14px; }
.stage-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 14px 16px; }
.stage-head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.stage-spacer { flex: 1; }
.sub-title { font-size: 13px; font-weight: 600; color: #334155; margin: 10px 0 6px; }
.step-row, .qc-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.row-label { font-size: 12px; color: #64748b; white-space: nowrap; }
.add-line { margin-bottom: 4px; }
.add-stage-btn { margin-top: 14px; }
</style>
