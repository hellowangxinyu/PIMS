<template>
  <div class="page-container">
    <div class="page-header">
      <h2>用户管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showDialog(null)" v-if="hasPerm('user:write')">新增用户</el-button>
      </div>
    </div>
    <div class="table-card">
      <p-table :data="list" stripe border @header-dragend="onHeaderDragend">
        <el-table-column prop="username" label="用户名" :width="cw('用户名') || 120" />
        <el-table-column prop="realName" label="姓名" :width="cw('姓名') || 100" />
        <el-table-column label="角色" :width="cw('角色') || 120" align="center">
          <template #default="{row}">{{ roleNames.find(r=>r.code===row.role)?.name || row.role }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="电话" :width="cw('电话') || 130" />
        <el-table-column label="状态" :width="cw('状态') || 80" align="center">
          <template #default="{row}">
            <el-tag :type="row.enabled?'success':'danger'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" :width="cw('创建时间') || 170" />
        <el-table-column label="操作" width="210" align="center" v-if="hasPerm('user:write')">
          <template #default="{row}">
            <button class="op-btn op-btn-primary" @click="showDialog(row)">编辑</button>
            <button class="op-btn op-btn-primary" @click="resetPwd(row)">重置密码</button>
            <button class="op-btn op-btn-danger" @click="del(row.id)">禁用</button>
          </template>
        </el-table-column>
      </p-table>
    </div>

    <el-dialog :title="form.id?'编辑用户':'新增用户'" v-model="dialogVisible" class="form-dialog">
      <el-form :model="form" label-width="80px">
        <el-form-item v-if="!form.id" label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width:100%">
            <el-option v-for="r in roleNames" :key="r.code" :label="r.name" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

const list = ref([])
const dialogVisible = ref(false)
const form = ref({})
const roleNames = ref({})
const perms = ref([])

// 表格列宽拖拽与持久化
const { cw, onHeaderDragend } = useColumnResize('user')

function hasPerm(code) { return perms.value.includes(code) }

async function fetch() {
  list.value = await api.get('/user')
  try { roleNames.value = await api.get('/role') } catch {}
}

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  await fetch()
})

function showDialog(row) {
  form.value = row ? { ...row, password: '' } : { enabled: true }
  dialogVisible.value = true
}

async function save() {
  if (form.value.id) {
    await api.put(`/user/${form.value.id}`, form.value)
  } else {
    await api.post('/user', form.value)
  }
  dialogVisible.value = false
  ElMessage.success('保存成功')
  fetch()
}

async function resetPwd(row) {
  const { value } = await ElMessageBox.prompt('请输入新密码', '重置密码', { inputType: 'password' })
  if (value) {
    await api.put(`/user/${row.id}/reset-password`, { password: value })
    ElMessage.success('密码已重置')
  }
}

async function del(id) {
  await ElMessageBox.confirm('确定禁用该用户？')
  await api.delete(`/user/${id}`)
  ElMessage.success('已禁用')
  fetch()
}

</script>

<style scoped>
.page-container {
  width: 100%;
}

@media (max-width: 500px) {
  .form-dialog {
    width: 92% !important;
  }
}
</style>
