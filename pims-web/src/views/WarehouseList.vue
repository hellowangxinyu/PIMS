<template>
  <div class="page-container">
    <div class="page-header">
      <h2>仓库管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showWarehouseForm(null)" v-if="hasPerm('warehouse:write')">新增仓库</el-button>
      </div>
    </div>

    <!-- 仓库列表 -->
    <div class="table-card">
      <div class="status-tabs">
        <button class="status-tab" :class="{ active: enabledFilter === 'ENABLED' }" @click="enabledFilter = 'ENABLED'">启用中 <span class="tab-badge">{{ enabledCount }}</span></button>
        <button class="status-tab" :class="{ active: enabledFilter === 'DISABLED' }" @click="enabledFilter = 'DISABLED'">已禁用 <span class="tab-badge">{{ disabledCount }}</span></button>
      </div>
      <p-table :data="filteredWarehouses" stripe border highlight-current-row @current-change="onWarehouseSelect" @header-dragend="onHeaderDragend">
        <el-table-column prop="code" label="编码" :width="cw('编码') || 140" />
        <el-table-column prop="name" label="名称" :width="cw('名称') || undefined" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" :width="cw('类型') || 110" align="center">
          <template #default="{row}">{{ typeMap[row.warehouseType] || row.warehouseType }}</template>
        </el-table-column>
        <el-table-column label="所属工厂" :width="cw('所属工厂') || 140" show-overflow-tooltip>
          <template #default="{row}">{{ row.processorName || row.processorId || '—' }}</template>
        </el-table-column>
        <el-table-column prop="address" label="地址" :width="cw('地址') || undefined" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" :width="cw('状态') || 80" align="center">
          <template #default="{row}"><el-tag size="small" :type="row.enabled?'success':'danger'">{{ row.enabled?'启用':'禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" v-if="hasPerm('warehouse:write')||hasPerm('warehouse:delete')">
          <template #default="{row}">
            <button class="op-btn op-btn-primary" @click.stop="showWarehouseForm(row)">编辑</button>
            <button v-if="!isUnqualified(row)" class="op-btn" :class="row.enabled ? 'op-btn-warn' : 'op-btn-success'" @click.stop="toggleWarehouse(row)">{{ row.enabled ? '禁用' : '启用' }}</button>
            <button v-if="!isUnqualified(row)" class="op-btn op-btn-danger" @click.stop="delWarehouse(row.id)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <!-- 分库 & 库位管理区域 -->
    <div class="zone-section" v-if="selectedWarehouse">
      <div class="zone-header">
        <h3>
          <el-icon><FolderOpened /></el-icon>
          {{ selectedWarehouse.name }} — 分库管理
        </h3>
        <el-button type="primary" size="small" @click="showZoneForm(null)" v-if="hasPerm('warehouse:write')">新增分库</el-button>
      </div>
      <p-table :data="zones" stripe border size="small" highlight-current-row @current-change="onZoneSelect" empty-text="暂无分库，点击上方按钮新增" @header-dragend="onHeaderDragendZone">
        <el-table-column prop="code" label="编码" :width="cwZone('编码') || 120" />
        <el-table-column prop="name" label="名称" :width="cw('名称') || undefined" min-width="140" show-overflow-tooltip>
          <template #default="{row}">
            {{ row.name }}
            <el-tag v-if="row.zoneType === 'UNQUALIFIED_RAW'" size="small" type="danger" style="margin-left:6px">原材料不合格品库·保护</el-tag>
            <el-tag v-else-if="row.zoneType === 'UNQUALIFIED_SEMI'" size="small" type="danger" style="margin-left:6px">半成品不合格品库·保护</el-tag>
            <el-tag v-else-if="row.zoneType === 'UNQUALIFIED_FIN'" size="small" type="danger" style="margin-left:6px">成品不合格品库·保护</el-tag>
            <el-tag v-else-if="row.zoneType === 'TAILING'" size="small" type="warning" style="margin-left:6px">聚酯油尾库·保护</el-tag>
            <el-tag v-else-if="row.zoneType === 'TAILING_FC'" size="small" type="warning" style="margin-left:6px">氟碳油尾库·保护</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" :width="cwZone('排序') || 80" align="center" />
        <el-table-column prop="remark" label="备注" :width="cw('备注') || undefined" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" :width="cwZone('状态') || 70" align="center">
          <template #default="{row}"><el-tag size="small" :type="row.enabled?'success':'danger'">{{ row.enabled?'启用':'禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" :width="cwZone('操作') || 130" align="center" v-if="hasPerm('warehouse:write')">
          <template #default="{row}">
            <button class="op-btn op-btn-primary" @click.stop="showZoneForm(row)" :disabled="!!row.zoneType">编辑</button>
            <button class="op-btn op-btn-danger" @click.stop="delZone(row.id)" :disabled="!!row.zoneType">删除</button>
          </template>
        </el-table-column>
      </p-table>

      <!-- 库位管理 -->
      <div class="location-section" v-if="selectedZone">
        <div class="location-header">
          <h4>
            <el-icon><Grid /></el-icon>
            {{ selectedZone.name }} — 库位管理
          </h4>
          <el-button type="primary" size="small" @click="showLocationForm(null)" v-if="hasPerm('warehouse:write')">新增库位</el-button>
        </div>
        <p-table :data="locations" stripe border size="small" empty-text="暂无库位，点击上方按钮新增" @header-dragend="onHeaderDragendLoc">
          <el-table-column prop="code" label="编码" :width="cwLoc('编码') || 120" />
          <el-table-column prop="name" label="名称" :width="cw('名称') || undefined" min-width="140" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="排序" :width="cwLoc('排序') || 80" align="center" />
          <el-table-column prop="remark" label="备注" :width="cw('备注') || undefined" min-width="160" show-overflow-tooltip />
          <el-table-column label="状态" :width="cwLoc('状态') || 70" align="center">
            <template #default="{row}"><el-tag size="small" :type="row.enabled?'success':'danger'">{{ row.enabled?'启用':'禁用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" :width="cwLoc('操作') || 130" align="center" v-if="hasPerm('warehouse:write')">
            <template #default="{row}">
              <!-- v5.58：隔离分库下的库位与分库同级保护，禁止编辑/删除（新增库位不受限） -->
              <button class="op-btn op-btn-primary" @click="showLocationForm(row)" :disabled="!!selectedZone.zoneType">编辑</button>
              <button class="op-btn op-btn-danger" @click="delLocation(row.id)" :disabled="!!selectedZone.zoneType">删除</button>
            </template>
          </el-table-column>
        </p-table>
      </div>
    </div>

    <!-- 仓库表单 -->
    <el-dialog :title="warehouseForm.id?'编辑仓库':'新增仓库'" v-model="warehouseVisible" width="min(1100px, 96vw)">
      <el-form :model="warehouseForm" label-width="80px" size="small">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="编码"><el-input v-model="warehouseForm.code" :disabled="!!warehouseForm.id" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="名称"><el-input v-model="warehouseForm.name" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="类型">
          <el-select v-model="warehouseForm.warehouseType" :disabled="isUnqualified(warehouseForm)" style="width:100%">
            <el-option v-for="(label, code) in typeMap" :key="code" :label="label" :value="code" />
          </el-select>
          <div v-if="isUnqualified(warehouseForm)" class="form-tip">不合格品库为系统隔离仓，类型不可修改</div>
        </el-form-item>
        <el-form-item label="所属工厂">
          <el-select v-model="warehouseForm.processorName" filterable clearable placeholder="选择代工厂（供应商档案中维护）" style="width:100%" @change="onProcessorChange">
            <el-option v-for="s in processorSuppliers" :key="s.id" :label="s.name" :value="s.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址"><el-input v-model="warehouseForm.address" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="联系人"><el-input v-model="warehouseForm.contactPerson" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="电话"><el-input v-model="warehouseForm.contactPhone" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="warehouseForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="warehouseVisible=false">取消</el-button>
        <el-button type="primary" @click="saveWarehouse" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分库表单 -->
    <el-dialog :title="zoneForm.id?'编辑分库':'新增分库'" v-model="zoneVisible" width="min(1100px, 96vw)">
      <el-form :model="zoneForm" label-width="70px" size="small">
        <el-form-item label="名称"><el-input v-model="zoneForm.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="zoneForm.sortOrder" :min="0" :max="9999" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="zoneForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="zoneVisible=false">取消</el-button>
        <el-button type="primary" @click="saveZone" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 库位表单 -->
    <el-dialog :title="locationForm.id?'编辑库位':'新增库位'" v-model="locationVisible" width="min(1100px, 96vw)">
      <el-form :model="locationForm" label-width="70px" size="small">
        <el-form-item label="名称"><el-input v-model="locationForm.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="locationForm.sortOrder" :min="0" :max="9999" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="locationForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="locationVisible=false">取消</el-button>
        <el-button type="primary" @click="saveLocation" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, Grid } from '@element-plus/icons-vue'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

const list = ref([])
const zones = ref([])
const locations = ref([])
const selectedWarehouse = ref(null)
const selectedZone = ref(null)
const saving = ref(false)

// 仓库表单
const warehouseVisible = ref(false)
const warehouseForm = ref({})

// 分库表单
const zoneVisible = ref(false)
const zoneForm = ref({})

// 库位表单
const locationVisible = ref(false)
const locationForm = ref({})

const perms = ref([])

const { cw, onHeaderDragend } = useColumnResize('warehouse')

// 启用/禁用 Tab 过滤（禁用仓库收进「已禁用」页签）
const enabledFilter = ref('ENABLED')
const filteredWarehouses = computed(() => list.value.filter(w => {
  if (enabledFilter.value === 'ENABLED') return w.enabled !== false
  return w.enabled === false
}))
const enabledCount = computed(() => list.value.filter(w => w.enabled !== false).length)
const disabledCount = computed(() => list.value.filter(w => w.enabled === false).length)
const { cw: cwZone, onHeaderDragend: onHeaderDragendZone } = useColumnResize('warehouse_zone')
const { cw: cwLoc, onHeaderDragend: onHeaderDragendLoc } = useColumnResize('warehouse_location')

function hasPerm(code) { return perms.value.includes(code) }

const typeMap = {
  'OWN_RAW': '自有原料仓',
  'OUT_RAW': '委外原料仓',
  'OWN_FINISHED': '自有成品仓',
  'OUT_FINISHED': '委外成品仓',
  'OWN_UNQUALIFIED': '不合格品库'
}

// v5.30：不合格品库为系统隔离仓——禁止改类型/删除/禁用（后端同样兜底拦截）
function isUnqualified(row) { return row?.warehouseType === 'OWN_UNQUALIFIED' }

// ==================== 仓库 ====================

async function fetch() { list.value = await api.get('/warehouse') }

function onWarehouseSelect(row) {
  selectedWarehouse.value = row
  selectedZone.value = null
  locations.value = []
  if (row) fetchZones(row.id)
  else zones.value = []
}

function showWarehouseForm(row) {
  warehouseForm.value = row ? { ...row } : {}
  warehouseVisible.value = true
}

async function saveWarehouse() {
  saving.value = true
  try {
    if (warehouseForm.value.id) {
      await api.put(`/warehouse/${warehouseForm.value.id}`, warehouseForm.value)
    } else {
      await api.post('/warehouse', warehouseForm.value)
    }
    ElMessage.success('保存成功')
    warehouseVisible.value = false
    fetch()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '保存失败')
  } finally {
    saving.value = false
  }
}

async function delWarehouse(id) {
  try {
    await ElMessageBox.confirm('确定删除该仓库？')
    await api.delete(`/warehouse/${id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.msg || '删除失败')
      fetch()
    }
  }
}

// 禁用/启用仓库（有出入库记录的仓库不能删除，但可禁用）
async function toggleWarehouse(row) {
  const next = !row.enabled
  try {
    await ElMessageBox.confirm(`确定${next ? '禁用' : '启用'}仓库「${row.name}」？${next ? '\n禁用后该仓库不能再用于出入库。' : ''}`, next ? '禁用仓库' : '启用仓库', { type: 'warning' })
    await api.put(`/warehouse/${row.id}/enabled`, null, { params: { enabled: next } })
    ElMessage.success(`已${next ? '禁用' : '启用'}`)
    fetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.msg || '操作失败') }
}

// ==================== 分库 ====================

async function fetchZones(warehouseId) {
  zones.value = await api.get(`/warehouse/${warehouseId}/zone`)
}

function onZoneSelect(row) {
  selectedZone.value = row
  if (row) fetchLocations(row.id)
  else locations.value = []
}

function showZoneForm(row) {
  zoneForm.value = row ? { ...row } : { warehouseId: selectedWarehouse.value.id, sortOrder: 0 }
  zoneVisible.value = true
}

async function saveZone() {
  saving.value = true
  try {
    if (zoneForm.value.id) {
      await api.put(`/warehouse/zone/${zoneForm.value.id}`, zoneForm.value)
    } else {
      await api.post(`/warehouse/${selectedWarehouse.value.id}/zone`, zoneForm.value)
    }
    ElMessage.success('保存成功')
    zoneVisible.value = false
    fetchZones(selectedWarehouse.value.id)
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '保存失败')
  } finally {
    saving.value = false
  }
}

async function delZone(id) {
  try {
    await ElMessageBox.confirm('确定删除该分库？其下库位也将被禁用。')
    await api.delete(`/warehouse/zone/${id}`)
    ElMessage.success('已删除')
    selectedZone.value = null
    locations.value = []
    fetchZones(selectedWarehouse.value.id)
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.msg || '删除失败') }
}

// ==================== 库位 ====================

async function fetchLocations(zoneId) {
  locations.value = await api.get(`/warehouse/zone/${zoneId}/location`)
}

function showLocationForm(row) {
  locationForm.value = row ? { ...row } : { zoneId: selectedZone.value.id, sortOrder: 0 }
  locationVisible.value = true
}

async function saveLocation() {
  saving.value = true
  try {
    if (locationForm.value.id) {
      await api.put(`/warehouse/location/${locationForm.value.id}`, locationForm.value)
    } else {
      await api.post(`/warehouse/zone/${selectedZone.value.id}/location`, locationForm.value)
    }
    ElMessage.success('保存成功')
    locationVisible.value = false
    fetchLocations(selectedZone.value.id)
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '保存失败')
  } finally {
    saving.value = false
  }
}

async function delLocation(id) {
  try {
    await ElMessageBox.confirm('确定删除该库位？')
    await api.delete(`/warehouse/location/${id}`)
    ElMessage.success('已删除')
    fetchLocations(selectedZone.value.id)
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.msg || '删除失败') }
}

// 所属工厂下拉：只从供应商档案（类型=代工厂）中选择
const processorSuppliers = ref([])
async function loadProcessors() {
  try { processorSuppliers.value = await api.get('/supplier', { params: { type: 'PROCESSOR', enabled: true } }) } catch {}
}
function onProcessorChange(name) {
  const s = processorSuppliers.value.find(x => x.name === name)
  warehouseForm.value.processorId = s ? String(s.id) : ''
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  await loadProcessors()
  await fetch()
})
</script>

<style scoped>
.page-container {
  width: 100%;
}
.form-tip {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
.zone-section {
  margin-top: 16px;
  background: #fff;
  border-radius: 6px;
  padding: 16px;
  border: 1px solid #ebeef5;
}
.zone-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.zone-header h3 {
  margin: 0;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #303133;
}
.location-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed #dcdfe6;
}
.location-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.location-header h4 {
  margin: 0;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
}
.status-tabs { display: flex; gap: 0; margin-bottom: 12px; background: #f1f5f9; border-radius: 8px; padding: 3px; width: fit-content; }
.status-tab { padding: 6px 20px; font-size: 13px; font-weight: 600; border: none; border-radius: 6px; cursor: pointer; background: transparent; color: #64748b; transition: all 0.2s; }
.status-tab:hover { color: #334155; }
.status-tab.active { background: #fff; color: #4a6785; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.tab-badge { display: inline-block; min-width: 18px; padding: 0 5px; margin-left: 4px; font-size: 11px; line-height: 16px; border-radius: 9px; background: rgba(0,0,0,0.08); color: inherit; }
.status-tab.active .tab-badge { background: rgba(29,78,216,0.12); }
</style>
