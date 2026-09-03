<template>
  <div class="report-page">
    <div class="report-header">
      <h2 class="report-title">生产进度表</h2>
    </div>

    <div v-if="loading" class="report-loading">
      <el-skeleton :rows="8" animated />
    </div>

    <template v-else>
      <div class="chart-card">
        <div class="chart-card-title">生产进度（生产 + 委外订单，进度 = 已确认入库量 ÷ 计划批量）</div>
        <el-tabs v-model="progressTab" class="progress-tabs">
          <el-tab-pane :label="`进行中（${unfinishedRows.length}）`" name="unfinished">
            <div class="progress-summary">未完成订单 {{ unfinishedRows.length }} 个（含委外）· 平均进度 {{ unfinishedAvg }}%</div>
            <p-table v-if="unfinishedRows.length" :data="unfinishedRows" size="small" border style="width:100%">
              <el-table-column label="类型" width="70" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.type === '委外' ? 'warning' : 'primary'" size="small" effect="plain">{{ row.type }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="orderNo" label="订单号" width="130" />
              <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip />
              <el-table-column prop="batchQty" label="计划量" width="90" align="right" />
              <el-table-column prop="inQty" label="已入库" width="90" align="right" />
              <el-table-column label="生产进度" min-width="240">
                <template #default="{ row }">
                  <el-progress :percentage="Number(row.progress)" :status="progressStatus(row)"
                    :stroke-width="14" :format="p => p + '%'" />
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="Number(row.progress) > 0 ? 'primary' : 'info'" size="small">
                    {{ Number(row.progress) > 0 ? '生产中' : '待生产' }}
                  </el-tag>
                </template>
              </el-table-column>
            </p-table>
            <el-empty v-else description="暂无进行中的订单" :image-size="50" />
          </el-tab-pane>
          <el-tab-pane :label="`已完成（${completedRows.length}）`" name="completed">
            <div class="progress-summary">已完成订单 {{ completedRows.length }} 个（含超产入库）</div>
            <p-table v-if="completedRows.length" :data="completedRows" size="small" border style="width:100%">
              <el-table-column label="类型" width="70" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.type === '委外' ? 'warning' : 'primary'" size="small" effect="plain">{{ row.type }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="orderNo" label="订单号" width="130" />
              <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip />
              <el-table-column prop="batchQty" label="计划量" width="90" align="right" />
              <el-table-column prop="inQty" label="已入库" width="90" align="right" />
              <el-table-column label="生产进度" min-width="240">
                <template #default="{ row }">
                  <el-progress :percentage="Number(row.progress)" :status="'success'"
                    :stroke-width="14" :format="p => p + '%'" />
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag type="success" size="small">已完成</el-tag>
                </template>
              </el-table-column>
            </p-table>
            <el-empty v-else description="暂无已完成订单" :image-size="50" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import api from '../api'

const loading = ref(false)
const progress = reactive({ rows: [], total: 0, completed: 0, avgProgress: 0 })
const progressTab = ref('unfinished')

// 进行中/已完成分 tab 展示：进度 100% 视为已完成
const unfinishedRows = computed(() => (progress.rows || []).filter(r => Number(r.progress) < 100))
const completedRows = computed(() => (progress.rows || []).filter(r => Number(r.progress) >= 100))
const unfinishedAvg = computed(() => {
  const rs = unfinishedRows.value
  if (!rs.length) return 0
  return Math.round(rs.reduce((s, r) => s + Number(r.progress), 0) / rs.length)
})

// 进度条颜色：已完成/≥80% 绿，30-79% 黄，<30% 红
function progressStatus(row) {
  const p = Number(row.progress)
  if (row.status === 'COMPLETED' || p >= 80) return 'success'
  if (p >= 30) return 'warning'
  return 'exception'
}

async function loadData() {
  loading.value = true
  try {
    Object.assign(progress, await api.get('/report/production-progress'))
  } catch (e) { console.error(e) }
  loading.value = false
}

onMounted(loadData)
</script>

<style scoped>
.report-page { width: 100%; }
.report-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
.report-title { font-size: 20px; font-weight: 800; color: var(--pims-text); margin: 0; }
.report-loading { padding: 40px 20px; }
.chart-card { background: var(--pims-card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--pims-card-shadow); border: var(--pims-card-border); }
.chart-card-title { font-size: 14px; font-weight: 700; color: var(--pims-text); margin-bottom: 16px; }
.progress-tabs { margin-top: -8px; }
.progress-summary { font-size: 13px; color: var(--pims-text-secondary); margin-bottom: 10px; }
</style>
