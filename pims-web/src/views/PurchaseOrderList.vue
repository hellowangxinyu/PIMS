<template>
  <div class="page-container">
    <div class="page-header">
      <h2>采购订单</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showDialog" v-if="hasPerm('purchase:write')">新增采购单</el-button>
      </div>
    </div>
    <div class="table-card">
      <p-table :data="list" stripe border @header-dragend="onHeaderDragend">
        <el-table-column prop="orderNo" label="单号" :width="cw('单号') || 140" />
        <el-table-column prop="supplierId" label="供应商ID" :width="cw('供应商ID') || 100" />
        <el-table-column prop="totalAmount" label="金额" :width="cw('金额') || 120" />
        <el-table-column prop="status" label="状态" :width="cw('状态') || 100">
          <template #default="{row}">
            <el-tag size="small" :type="poStatusType(row.status)">{{ poStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" :width="cw('创建时间') || 170" />
      </p-table>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'
import { useColumnResize } from '../composables/useColumnResize'

const list = ref([])
const perms = ref([])
const { cw, onHeaderDragend } = useColumnResize('purchase_order')

function hasPerm(c) { return perms.value.includes(c) }
// 采购订单状态映射为中文
const PO_STATUS_MAP = { DRAFT: '草稿', APPROVED: '已审核', RECEIVED: '已到货', CLOSED: '已关闭', CANCELLED: '已取消' }
function poStatusLabel(s) { return s ? (PO_STATUS_MAP[s] || '未知') : '-' }
function poStatusType(s) {
  if (!s) return 'info'
  if (s === 'DRAFT') return 'info'
  if (s === 'APPROVED') return 'warning'
  if (s === 'RECEIVED' || s === 'CLOSED') return 'success'
  if (s === 'CANCELLED') return 'danger'
  return 'info'
}
async function showDialog() { alert('采购单创建功能待完善') }
onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  try { list.value = await api.get('/purchase-order') } catch {}
})
</script>

<style scoped>
.page-container {
  width: 100%;
}
</style>
