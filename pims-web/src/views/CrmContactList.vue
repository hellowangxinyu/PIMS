<template>
  <div class="page-container">
    <div class="page-header">
      <h2>联系人</h2>
      <div class="header-actions">
        <el-input v-model="keyword" placeholder="搜索姓名/公司/电话" clearable style="width:200px" />
        <el-select v-model="custFilter" clearable filterable placeholder="按客户筛选" style="width:180px">
          <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button type="primary" @click="openDialog(null)" v-if="hasPerm('crm:write')">新增联系人</el-button>
      </div>
    </div>

    <div class="table-card">
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="name" label="姓名" width="100">
          <template #default="{ row }">
            {{ row.name }}
            <el-tag v-if="row.isPrimary" size="small" type="success" style="margin-left:4px">主</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="职务" width="110" />
        <el-table-column prop="companyName" label="公司" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="wechat" label="微信" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="150" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="130" align="center" v-if="hasPerm('crm:write')">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="openDialog(row)">编辑</button>
            <button class="op-btn op-btn-danger" @click="del(row)">删除</button>
          </template>
        </el-table-column>
      </p-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]" :total="filtered.length" layout="total, sizes, prev, pager, next" />
      </div>
    </div>

    <el-dialog :title="editing ? '编辑联系人' : '新增联系人'" v-model="visible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="关联客户">
          <el-select v-model="form.customerId" filterable clearable placeholder="选择正式客户（线索可不选）" style="width:100%" @change="onCustChange">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="公司名称" required>
          <el-input v-model="form.companyName" placeholder="选客户自动带出，线索可手填" />
        </el-form-item>
        <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="职务"><el-input v-model="form.title" placeholder="如：采购经理/技术总监" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="微信"><el-input v-model="form.wechat" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="主要联系人"><el-switch v-model="form.isPrimary" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="loading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'

const list = ref([])
const customers = ref([])
const perms = ref([])
const visible = ref(false)
const loading = ref(false)
const editing = ref(null)
const keyword = ref('')
const custFilter = ref('')
const form = ref(emptyForm())

function emptyForm() {
  return { customerId: null, companyName: '', name: '', title: '', phone: '', wechat: '', email: '', isPrimary: false, remark: '' }
}
function hasPerm(c) { return perms.value.includes(c) }

const filtered = computed(() => {
  let arr = list.value
  if (custFilter.value) arr = arr.filter(c => c.customerId === custFilter.value)
  const kw = keyword.value.trim().toLowerCase()
  if (kw) arr = arr.filter(c => [c.name, c.companyName, c.phone, c.wechat, c.title].some(v => v && v.toLowerCase().includes(kw)))
  return arr
})

async function fetch() {
  resetPage()
  try { list.value = await api.get('/crm/contact') } catch {}
}

function onCustChange(id) {
  const c = customers.value.find(x => x.id === id)
  if (c) form.value.companyName = c.name
}

function openDialog(row) {
  editing.value = row
  form.value = row ? { ...row } : emptyForm()
  visible.value = true
}

async function submit() {
  if (!form.value.name) { ElMessage.warning('请填姓名'); return }
  if (!form.value.companyName) { ElMessage.warning('请填公司名称'); return }
  loading.value = true
  try {
    if (editing.value) await api.put(`/crm/contact/${editing.value.id}`, form.value)
    else await api.post('/crm/contact', form.value)
    ElMessage.success('保存成功')
    visible.value = false
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败') }
  finally { loading.value = false }
}

async function del(row) {
  try { await ElMessageBox.confirm(`确定删除联系人 ${row.name}？`) } catch { return }
  try {
    await api.delete(`/crm/contact/${row.id}`)
    ElMessage.success('已删除')
    fetch()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败') }
}

const { page, pageSize, pagedRows, resetPage } = usePaging(filtered)

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  fetch()
  try { customers.value = await api.get('/customer', { params: { enabled: true } }) } catch {}
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
</style>
