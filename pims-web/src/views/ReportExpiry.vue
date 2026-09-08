<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">批次过期预警</h2>
      <div class="header-actions">
        <el-tooltip placement="top" :content="ruleTip">
          <span class="rule-tip">口径说明 ⓘ</span>
        </el-tooltip>
        <el-button size="small" @click="doExport" :loading="exporting">导出 Excel</el-button>
        <el-button size="small" @click="loadData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <div v-if="loading" class="report-loading">
      <el-skeleton :rows="8" animated />
    </div>

    <template v-else>
      <div class="stat-cards">
        <div class="stat-card red">
          <div class="stat-num">{{ expiredCount }}</div>
          <div class="stat-label">已过期批次（{{ fmtNum(expiredQty) }} kg，禁止正常出库）</div>
        </div>
        <div class="stat-card warn">
          <div class="stat-num">{{ expiringCount }}</div>
          <div class="stat-label">30 天内到期批次（{{ fmtNum(expiringQty) }} kg）</div>
        </div>
        <div class="stat-card total">
          <div class="stat-num">{{ totalBatchCount }}</div>
          <div class="stat-label">在库批次总数（有库存且有过期日期）</div>
        </div>
      </div>

      <div class="chart-card full">
        <div class="chart-card-title">预警明细（已过期在前，按紧急程度升序）</div>
        <p-table v-if="rows.length" :data="pagedRows" size="small" border style="width:100%">
          <el-table-column prop="materialCode" label="物料编码" min-width="110" />
          <el-table-column prop="materialName" label="品名" min-width="130" show-overflow-tooltip />
          <el-table-column prop="batchNo" label="批号" min-width="130" />
          <el-table-column label="库存量" width="100" align="right">
            <template #default="{ row }">{{ fmtNum(row.qty) }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="60" align="center" />
          <el-table-column prop="expiryDate" label="过期日期" width="110" align="center" />
          <el-table-column label="剩余天数" width="120" align="right">
            <template #default="{ row }">
              <span :class="isExpired(row) ? 'days-red' : 'days-warn'">
                {{ isExpired(row) ? `已过期 ${Math.abs(row.remainDays)} 天` : `剩余 ${row.remainDays} 天` }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="预警级别" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="isExpired(row) ? 'danger' : 'warning'" size="small">{{ isExpired(row) ? '已过期' : '即将到期' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="warehouses" label="仓库" min-width="100" show-overflow-tooltip />
          <el-table-column label="操作" width="110" align="center" v-if="hasPerm('qc:write')">
            <template #default="{ row }">
              <button v-if="isExpired(row)" class="op-btn op-btn-warn" @click="reinspect(row)">发起复检</button>
            </template>
          </el-table-column>
        </p-table>
        <el-empty v-else description="当前无过期或即将到期批次（30 天内）" :image-size="60" />
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[25, 50, 100]"
            :total="rows.length"
            layout="total, sizes, prev, pager, next"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { todayLocal } from '../utils/date'
import { fmtMoney } from '../utils/fmt'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { usePaging } from '../composables/usePaging'
import { downloadFile } from '../utils/download'

const loading = ref(false)
const exporting = ref(false)
const rows = ref([])
const expiredCount = ref(0)
const expiringCount = ref(0)
const expiredQty = ref(0)
const expiringQty = ref(0)
const totalBatchCount = ref(0)

const ruleTip = '统计在库（库存量 > 0）且有过期日期的批次。' +
  '剩余天数 = 过期日期 − 今天（当天到期不算过期）。' +
  '已过期（剩余 < 0 天，红色）与 30 天内到期（0 ≤ 剩余 ≤ 30，橙色）进入本表，按紧急程度升序。' +
  '同一批次跨仓库/库位时按总量聚合，过期日期取最早。' +
  '已过期批次每日自动隔离（qcStatus=EXPIRED，从可用库存口径排除、禁止正常出库）。' +
  '处理通道二选一：「发起复检」→ 质检员评估，合格即恢复可用并延期；无法复用则通过「其他出库-报废」处理。'

const perms = ref([])
function hasPerm(c) { return perms.value.includes(c) }

// v5.37：对已过期批次发起复检评估（生成复检质检单，到质检管理完成判定）
async function reinspect(row) {
  try {
    await ElMessageBox.confirm(
      `对过期批次发起复检？\n\n${row.materialName}（${row.materialCode}）\n批号：${row.batchNo}\n库存：${fmtNum(row.qty)}${row.unit || ''}\n过期日：${row.expiryDate}`,
      '发起复检', { type: 'warning', confirmButtonText: '发起', cancelButtonText: '取消' })
  } catch { return }
  try {
    const qc = await api.post('/qc/reinspection', { materialCode: row.materialCode, batchNo: row.batchNo })
    ElMessage.success(`复检单 ${qc.inspectionNo} 已创建，请到「质检管理」完成复检判定`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '发起失败')
  }
}

function fmtNum(v) {
  if (v === null || v === undefined) return '-'
  const n = Number(v)
  return Number.isInteger(n) ? n.toString() : n.toFixed(2).replace(/\.?0+$/, '')
}

// v6.6 金额格式收口（utils/fmt）

function isExpired(row) { return row.level === 'EXPIRED' }

async function loadData() {
  resetPage()
  loading.value = true
  try {
    const res = await api.get('/report/expiry')
    rows.value = res.rows || []
    expiredCount.value = res.expiredCount || 0
    expiringCount.value = res.expiringCount || 0
    expiredQty.value = res.expiredQty || 0
    expiringQty.value = res.expiringQty || 0
    totalBatchCount.value = res.totalBatchCount || 0
  } catch (e) { console.error(e) }
  loading.value = false
}

async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/report/expiry/export', {}, `批次过期预警-${todayLocal()}.xlsx`)
  } catch (e) { /* downloadFile 内已提示 */ }
  finally { exporting.value = false }
}

// ===== 分页：前端分页 =====
const { page, pageSize, pagedRows, resetPage } = usePaging(rows)

onMounted(() => {
  try { perms.value = JSON.parse(localStorage.getItem('user') || '{}').permissions || [] } catch {}
  loadData()
})
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.rule-tip { font-size: 12px; color: #64748b; cursor: help; border-bottom: 1px dashed #94a3b8; }
.report-loading { padding: 40px 20px; }
.stat-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card { border-radius: 16px; padding: 18px 20px; background: var(--pims-card-bg); box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.stat-num { font-size: 28px; font-weight: 800; line-height: 1.2; }
.stat-label { font-size: 12px; color: #64748b; margin-top: 4px; }
.stat-card.red .stat-num { color: #dc2626; }
.stat-card.warn .stat-num { color: #d97706; }
.stat-card.total .stat-num { color: #6366f1; }
.chart-card { background: var(--pims-card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.chart-card-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 16px; }
.days-red { color: #dc2626; font-weight: 700; }
.days-warn { color: #d97706; font-weight: 700; }
@media (max-width: 768px) {
  .stat-cards { grid-template-columns: 1fr; }
  .report-header { flex-direction: column; align-items: flex-start; }
}
</style>
