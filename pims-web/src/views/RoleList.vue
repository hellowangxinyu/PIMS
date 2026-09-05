<template>
  <div class="page-container">
    <div class="page-header">
      <h2>角色权限</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showRoleDialog(null)" v-if="hasPerm('user:write')">新增角色</el-button>
      </div>
    </div>
    <div class="table-card">
      <p-table :data="roles" stripe border @header-dragend="onHeaderDragend">
        <el-table-column prop="code" label="编码" :width="cw('编码') || 130" />
        <el-table-column prop="name" label="名称" :width="cw('名称') || undefined" min-width="150" />
        <el-table-column prop="enabled" label="启用" :width="cw('启用') || 80" align="center">
          <template #default="{row}"><el-tag :type="row.enabled?'success':'danger'">{{ row.enabled?'是':'否' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" v-if="hasPerm('user:write')">
          <template #default="{row}">
            <button class="op-btn op-btn-primary" @click="editPerms(row)">权限</button>
            <button class="op-btn op-btn-primary" @click="showRoleDialog(row)">编辑</button>
            <button class="op-btn op-btn-danger" @click="delRole(row.id)">删除</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <!-- 角色编辑弹窗 -->
    <el-dialog :title="roleForm.id?'编辑角色':'新增角色'" v-model="roleDialogVisible" class="role-dialog">
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="编码"><el-input v-model="roleForm.code" :disabled="!!roleForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="roleForm.name" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="roleForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible=false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <!-- v5.68 权限矩阵弹窗（表格化：行=模块、列=操作+字段权限） -->
    <el-dialog :title="'配置权限 - ' + editingRole?.name" v-model="permDialogVisible" class="perm-dialog" destroy-on-close>
      <div class="perm-toolbar">
        <el-input v-model="permSearch" placeholder="搜索模块名（如：采购/凭证/工资）" clearable size="small" style="width:220px" :prefix-icon="Search" />
        <el-button size="small" @click="checkAll">全选</el-button>
        <el-button size="small" @click="uncheckAll">清空</el-button>
        <span class="perm-tip">勾选即授权；「—」表示该模块无此操作</span>
      </div>
      <el-table :data="filteredMatrix" border size="small" class="perm-matrix" :header-cell-style="{background:'#f5f7fa'}">
        <el-table-column prop="moduleLabel" label="模块" width="130" fixed="left" show-overflow-tooltip />
        <el-table-column v-for="col in opCols" :key="col.key" :label="col.label" :width="col.width" align="center">
          <template #header>
            <div class="col-header">
              <el-checkbox :model-value="colAllChecked(col.key)" :indeterminate="colIndeterminate(col.key)" @change="toggleCol(col.key, $event)" label="" size="small" />
              <span>{{ col.label }}</span>
            </div>
          </template>
          <template #default="{ row }">
            <el-checkbox v-if="row[col.key]" :model-value="checkedSet.has(row[col.key])" @change="togglePerm(row[col.key], $event)" />
            <span v-else class="no-perm">—</span>
          </template>
        </el-table-column>
        <el-table-column label="字段权限" :width="cw('字段权限') || undefined" min-width="160">
          <template #default="{ row }">
            <template v-if="row.fieldPerms && row.fieldPerms.length">
              <div v-for="fp in row.fieldPerms" :key="fp.code" class="field-perm-item">
                <el-checkbox :model-value="checkedSet.has(fp.code)" @change="togglePerm(fp.code, $event)" size="small">
                  {{ fp.label }}
                </el-checkbox>
              </div>
            </template>
            <span v-else class="no-perm">—</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="permDialogVisible=false">取消</el-button>
        <el-button type="primary" @click="savePerms" :loading="savingPerms">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

const roles = ref([])
const roleDialogVisible = ref(false)
const roleForm = ref({})
const permDialogVisible = ref(false)
const editingRole = ref(null)
const matrix = ref([])
const checkedSet = ref(new Set())
const savingPerms = ref(false)
const perms = ref([])
const permSearch = ref('')

// 搜索过滤矩阵行（模块名/权限码模糊匹配）
const filteredMatrix = computed(() => {
  const kw = permSearch.value.trim().toLowerCase()
  if (!kw) return matrix.value
  return matrix.value.filter(r =>
    (r.moduleLabel || '').toLowerCase().includes(kw) ||
    (r.moduleKey || '').toLowerCase().includes(kw)
  )
})

const { cw, onHeaderDragend } = useColumnResize('role')

function hasPerm(code) { return perms.value.includes(code) }

// 操作列定义（key 对应矩阵 row 的字段名）
const opCols = [
  { key: 'read', label: '查看', width: 70 },
  { key: 'write', label: '编辑', width: 70 },
  { key: 'delete', label: '删除', width: 70 },
  { key: 'audit', label: '审核', width: 70 },
  { key: 'reverseAudit', label: '反审核', width: 75 },
]

async function fetchMatrix() {
  try { matrix.value = await api.get('/role/permissions/matrix') } catch {}
}
async function fetchRoles() {
  roles.value = await api.get('/role')
}

function showRoleDialog(row) {
  roleForm.value = row ? { ...row } : { enabled: true }
  roleDialogVisible.value = true
}

async function saveRole() {
  if (roleForm.value.id) await api.put(`/role/${roleForm.value.id}`, roleForm.value)
  else await api.post('/role', roleForm.value)
  roleDialogVisible.value = false
  ElMessage.success('保存成功')
  fetchRoles()
}

async function delRole(id) {
  await ElMessageBox.confirm('确定删除该角色？')
  await api.delete(`/role/${id}`)
  ElMessage.success('已禁用')
  fetchRoles()
}

async function editPerms(row) {
  editingRole.value = row
  const checked = await api.get(`/role/${row.code}/permissions`)
  checkedSet.value = new Set(checked)
  permDialogVisible.value = true
}

// 勾选/取消单个权限码
function togglePerm(code, val) {
  const s = new Set(checkedSet.value)
  if (val) s.add(code); else s.delete(code)
  checkedSet.value = s
}

// 列全选/取消（某操作列的所有模块）
function colAllChecked(key) {
  const codes = matrix.value.filter(r => r[key]).map(r => r[key])
  return codes.length > 0 && codes.every(c => checkedSet.value.has(c))
}
function colIndeterminate(key) {
  const codes = matrix.value.filter(r => r[key]).map(r => r[key])
  const hit = codes.filter(c => checkedSet.value.has(c)).length
  return hit > 0 && hit < codes.length
}
function toggleCol(key, val) {
  const s = new Set(checkedSet.value)
  for (const r of matrix.value) {
    if (r[key]) { if (val) s.add(r[key]); else s.delete(r[key]) }
  }
  checkedSet.value = s
}

function checkAll() {
  const s = new Set()
  for (const r of matrix.value) {
    for (const col of opCols) if (r[col.key]) s.add(r[col.key])
    if (r.fieldPerms) for (const fp of r.fieldPerms) s.add(fp.code)
  }
  checkedSet.value = s
}
function uncheckAll() { checkedSet.value = new Set() }

async function savePerms() {
  savingPerms.value = true
  try {
    await api.put(`/role/${editingRole.value.code}/permissions`, [...checkedSet.value])
    ElMessage.success('权限已更新，该角色用户需重新登录生效')
    permDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally { savingPerms.value = false }
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  await Promise.all([fetchRoles(), fetchMatrix()])
})
</script>

<style scoped>
.page-container { width: 100%; }

.perm-toolbar {
  display: flex; align-items: center; gap: 8px; margin-bottom: 12px;
  padding-bottom: 10px; border-bottom: 1px solid #eee; flex-wrap: wrap;
}
.perm-tip { font-size: 12px; color: #999; }

.perm-matrix { max-height: 480px; overflow-y: auto; }
.perm-matrix::-webkit-scrollbar { width: 4px; }
.perm-matrix::-webkit-scrollbar-thumb { background: #ddd; border-radius: 2px; }

.col-header { display: flex; align-items: center; justify-content: center; gap: 2px; }
.col-header span { font-size: 12px; }

.no-perm { color: #d0d5dd; font-size: 12px; }
.field-perm-item { margin: 2px 0; }

.role-dialog { width: min(400px, 92vw) !important; }
.perm-dialog { width: min(820px, 95vw) !important; }

@media (max-width: 768px) {
  .perm-dialog { width: 95% !important; }
}
</style>
