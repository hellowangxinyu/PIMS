<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">应收明细</h2>
      <div class="header-actions">
        <el-button size="small" @click="doExport" :loading="exporting">导出 Excel</el-button>
      </div>
    </div>
    <div class="table-card">
      <div class="tab-toolbar">
        <span class="tab-count">共 {{ arList.length }} 条</span>
      </div>
      <p-table :data="pagedRows" stripe border style="width:100%">
        <el-table-column prop="docNo" label="单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="180" show-overflow-tooltip />
        <el-table-column prop="salesOrderNo" label="销售订单" min-width="120" show-overflow-tooltip />
        <el-table-column prop="amount" label="应收金额" width="120" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="receivedAmount" label="已收金额" width="120" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">¥{{ fmt(row.receivedAmount) }}</template>
        </el-table-column>
        <el-table-column label="剩余" width="120" align="right" v-if="hasAmountPerm('finance-ar')">
          <template #default="{ row }">¥{{ fmt((row.amount||0) - (row.receivedAmount||0)) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="arStatusType(row.status)" size="small">{{ arStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="110" />
        <el-table-column prop="createTime" label="创建时间" width="160" />
      </p-table>
      <!-- 分页（v5.2） -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[25, 50, 100]"
          :total="arList.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { statusType as globalStatusType } from '../utils/statusTag'
import { fmt } from '../utils/fmt'
import { ref, onMounted } from 'vue'
import api from '../api'
import { usePaging } from '../composables/usePaging'
import { downloadFile } from '../utils/download'

const arList = ref([])
const perms = ref([])
const exporting = ref(false)

function hasPerm(c) { return perms.value.includes(c) }
function hasAmountPerm(m) { return perms.value.includes(m + ':amount') || perms.value.includes('finance:amount') }
// v6.4 金额格式统一（utils/fmt 千分位 2 位）
// v5.23：导出全部应收明细（金额列按后端 finance:amount 权限脱敏）
async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/finance/ar/export', {}, `应收明细-${new Date().toISOString().slice(0, 10)}.xlsx`)
  } catch (e) { /* downloadFile 内已提示 */ }
  finally { exporting.value = false }
}

// 状态中文映射（避免英文显示）
const arStatusType = (s) => globalStatusType(s, {UNPAID: 'danger', PARTIAL: 'warning', PAID: 'success'})   // v6.6 收口：全局 + 域局部
function arStatusLabel(s) { return { UNPAID: '未收款', PARTIAL: '部分收款', PAID: '已结清' }[s] || '未知' }

async function loadAR() {
  resetPage()
  try { arList.value = await api.get('/finance/ar') } catch {} }


// ===== 分页（v5.2）：超过 50 条自动分页展示 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(arList)

onMounted(async () => {
  try { perms.value = JSON.parse(localStorage.getItem('user')||'{}').permissions || [] } catch {}
  loadAR()
})
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.table-card { background: var(--pims-card-bg); border-radius: 12px; padding: 16px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 12px; }
.tab-count { font-size: 13px; color: #64748b; }
</style>
