<template>
  <div class="page-container">
    <div class="page-header">
      <h2>操作日志</h2>
      <div class="header-actions">
        <el-radio-group v-model="view" size="small">
          <el-radio-button value="live">实时日志（近 30 天）</el-radio-button>
          <el-radio-button value="archive">归档日志</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="table-card">
      <!-- 筛选 -->
      <div class="filter-bar">
        <el-input v-model="query.username" placeholder="账户" clearable style="width: 130px" @keydown.enter="search" />
        <el-select v-model="query.module" placeholder="模块（如：会计凭证）" clearable filterable style="width: 160px">
          <el-option v-for="m in moduleList" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
        <el-select v-model="query.action" placeholder="动作（增删改查）" clearable filterable style="width: 130px">
          <el-option label="新增" value="新增" />
          <el-option label="修改" value="修改" />
          <el-option label="删除" value="删除" />
          <el-option label="审核" value="审核" />
          <el-option label="确认" value="确认" />
          <el-option label="记账" value="记账" />
          <el-option label="反记账" value="反记账" />
          <el-option label="登录" value="登录" />
          <el-option label="生成" value="生成" />
        </el-select>
        <el-input v-model="query.bizNo" placeholder="单号搜索" clearable style="width: 160px" @keydown.enter="search" />
        <el-input v-model="query.path" placeholder="接口路径关键字" clearable style="width: 170px" @keydown.enter="search" />
        <template v-if="view === 'archive'">
          <el-select v-model="archiveMonth" placeholder="选择月份" style="width: 140px">
            <el-option v-for="a in archiveList" :key="a.month" :label="a.month + '（' + a.count + ' 条）'" :value="a.month" />
          </el-select>
        </template>
        <template v-else>
          <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="开始日期" end-placeholder="结束日期" :clearable="true" />
        </template>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <!-- 表格 -->
      <p-table :data="rows" stripe border size="small" @row-click="showDetail" style="cursor: pointer">
        <el-table-column prop="create_time" label="时间" width="150" />
        <el-table-column label="账户" width="130">
          <template #default="{row}">{{ row.username }}<span v-if="row.real_name" class="real-name">（{{ row.real_name }}）</span></template>
        </el-table-column>
        <el-table-column label="单据" width="110" align="center">
          <template #default="{row}">
            <el-tag v-if="row.module" size="small" effect="plain">{{ row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动作" width="90" align="center">
          <template #default="{row}">
            <el-tag :type="actionTag(row.action)" size="small">{{ row.action || row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="biz_no" label="单号" width="150" show-overflow-tooltip />
        <el-table-column prop="detail" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column prop="path" label="接口路径" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="70" align="center">
          <template #default="{row}">
            <el-tag :type="statusTag(row.result_code)" size="small">{{ row.result_code }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration_ms" label="耗时(ms)" width="85" align="right" />
        <el-table-column prop="ip" label="IP" width="120" />
      </p-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :total="total" :page-sizes="[25, 50, 100]" layout="total, sizes, prev, pager, next"
          @current-change="fetch" @size-change="search" />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog title="日志详情" v-model="detailVisible" width="640px">
      <div v-if="detail" class="detail">
        <div class="detail-grid">
          <span>账户</span><b>{{ detail.username }}<span v-if="detail.real_name">（{{ detail.real_name }}）</span></b>
          <span>时间</span><b>{{ detail.create_time }}</b>
          <span>单据</span><b>{{ detail.module || '-' }}<span v-if="detail.biz_no">　单号：{{ detail.biz_no }}</span></b>
          <span>动作</span><b>{{ detail.action || detail.method }}（{{ detail.method }}）</b>
          <span>状态</span><b>{{ detail.result_code }}</b>
          <span>耗时</span><b>{{ detail.duration_ms }} ms</b>
          <span>IP</span><b>{{ detail.ip }}</b>
        </div>
        <div class="detail-block">
          <div class="detail-title">请求参数</div>
          <pre>{{ detail.params || '（无）' }}</pre>
        </div>
        <div class="detail-block">
          <div class="detail-title">系统返回</div>
          <pre>{{ detail.result_msg || '（空）' }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import api from '../api'

const view = ref('live')
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(25)
const query = ref({ username: '', module: '', action: '', bizNo: '', path: '', dateRange: null })
const moduleList = ref([])
const archiveList = ref([])
const archiveMonth = ref('')
const detailVisible = ref(false)
const detail = ref(null)

function actionTag(a) {
  if (!a) return 'info'
  if (a.includes('新增') || a.includes('登录')) return 'success'
  if (a.includes('删除')) return 'danger'
  if (a.includes('审核') || a.includes('确认')) return 'warning'
  return 'primary'
}
function statusTag(code) {
  if (code >= 200 && code < 300) return 'success'
  if (code >= 400 && code < 500) return 'warning'
  return 'danger'
}

async function fetch() {
  if (view.value === 'live') {
    const res = await api.get('/log', { params: {
      username: query.value.username || undefined,
      module: query.value.module || undefined,
      action: query.value.action || undefined,
      bizNo: query.value.bizNo || undefined,
      path: query.value.path || undefined,
      startDate: query.value.dateRange?.[0] || undefined,
      endDate: query.value.dateRange?.[1] || undefined,
      page: page.value, pageSize: pageSize.value
    } })
    rows.value = res.rows
    total.value = res.total
  } else {
    if (!archiveMonth.value) { rows.value = []; total.value = 0; return }
    const res = await api.get(`/log/archives/${archiveMonth.value}`, { params: {
      username: query.value.username || undefined,
      module: query.value.module || undefined,
      action: query.value.action || undefined,
      bizNo: query.value.bizNo || undefined,
      path: query.value.path || undefined,
      page: page.value, pageSize: pageSize.value
    } })
    rows.value = res.rows
    total.value = res.total
  }
}

async function loadModules() {
  try { moduleList.value = await api.get('/log/modules') } catch {}
}

async function loadArchives() {
  archiveList.value = await api.get('/log/archives')
  if (archiveList.value.length) {
    archiveMonth.value = archiveList.value[0].month
    fetch()
  }
}

function search() {
  page.value = 1
  fetch()
}

function reset() {
  query.value = { username: '', module: '', action: '', bizNo: '', path: '', dateRange: null }
  page.value = 1
  fetch()
}

function showDetail(row) {
  detail.value = row
  detailVisible.value = true
}

watch(view, () => {
  page.value = 1
  if (view.value === 'archive') {
    loadArchives()
  } else {
    fetch()
  }
})

watch(archiveMonth, () => {
  page.value = 1
  if (archiveMonth.value) fetch()
})

onMounted(() => {
  loadModules()
  fetch()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
  align-items: center;
}
.real-name {
  color: #94a3b8;
  font-size: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.detail-grid {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 8px 12px;
  margin-bottom: 14px;
}
.detail-grid span {
  color: #94a3b8;
}
.detail-block {
  margin-bottom: 12px;
}
.detail-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.detail-block pre {
  background: var(--pims-bg);
  border-radius: 8px;
  padding: 10px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 220px;
  overflow-y: auto;
  margin: 0;
}
</style>
