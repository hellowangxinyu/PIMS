<template>
  <div class="page-container">
    <div class="page-header">
      <h2>配方管理</h2>
      <div class="header-actions">
        <el-button @click="doExport" :loading="exporting">导出 Excel</el-button>
        <el-button @click="downloadTpl">下载导入模板</el-button>
        <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" :on-change="onImportFile" style="display:inline-block;margin-right:12px">
          <el-button :loading="importing">导入Excel</el-button>
        </el-upload>
        <el-button type="primary" @click="openCreateRecipe">新建配方</el-button>
      </div>
    </div>

    <div class="recipe-layout">
      <!-- 左侧：配方列表 -->
      <div class="recipe-left">
        <div class="type-tabs">
          <button class="type-tab" :class="{ active: activeType === 'GRINDING' }" @click="switchType('GRINDING')">制浆配方</button>
          <button class="type-tab" :class="{ active: activeType === 'TINTING' }" @click="switchType('TINTING')">制漆配方</button>
        </div>
        <div class="status-tabs">
          <button class="status-tab" :class="{ active: enabledFilter === 'ENABLED' }" @click="enabledFilter = 'ENABLED'">启用中</button>
          <button class="status-tab" :class="{ active: enabledFilter === 'DISABLED' }" @click="enabledFilter = 'DISABLED'">已禁用</button>
        </div>
        <div class="search-bar">
          <el-input v-model="keyword" placeholder="搜索编号/品名" clearable size="small" style="width:180px" @clear="fetchList" @keyup.enter="fetchList" />
          <el-button size="small" type="primary" @click="fetchList">搜索</el-button>
        </div>
        <p-table :data="filteredRecipes" stripe border size="small" highlight-current-row @current-change="onSelectRecipe" style="width:100%">
          <el-table-column prop="recipeNo" label="编号" width="100" />
          <el-table-column label="产品编码" width="110" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="row.productCode ? '' : 'cost-empty'">{{ row.productCode || '未绑定' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="品名" min-width="140" show-overflow-tooltip />
          <el-table-column prop="printCount" label="打印次数" width="80" align="center" />
          <el-table-column prop="usageCount" label="引用次数" width="80" align="center">
            <template #default="{ row }">
              <span :class="row.usageCount > 0 ? 'usage-count' : ''">{{ row.usageCount }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column label="配方成本" width="110" align="right">
            <template #default="{ row }">
              <span v-if="recipeCostMap[row.id]" class="cost-cell" :title="`已发布版本 ${recipeCostMap[row.id].versionNo} 单位成本`">￥{{ fmtCost(recipeCostMap[row.id].unitCost) }}/{{ recipeCostMap[row.id].unit || 'kg' }}</span>
              <span v-else class="cost-empty">-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </p-table>
      </div>

      <!-- 右侧：详情面板 -->
      <div class="recipe-right" v-if="current">
        <div class="detail-card">
          <div class="detail-header">
            <h3>{{ current.productName }} <span class="recipe-no">{{ current.recipeNo }}</span></h3>
            <div class="detail-actions">
              <button class="op-btn op-btn-print" @click="printRecipe" :disabled="printing">{{ printing ? '打印中...' : '打印' }}</button>
              <button class="op-btn op-btn-primary" @click="openEditRecipe">编辑</button>
              <button class="op-btn" :class="current.enabled ? 'op-btn-warning' : 'op-btn-success'" @click="toggleEnabled">{{ current.enabled ? '禁用' : '启用' }}</button>
              <button class="op-btn op-btn-danger" @click="deleteRecipe">删除</button>
            </div>
          </div>
          <div class="detail-meta">
            <span>产品编码：{{ current.productCode || '-' }}</span>
            <span>分类：{{ current.category || '-' }}</span>
            <span>描述：{{ current.description || '-' }}</span>
          </div>
        </div>

        <!-- 版本管理 -->
        <div class="version-section">
          <div class="section-header">
            <h4>配方版本</h4>
            <div style="display:flex;gap:8px">
              <el-button size="small" @click="openChanges">变更记录</el-button>
              <el-button size="small" type="primary" @click="createVersion">+ 新建版本</el-button>
            </div>
          </div>
          <p-table :data="versions" border size="small" highlight-current-row @current-change="onSelectVersion" style="width:100%">
            <el-table-column prop="versionNo" label="版本" width="80" />
            <el-table-column prop="batchQty" label="标准批量" width="100" align="right" />
            <el-table-column prop="unit" label="单位" width="60" align="center" />
            <el-table-column label="成本" width="100" align="right">
              <template #default="{ row }">
                <span v-if="versionCostMap[row.id]" class="cost-cell">￥{{ fmtCost(versionCostMap[row.id].unitCost) }}/{{ row.unit || 'kg' }}</span>
                <span v-else class="cost-empty">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="vStatusType(row.status)" size="small">{{ vStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="releasedBy" label="发布人" width="80" />
            <el-table-column label="发布时间" width="140">
              <template #default="{ row }">{{ fmtTime(row.releasedTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="250" align="center">
              <template #default="{ row }">
                <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-success" @click.stop="releaseVersion(row)">发布</button>
                <button v-if="row.status === 'DRAFT'" class="op-btn op-btn-danger" @click.stop="deleteVersion(row)">删除</button>
              </template>
            </el-table-column>
          </p-table>
        </div>

        <!-- 配方树 -->
        <div class="tree-section" v-if="currentVersion">
          <div class="section-header">
            <h4>配方树 <span class="ver-label">{{ currentVersion.versionNo }}</span></h4>
            <div v-if="currentVersion.status === 'DRAFT'" class="tree-actions">
              <el-button v-if="current?.recipeType !== 'GRINDING'" size="small" type="primary" @click="addTreeNode(null)">+ 添加节点</el-button>
              <el-button size="small" type="primary" @click="saveTree" :disabled="treeSaving">{{ treeSaving ? '保存中...' : '保存树' }}</el-button>
            </div>
            <el-tag v-else :type="vStatusType(currentVersion.status)" size="small">{{ vStatusLabel(currentVersion.status) }}（只读）</el-tag>
          </div>
          <div class="tree-meta" v-if="currentVersion.status === 'DRAFT'">
            <span>标准批量：</span>
            <el-input-number v-model="currentVersion.batchQty" :min="0.001" :precision="3" :step="100" size="small" style="width:140px" @change="updateVersionMeta" />
            <span style="margin-left:12px">单位：kg（公斤）</span>
          </div>
          <div class="cost-bar">
            <span class="cost-bar-label">总成本（{{ currentVersion.versionNo }}，含包装）：</span>
            <span class="cost-bar-total">￥{{ fmtCost(treeTotalCost + packCostForBatch) }}</span>
            <span class="cost-bar-unit">≈ ￥{{ fmtCost((treeTotalCost + packCostForBatch) / (Number(currentVersion.batchQty) || 1)) }}/{{ currentVersion.unit || 'kg' }}</span>
            <span class="cost-bar-hint" v-if="packCostForBatch > 0">= 材料 ￥{{ fmtCost(treeTotalCost) }} + 包装 ￥{{ fmtCost(packCostForBatch) }}；材料按库存加权均价/最近采购价，半成品按子配方递归</span>
            <span class="cost-bar-hint" v-else>材料按库存加权均价，无库存取最近采购价；半成品按子配方递归</span>
          </div>
          <p-table
            :data="treeData"
            row-key="uid"
            :tree-props="{ children: 'children' }"
            default-expand-all
            :row-class-name="treeRowClass"
            border
            size="small"
            class="recipe-tree-table"
          >
            <el-table-column label="序号" width="56" align="center">
              <template #default="{ row }">
                <span v-if="treeSeqMap[row.uid]" class="row-seq">{{ treeSeqMap[row.uid] }}</span>
              </template>
            </el-table-column>
            <el-table-column label="品名" min-width="180">
              <template #default="{ row }">
                <el-tag :type="row.nodeType === 'SUB_RECIPE' ? 'warning' : row.nodeType === 'OIL_TAIL' ? 'danger' : ''" size="small" class="row-type-tag">
                  {{ row.nodeType === 'SUB_RECIPE' ? '半成品' : row.nodeType === 'OIL_TAIL' ? '油尾' : '原料' }}
                </el-tag>
                <span :class="row.nodeType === 'SUB_RECIPE' ? 'row-name-sub' : 'row-name'">{{ row.materialName || row.materialCode }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="materialCode" label="编码" width="105" />
            <el-table-column label="大类" width="75" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.category" size="small" type="info">{{ categoryLabel(row.category) }}</el-tag>
                <span v-else class="row-dash">-</span>
              </template>
            </el-table-column>
            <el-table-column label="小类" min-width="95">
              <template #default="{ row }">
                <span v-if="row.subCategory">{{ subLabel(row.subCategory) }}</span>
                <span v-else class="row-dash">-</span>
              </template>
            </el-table-column>
            <el-table-column label="用量" width="120" align="right">
              <template #default="{ row }">
                <!-- :key 带 treeReloadSeq：保存树后强制重建输入框，避免 el-input-number 内部状态残留显示旧值 -->
                <el-input-number v-if="currentVersion.status === 'DRAFT'" :key="'tq-' + treeReloadSeq + '-' + row.uid" v-model="row.qty" :min="0.001" :precision="3" :step="1" size="small" controls-position="right" style="width:115px" />
                <span v-else>{{ row.qty }} {{ row.unit }}</span>
              </template>
            </el-table-column>
            <el-table-column label="单价" width="100" align="right">
              <template #default="{ row }">
                <span v-if="row.unitPrice > 0" class="row-price">￥{{ fmtCost(row.unitPrice) }}<template v-if="row.nodeType === 'SUB_RECIPE'">/{{ row.unit || 'kg' }}</template></span>
                <span v-else class="row-dash">-</span>
              </template>
            </el-table-column>
            <el-table-column label="成本" width="100" align="right">
              <template #default="{ row }">
                <span v-if="row.cost > 0" class="row-cost">￥{{ fmtCost(row.cost) }}</span>
                <span v-else class="row-dash">-</span>
              </template>
            </el-table-column>
            <el-table-column v-if="currentVersion.status === 'DRAFT'" label="操作" width="180" align="center">
              <template #default="{ row }">
                <button v-if="row.nodeType === 'SUB_RECIPE'" class="op-btn op-btn-primary" @click.stop="addTreeNode(row)">+子</button>
                <button v-if="row.nodeType === 'MATERIAL' && altOf(row.materialCode).length" class="op-btn op-btn-alt" @click.stop="openAltDialog(row)">⇄平替</button>
                <button class="op-btn op-btn-danger" @click.stop="removeTreeNode(row)">✕</button>
              </template>
            </el-table-column>
          </p-table>
          <!-- 制浆配方：表格底部行内快速添加原料（模糊搜索，仅原材料） -->
          <div v-if="current?.recipeType === 'GRINDING' && currentVersion.status === 'DRAFT'" class="tree-add-row">
            <el-select v-model="addRow.materialCode" filterable placeholder="搜索原材料（编码/品名）" size="small" class="add-mat" @change="onAddRowMatChange">
              <el-option v-for="m in rawMaterials" :key="m.code" :label="m.code + ' ' + (m.name||'')" :value="m.code" />
            </el-select>
            <el-input-number v-model="addRow.qty" :min="0.001" :precision="3" :step="1" size="small" class="add-qty" @change="recalcAddRowCost" />
            <span class="add-price">
              <span v-if="addRow.unitPrice > 0" class="row-price">￥{{ fmtCost(addRow.unitPrice) }}</span>
              <span v-else class="row-dash">-</span>
            </span>
            <span class="add-cost">
              <span v-if="addRow.cost > 0" class="row-cost">￥{{ fmtCost(addRow.cost) }}</span>
              <span v-else class="row-dash">-</span>
            </span>
            <el-button size="small" type="primary" class="add-btn" @click="confirmAddRow">+ 添加</el-button>
          </div>
          <!-- 用量合计（实时） -->
          <div class="tree-total" v-if="treeData.length">
            <span>用量合计：<strong>{{ fmtCost(treeQtyTotal) }}</strong> {{ currentVersion.unit || 'kg' }}</span>
            <span class="tree-total-sep">/</span>
            <span>标准批量：{{ currentVersion.batchQty }} {{ currentVersion.unit || 'kg' }}</span>
            <span v-if="Math.abs(treeQtyTotal - Number(currentVersion.batchQty)) > 0.001" class="tree-total-warn">⚠ 合计不等于标准批量，保存时将被拦截</span>
            <span class="tree-total-sep">/</span>
          </div>
          <el-empty v-if="!treeData.length" description="暂无配方节点" :image-size="60" />
        </div>

        <!-- v5.82.1 绑定信息展示：质检模板 + 包装标准（树区可见） -->
        <div class="bind-info-card" v-if="current">
          <span><b>质检模板：</b>{{ printQcTemplateName() }}</span>
          <span style="margin-left:24px"><b>包装标准：</b>{{ printPackagingText() }}</span>
        </div>

        <!-- 标准工艺流程（按配方类型，{{N}} 自动替换为配方物料） -->
        <div class="process-section" v-if="processTpl && processTpl.stages && processTpl.stages.length">
          <div class="section-header">
            <h4>标准工艺 <span class="ver-label">{{ processTpl.name }}</span></h4>
          </div>
          <div class="process-stages">
            <div class="proc-stage" v-for="(stg, si) in processTpl.stages" :key="si">
              <div class="proc-stage-head">
                <span class="proc-no">{{ stg.stageNo }}</span>
                <span class="proc-name">{{ stg.stageName }}</span>
                <span v-if="stg.roleHint" class="proc-role">{{ stg.roleHint }}</span>
              </div>
              <div class="proc-step" v-for="(stp, ti) in stg.steps" :key="ti">
                <span class="proc-step-code">{{ stp.stepCode }}</span>
                <span class="proc-step-desc">{{ renderStepDesc(stp.description) }}</span>
                <span v-if="stp.params" class="proc-step-params">{{ stp.params }}</span>
              </div>
              <div v-if="stg.qcItems && stg.qcItems.length" class="proc-qc">
                <span class="proc-qc-title">质检：</span>
                <el-tag v-for="(qc, qi) in stg.qcItems" :key="qi" size="small" type="warning" class="proc-qc-tag">
                  {{ qc.name }} {{ qc.standard }}<template v-if="qc.testTimes > 1"> ×{{ qc.testTimes }}次</template>
                </el-tag>
              </div>
            </div>
          </div>
        </div>

        <!-- v5.83 质检模板区（绑定的模板检测项完整展示） -->
        <div class="process-section" v-if="boundQcTemplate">
          <div class="section-header">
            <h4>质检模板 <span class="ver-label">{{ boundQcTemplate.name }}</span></h4>
            <span class="dim-desc">{{ qcTplDimText }}</span>
          </div>
          <p-table :data="boundQcTemplate.items || []" border size="small" style="width:100%">
            <el-table-column type="index" label="#" width="45" align="center" />
            <el-table-column prop="name" label="检测项目" min-width="140" />
            <el-table-column prop="standard" label="标准要求" min-width="140" />
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column prop="method" label="检验方法/依据" min-width="160" show-overflow-tooltip />
          </p-table>
        </div>
        <div class="process-section" v-else>
          <div class="section-header">
            <h4>质检模板</h4>
            <span class="dim-desc">未绑定——质检单创建时按 小类/主材/色系 自动匹配</span>
          </div>
        </div>

        <!-- v5.83 包装标准区（组合明细展示；成品与半成品均可绑定） -->
        <div class="process-section">
          <div class="section-header">
            <h4>包装标准 <span class="ver-label" v-if="boundPackaging">{{ boundPackaging.name }}</span></h4>
            <span class="dim-desc" v-if="boundPackaging">{{ boundPackaging.capacityKg ? boundPackaging.capacityKg + 'kg/套' : '整件' }}　套单价￥{{ fmt(boundPackaging.setPrice ?? boundPackaging.unitPrice ?? 0) }}</span>
          </div>
          <p-table v-if="boundPackaging && boundPackaging.items && boundPackaging.items.length" :data="boundPackaging.items" border size="small" style="width:100%">
            <el-table-column type="index" label="#" width="45" align="center" />
            <el-table-column prop="name" label="包装物料" min-width="150" />
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">{{ ({ IRON_DRUM: '铁桶', PLASTIC_DRUM: '塑料桶', IBC: '吨桶', BAG: '编织袋', PALLET: '托盘', OTHER: '其他' })[row.packType] || row.packType || '-' }}</template>
            </el-table-column>
            <el-table-column prop="spec" label="规格" min-width="100" />
            <el-table-column label="每套数量" width="90" align="right">
              <template #default="{ row }">{{ Number(row.qty) }}</template>
            </el-table-column>
            <el-table-column label="单价(元)" width="90" align="right">
              <template #default="{ row }">{{ fmt(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="小计" width="90" align="right">
              <template #default="{ row }">￥{{ (Number(row.qty) * Number(row.unitPrice)).toFixed(2) }}</template>
            </el-table-column>
          </p-table>
          <div v-else class="dim-desc" style="padding:8px 0">未绑定包装标准</div>
        </div>
      </div>
      <div class="recipe-right recipe-empty" v-else>
        <el-empty description="请选择左侧配方查看详情" :image-size="80" />
      </div>
    </div>

    <!-- 平替物料切换弹窗（v5.1：配方树中一键切换为平替物料） -->
    <el-dialog title="切换平替物料" v-model="altDialogVisible" width="min(800px, 95vw)" destroy-on-close>
      <p class="alt-tip">「{{ altNode?.materialName || altNode?.materialCode }}」可被以下物料完美替代，点击「切换」即替换当前节点（用量保持不变，可按需调整）</p>
      <p-table :data="altList" border size="small" style="width:100%">
        <el-table-column prop="code" label="编码" min-width="105" />
        <el-table-column prop="name" label="品名" min-width="120" show-overflow-tooltip />
        <el-table-column label="牌号" min-width="90">
          <template #default="{ row }">{{ row.brand || '-' }}</template>
        </el-table-column>
        <el-table-column label="大类" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="小类" min-width="90">
          <template #default="{ row }">{{ subLabel(row.subCategory) }}</template>
        </el-table-column>
        <el-table-column label="质保期(天)" width="100" align="center">
          <template #default="{ row }">{{ row.shelfLifeDays || '不限' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <button class="op-btn op-btn-primary" @click="switchAlternative(row)">切换</button>
          </template>
        </el-table-column>
      </p-table>
      <template #footer>
        <el-button @click="altDialogVisible = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑配方弹窗 -->
    <el-dialog :title="editRecipeId ? '编辑配方' : '新建配方'" v-model="recipeDialogVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="recipeForm" label-width="80px">
        <el-form-item label="配方类型" required>
          <el-radio-group v-model="recipeForm.recipeType" @change="onRecipeTypeChange">
            <el-radio value="GRINDING">研磨（制浆）</el-radio>
            <el-radio value="TINTING">调色（制漆）</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- v7.7 从打样配方导入：选后自动带出成品物料/分类并预填配方树（折算标准批量 100，保存后可微调） -->
        <el-form-item label="打样配方" v-if="recipeForm.recipeType === 'TINTING' && !editRecipeId">
          <el-select v-model="sampleFormulaId" clearable filterable placeholder="（可选）选打样配方自动带入物料与配方树" style="width:100%" @change="onSampleFormulaChange">
            <el-option v-for="f in sampleFormulas" :key="f.id"
              :label="f.formulaNo + ' ' + (f.materialName || '') + (f.convertedRecipeId ? '（已转）' : '')" :value="f.id"
              :disabled="!!f.convertedRecipeId" />
          </el-select>
          <div class="form-tip" v-if="sampleFormulaId">保存时自动按标准批量 100 折算配方树（打样用量 × 100÷打样总量），保存后可微调；色浆需有已发布制浆配方</div>
        </el-form-item>
        <el-form-item label="工艺路线" required>
          <el-select v-model="recipeForm.processTemplateId" placeholder="选择工艺路线" style="width:100%">
            <el-option v-for="r in processRoutes" :key="r.id" :label="r.name + (r.isDefault ? ' ★' : '')" :value="r.id" />
          </el-select>
        </el-form-item>

        <!-- v5.81 质检模板：按产品 小类/主材/色系 过滤（模板维度空=不限也算匹配） -->
        <el-form-item label="质检模板" required>
          <el-select v-model="recipeForm.qcTemplateId" clearable placeholder="不选=质检单创建时自动按物料匹配" style="width:100%">
            <el-option v-for="t in matchedQcTemplates" :key="t.id"
              :label="t.name + tplDimSuffix(t)" :value="t.id" />
          </el-select>
          <div class="form-tip" v-if="recipeForm.productCode">仅显示与本产品 小类/主材/色系 一致的模板（留空的维度=不限）</div>
        </el-form-item>
        <!-- v5.81 包装标准：计入理论成本 ⌈批量÷容量⌉×单价；v5.83.1 半成品也可选（浆桶/袋装） -->
        <el-form-item label="包装标准">
          <el-select v-model="recipeForm.packagingStandardId" clearable placeholder="选择桶/袋（计入配方理论成本）" style="width:100%">
            <el-option v-for="ps in packagingStandards" :key="ps.id"
              :label="ps.name + '（' + (ps.capacityKg ? ps.capacityKg + 'kg/套 ' : '') + '套￥' + fmt(ps.setPrice ?? ps.unitPrice ?? 0) + '）'" :value="ps.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="recipeForm.recipeType === 'GRINDING' ? '半成品' : '成品'" required>
          <!-- v7.7.7 选了打样配方：成品自动来自打样配方（锁定只读，不再手工选） -->
          <div v-if="sampleFormulaId && recipeForm.productCode" class="sf-product-lock">
            <b>{{ recipeForm.productCode }}</b> {{ recipeForm.productName }}
            <span class="sf-product-from">（自动来自打样配方，编码已由打样生成）</span>
          </div>
          <el-select v-else v-model="recipeForm.productCode" filterable clearable placeholder="选择物料" style="width:100%" @change="onProductChange">
            <el-option v-for="m in productMaterials" :key="m.code" :label="m.code + ' ' + m.name" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="recipeForm.category" placeholder="如：涂料、色浆" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="recipeForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recipeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRecipe" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加树节点弹窗 -->
    <el-dialog title="添加配方节点" v-model="nodeDialogVisible" width="min(1100px, 96vw)" destroy-on-close>
      <el-form :model="nodeForm" label-width="80px">
        <el-form-item label="节点类型">
          <el-radio-group v-model="nodeForm.nodeType">
            <el-radio value="MATERIAL">原料</el-radio>
            <el-radio value="SUB_RECIPE">半成品（子配方）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="nodeForm.nodeType === 'MATERIAL'" label="选择物料">
          <el-select v-model="nodeForm.materialCode" filterable placeholder="搜索物料" style="width:100%" @change="onNodeMatChange">
            <el-option v-for="m in materials" :key="m.code" :label="m.code + ' ' + (m.name||'')" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="nodeForm.nodeType === 'MATERIAL' && nodeForm.category" label="物料分类">
          <el-tag size="small" type="info">{{ nodeForm.category }}</el-tag>
          <el-tag v-if="nodeForm.subCategory" size="small" style="margin-left:6px">{{ nodeForm.subCategory }}</el-tag>
        </el-form-item>
        <el-form-item v-if="nodeForm.nodeType === 'SUB_RECIPE'" label="选择配方">
          <el-select v-model="nodeForm.refRecipeId" filterable placeholder="搜索配方" style="width:100%" @change="onNodeRecipeChange">
            <el-option v-for="r in recipeList.filter(r => r.id !== current?.id)" :key="r.id" :label="r.recipeNo + ' ' + r.productName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="nodeForm.nodeType === 'SUB_RECIPE'" label="半成品物料" required>
          <el-select v-model="nodeForm.materialCode" filterable placeholder="选择半成品物料（B类，领料出库用）" style="width:100%" @change="onNodeSemiMatChange">
            <el-option v-for="m in semiMaterials" :key="m.code" :label="m.code + ' ' + (m.name||'')" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="用量" required>
          <el-input-number v-model="nodeForm.qty" :min="0.001" :precision="3" :step="1" style="width:160px" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input value="kg（公斤）" disabled style="width:120px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddNode">确定</el-button>
      </template>
    </el-dialog>
  
    <!-- v6.3 配方变更日志 -->
    <el-dialog title="配方变更记录" v-model="changesVisible" width="860px">
      <p-table :data="changes" border size="small" style="width:100%" max-height="480">
        <el-table-column prop="createTime" label="时间" width="160">
          <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column prop="versionNo" label="版本" width="80" align="center" />
        <el-table-column prop="action" label="动作" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="{ CREATE: 'info', UPDATE: 'warning', RELEASE: 'success', ARCHIVE: 'danger', TREE_SAVE: 'primary' }[row.action] || 'info'">
              {{ { CREATE: '新建', UPDATE: '修改', RELEASE: '发布', ARCHIVE: '归档', TREE_SAVE: '配方树' }[row.action] || row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="detail" label="内容" min-width="320" show-overflow-tooltip />
      </p-table>
    </el-dialog>
</div>
</template>

<script setup>
import { fmt } from '../utils/fmt'
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useExcelImport } from '../composables/useExcelImport'
// v9.6 导出当前筛选（下载工具绕过 JSON 拦截器）
import { downloadFile } from '../utils/download'
const exporting = ref(false)
async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/recipe/export', { keyword: keyword.value || undefined }, `配方-${new Date().toLocaleDateString('sv')}.xlsx`)
  } finally { exporting.value = false }
}


const { importing, downloadTpl, onFile: onImportFile } = useExcelImport('/recipe', '配方导入模板.xlsx', '配方', fetchList)

const recipeList = ref([])
const activeType = ref('GRINDING')
const keyword = ref('')
const current = ref(null)
const recipeCostMap = ref({})
const versionCostMap = ref({})
// 启用状态筛选：ENABLED=启用中 / DISABLED=已禁用
const enabledFilter = ref('ENABLED')

const filteredRecipes = computed(() => recipeList.value.filter(r => {
  if ((r.recipeType || 'TINTING') !== activeType.value) return false
  if (enabledFilter.value === 'ENABLED' && r.enabled === false) return false
  if (enabledFilter.value === 'DISABLED' && r.enabled !== false) return false
  return true
}))

// 配方树总成本与单位成本（材料成本、半成品成本由后端直出）
const treeTotalCost = computed(() => treeData.value.reduce((sum, n) => sum + (Number(n.cost) || 0), 0))
// v5.85 配方树合计含包装：⌈批量÷套容量⌉×套单价（与后端 costs 口径一致）
const packCostForBatch = computed(() => {
  const ps = boundPackaging.value
  if (!ps) return 0
  const qty = Number(currentVersion.value?.batchQty) || 0
  const cap = Number(ps.capacityKg) || 0
  const pieces = cap > 0 && qty > 0 ? Math.ceil(qty / cap) : 1
  return pieces * Number(ps.setPrice ?? ps.unitPrice ?? 0)
})
const treeUnitCost = computed(() => {
  const batchQty = Number(currentVersion.value?.batchQty) || 0
  return batchQty > 0 ? treeTotalCost.value / batchQty : 0
})

function switchType(type) {
  activeType.value = type
  if (current.value && (current.value.recipeType || 'TINTING') !== type) {
    current.value = null
    versions.value = []
    currentVersion.value = null
    treeData.value = []
  }
}

// 切换启用/禁用筛选时清空当前选中
watch(enabledFilter, () => {
  current.value = null
  versions.value = []
  currentVersion.value = null
  treeData.value = []
})
const versions = ref([])
const currentVersion = ref(null)
const treeData = ref([])
const materials = ref([])
const dicts = ref({})
const saving = ref(false)
const treeSaving = ref(false)
const printing = ref(false)
// 物料价格映射（库存加权均价→采购价，与配方树成本同源），用于选物料时实时显示单价
const priceMap = ref({})
async function fetchPriceMap() {
  try { priceMap.value = await api.get('/recipe/material-prices') } catch {}
}
// 原材料（A助剂/P颜料/F填料/R树脂/S溶剂），制浆配方只允许选原材料
const rawMaterials = computed(() => (materials.value || []).filter(m => ['A','P','F','R','S'].includes(m.category)))
// 制浆配方表格行内新增条
const addRow = ref({ materialCode: '', materialName: '', qty: 1, unit: 'kg', category: '', subCategory: '', unitPrice: 0, cost: 0 })
// 用量合计（顶层节点用量之和，应等于标准批量）
const treeQtyTotal = computed(() => treeData.value.reduce((s, n) => s + (Number(n.qty) || 0), 0))
// 顶层节点投料序号映射（uid→序号），与下方工艺 {{N}} 占位符一一对应
const treeSeqMap = computed(() => {
  const m = {}
  treeData.value.forEach((n, i) => { m[n.uid] = i + 1 })
  return m
})

// 配方弹窗
const recipeDialogVisible = ref(false)
const editRecipeId = ref(null)
const recipeForm = ref({ productName: '', productCode: '', recipeType: 'TINTING', category: '', description: '', processTemplateId: null, qcTemplateId: null, packagingStandardId: null })

// 节点弹窗
const nodeDialogVisible = ref(false)
const nodeForm = ref({ nodeType: 'MATERIAL', materialCode: '', materialName: '', refRecipeId: null, qty: 1, unit: 'kg' })
let nodeParent = null
let uidSeq = 0

function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }
function fmtCost(v) {
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}
function vStatusType(s) { return s === 'RELEASED' ? 'success' : s === 'ARCHIVED' ? 'info' : 'warning' }
function vStatusLabel(s) { return s === 'RELEASED' ? '已发布' : s === 'ARCHIVED' ? '已归档' : '草稿' }

// ==================== 配方列表 ====================
async function fetchList() {
  try {
    recipeList.value = await api.get('/recipe', { params: { keyword: keyword.value || undefined } })
  } catch {}
  fetchReleasedCosts()
}

// 拉取已发布配方成本（成品成本/半成品成本直出于列表）
async function fetchReleasedCosts() {
  try {
    const costs = await api.get('/recipe/costs')
    const map = {}
    costs.forEach(c => { map[c.recipeId] = c })
    recipeCostMap.value = map
  } catch {}
}

// 拉取当前配方各版本成本
async function fetchVersionCosts() {
  versionCostMap.value = {}
  for (const v of versions.value) {
    try {
      const c = await api.get(`/recipe/version/${v.id}/cost`)
      versionCostMap.value = { ...versionCostMap.value, [v.id]: c }
    } catch {}
  }
}

function onSelectRecipe(row) {
  if (!row) return
  current.value = row
  currentVersion.value = null
  treeData.value = []
  fetchVersions(row.id)
  fetchProcess()
}

// ==================== 配方 CRUD ====================
async function openCreateRecipe() {
  editRecipeId.value = null
  sampleFormulaId.value = null
  recipeForm.value = { productName: '', productCode: '', recipeType: activeType.value, category: '', description: '', processTemplateId: null, qcTemplateId: null, packagingStandardId: null }
  await fetchProcessRoutes(recipeForm.value.recipeType)
  autoSelectDefaultRoute()
  recipeDialogVisible.value = true
  if (!sampleFormulas.value.length) {
    try { sampleFormulas.value = await api.get('/sample/formulas') } catch { /* 无打样权限时忽略 */ }
  }
}

// v7.7 从打样配方导入：自动选中成品物料并带出分类/描述
const sampleFormulaId = ref(null)
const sampleFormulas = ref([])
function onSampleFormulaChange(fid) {
  if (!fid) return
  const f = sampleFormulas.value.find(x => x.id === fid)
  if (!f) return
  // 打样生成的物料可能不在已缓存的 materials 里（如缓存早于物料创建），补进去保证下拉能选中
  if (f.materialCode && !materials.value.some(m => m.code === f.materialCode)) {
    materials.value.push({ code: f.materialCode, name: f.materialName, category: 'C', subCategory: f.subCategory, unit: 'kg' })
  }
  recipeForm.value.productCode = f.materialCode || ''
  onProductChange(f.materialCode)
  if (!recipeForm.value.description) recipeForm.value.description = '打样配方转入 ' + f.formulaNo
}

const productMaterials = computed(() => {
  const cat = recipeForm.value.recipeType === 'GRINDING' ? 'B' : 'C'
  return materials.value.filter(m => m.category === cat)
})
function onProductChange(code) {
  const m = materials.value.find(m => m.code === code)
  recipeForm.value.productName = m ? m.name : ''
  if (m && m.subCategory) {
    const subLabel = dicts.value.material_sub_category?.find(d => d.value === m.subCategory)
    recipeForm.value.category = subLabel ? subLabel.label : ''
  }
}
async function onRecipeTypeChange() {
  recipeForm.value.productCode = ''
  recipeForm.value.productName = ''
  recipeForm.value.category = ''
  await fetchProcessRoutes(recipeForm.value.recipeType)
  recipeForm.value.processTemplateId = null
  autoSelectDefaultRoute()
}
async function openEditRecipe() {
  editRecipeId.value = current.value.id
  recipeForm.value = {
    productName: current.value.productName,
    productCode: current.value.productCode || '',
    recipeType: current.value.recipeType || 'TINTING',
    category: current.value.category || '',
    description: current.value.description || '',
    processTemplateId: current.value.processTemplateId || null,
    qcTemplateId: current.value.qcTemplateId || null,
    packagingStandardId: current.value.packagingStandardId || null
  }
  await fetchProcessRoutes(recipeForm.value.recipeType)
  if (!recipeForm.value.processTemplateId) autoSelectDefaultRoute()
  recipeDialogVisible.value = true
}
async function submitRecipe() {
  if (!recipeForm.value.productCode) { ElMessage.warning('请选择产品物料'); return }
  if (!recipeForm.value.processTemplateId) { ElMessage.warning('请选择工艺路线'); return }
  // v5.99 四必填：质检模板、包装标准不选不允许保存（调色/磨浆一致）
  if (!recipeForm.value.qcTemplateId) { ElMessage.warning('请选择质检模板'); return }
  if (!recipeForm.value.packagingStandardId) { ElMessage.warning('请选择包装标准'); return }
  // 配方名称（=所选物料名）即时查重，排除自身；后端另有兜底校验
  const name = (recipeForm.value.productName || '').trim()
  const dup = recipeList.value.some(r => (r.productName || '').trim() === name && r.id !== editRecipeId.value)
  if (dup) { ElMessage.warning(`配方名称「${name}」已存在，请更换产品物料`); return }
  saving.value = true
  try {
    if (editRecipeId.value) {
      await api.put(`/recipe/${editRecipeId.value}`, recipeForm.value)
    } else if (sampleFormulaId.value) {
      // v7.7 打样配方一键转制漆：先严格校验色浆（缺制浆配方直接拦截提示清单）
      try {
        const chk = await api.get(`/sample/formula/${sampleFormulaId.value}/convert-check`)
        if (!chk.ok) {
          const names = (chk.missing || []).map(m => m.materialCode + ' ' + m.materialName).join('、')
          ElMessage.error('以下色浆没有已发布的制浆配方，请先在制浆配方中建立：' + names)
          saving.value = false
          return
        }
      } catch (e) { ElMessage.error(e.response?.data?.msg || '校验失败'); saving.value = false; return }
      const r = await api.post(`/sample/formula/${sampleFormulaId.value}/to-recipe`, {
        processTemplateId: recipeForm.value.processTemplateId,
        qcTemplateId: recipeForm.value.qcTemplateId,
        packagingStandardId: recipeForm.value.packagingStandardId,
        category: recipeForm.value.category
      })
      ElMessage.success(`已从打样配方创建制漆配方 ${r.recipeNo}（V1.0 草稿，配方树已按标准批量 100 预填，可微调）`)
      recipeDialogVisible.value = false
      await fetchList()
      const created = recipeList.value.find(x => x.id === r.recipeId)
      // v7.8.1 走 onSelectRecipe（等价用户点开该配方）：拉版本+自动选中+显示预填的配方树（此前只赋 current，树区空白）
      if (created) onSelectRecipe(created)
      saving.value = false
      return
    } else {
      await api.post('/recipe', recipeForm.value)
    }
    ElMessage.success('保存成功')
    recipeDialogVisible.value = false
    await fetchList()
    if (editRecipeId.value && current.value) {
      const updated = recipeList.value.find(r => r.id === editRecipeId.value)
      if (updated) current.value = updated
    }
  } catch {} finally { saving.value = false }
}
async function deleteRecipe() {
  try {
    await ElMessageBox.confirm(`确认删除配方 ${current.value.recipeNo}？`, '删除', { type: 'warning' })
    await api.delete(`/recipe/${current.value.id}`)
    ElMessage.success('已删除')
    current.value = null
    versions.value = []
    treeData.value = []
    fetchList()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    const msg = e?.response?.data?.msg || e?.message || '删除失败'
    ElMessageBox.alert(msg, '删除失败', { type: 'error', confirmButtonText: '我知道了' })
  }
}

// 禁用/启用配方：禁用后任何单据不可引用
async function toggleEnabled() {
  const next = !current.value.enabled
  const action = next ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${action}配方 ${current.value.recipeNo}？${next ? '' : '禁用后，生产订单、委外订单将无法引用此配方。'}`, action + '配方', { type: 'warning' })
    await api.put(`/recipe/${current.value.id}/enabled`, null, { params: { enabled: next } })
    ElMessage.success(`已${action}`)
    fetchList()
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

// ==================== 版本管理 ====================
async function fetchVersions(recipeId) {
  try {
    versions.value = await api.get(`/recipe/${recipeId}/versions`)
    fetchVersionCosts()
    // 自动选中第一个版本
    if (versions.value.length) {
      onSelectVersion(versions.value[0])
    }
  } catch {}
}

function onSelectVersion(row) {
  if (!row) return
  currentVersion.value = { ...row }
  fetchTree(row.id)
}

const changesVisible = ref(false)
const changes = ref([])
async function openChanges() {
  if (!current.value?.id) { ElMessage.warning('请先选择配方'); return }
  try {
    changes.value = await api.get(`/recipe/${current.value.id}/changes`)
    changesVisible.value = true
  } catch {}
}

async function createVersion() {
  try {
    await api.post(`/recipe/${current.value.id}/version`, { batchQty: 100, unit: 'kg' })
    ElMessage.success('新版本已创建')
    fetchVersions(current.value.id)
  } catch {}
}

async function releaseVersion(row) {
  try {
    await ElMessageBox.confirm(`确认发布版本 ${row.versionNo}？\n发布后旧版本将自动归档。`, '发布', { type: 'warning' })
    await api.post(`/recipe/version/${row.id}/release`)
    ElMessage.success('已发布')
    fetchVersions(current.value.id)
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

async function deleteVersion(row) {
  try {
    await ElMessageBox.confirm(`确认删除版本 ${row.versionNo}？`, '删除', { type: 'warning' })
    await api.delete(`/recipe/version/${row.id}`)
    ElMessage.success('已删除')
    fetchVersions(current.value.id)
  } catch (e) { if (e !== 'cancel' && e !== 'close') {} }
}

async function updateVersionMeta() {
  if (!currentVersion.value) return
  try {
    await api.put(`/recipe/version/${currentVersion.value.id}`, {
      batchQty: currentVersion.value.batchQty,
      unit: currentVersion.value.unit
    })
  } catch {}
}

// ==================== 配方树 ====================
// 树重载序号：随 fetchTree 递增，用于给用量输入框 :key 强制重建（保存后清掉 el-input-number 内部残留状态）
const treeReloadSeq = ref(0)
async function fetchTree(versionId) {
  try {
    const raw = await api.get(`/recipe/version/${versionId}/tree`)
    uidSeq = 0
    treeData.value = assignUid(raw)
    treeReloadSeq.value++
  } catch { treeData.value = [] }
}

function assignUid(nodes) {
  if (!nodes) return []
  return nodes.map(n => {
    n.uid = 'n' + (++uidSeq)
    n.label = n.materialName || n.materialCode
    if (n.children) n.children = assignUid(n.children)
    return n
  })
}

// 树形表格行样式：半成品节点整行浅色底，层级一目了然（与打印单 .sub-row 风格一致）
function treeRowClass({ row }) {
  return row.nodeType === 'SUB_RECIPE' ? 'recipe-sub-row' : ''
}

// v5.35：油尾库可用物料（制漆配方加「油尾」节点：体系一致 + 编码或色系相同才可选）
const tailingOptions = ref([])
async function fetchTailingOptions() {
  try { tailingOptions.value = await api.get('/recipe/tailing-options') } catch { tailingOptions.value = [] }
}
// 配方产品的体系/色系（油尾匹配校验用）
const productMat = computed(() => materials.value.find(m => m.code === current.value?.productCode) || null)

function addTreeNode(parentData) {
  nodeParent = parentData
  nodeForm.value = { nodeType: 'MATERIAL', materialCode: '', materialName: '', spec: '', category: '', subCategory: '', refRecipeId: null, qty: 1, unit: 'kg' }
  fetchTailingOptions()
  nodeDialogVisible.value = true
}

function onNodeMatChange(code) {
  const m = materials.value.find(m => m.code === code)
  if (m) { nodeForm.value.materialName = m.name || ''; nodeForm.value.spec = m.brand || ''; nodeForm.value.category = m.category || ''; nodeForm.value.subCategory = m.subCategory || '' }
}
// v5.35：选择油尾物料
function onNodeTailChange(code) {
  const t = tailingOptions.value.find(t => t.materialCode === code)
  if (t) {
    nodeForm.value.materialName = t.materialName || ''
    nodeForm.value.category = 'C'
    nodeForm.value.subCategory = ''
  }
}
function onNodeRecipeChange(id) {
  const r = recipeList.value.find(r => r.id === id)
  if (r) {
    nodeForm.value.materialName = r.productName
    // v5.6：半成品为常备库存，自动按名称匹配 B 类半成品物料（领料出库用编码）
    const m = materials.value.find(m => m.category === 'B' && r.productName
      && (r.productName.includes(m.name) || m.name.includes(r.productName)))
    nodeForm.value.materialCode = m ? m.code : ''
    nodeForm.value.spec = m ? m.brand || '' : ''
    nodeForm.value.category = 'B'
  }
}
function onNodeSemiMatChange(code) {
  const m = materials.value.find(m => m.code === code)
  if (m) { nodeForm.value.materialName = m.name || ''; nodeForm.value.spec = m.brand || ''; nodeForm.value.category = 'B' }
}
// v5.6：B 类半成品物料（常备库存，配方半成品节点领料用）
const semiMaterials = computed(() => (materials.value || []).filter(m => m.category === 'B'))

function confirmAddNode() {
  if (nodeForm.value.nodeType === 'MATERIAL' && !nodeForm.value.materialCode) {
    ElMessage.warning('请选择物料'); return
  }
  if (nodeForm.value.nodeType === 'SUB_RECIPE' && !nodeForm.value.refRecipeId) {
    ElMessage.warning('请选择子配方'); return
  }
  if (nodeForm.value.nodeType === 'SUB_RECIPE' && !nodeForm.value.materialCode) {
    ElMessage.warning('请选择半成品物料（B类，用于领料出库）'); return
  }
  // v5.35：油尾节点——体系一致 + 编码或色系相同（前端预检，后端保存兜底）
  if (nodeForm.value.nodeType === 'OIL_TAIL') {
    if (!nodeForm.value.materialCode) { ElMessage.warning('请选择油尾物料'); return }
    const t = tailingOptions.value.find(t => t.materialCode === nodeForm.value.materialCode)
    const p = productMat.value
    if (t && p && t.mainMaterial && p.mainMaterial && t.mainMaterial !== p.mainMaterial) {
      ElMessage.warning(`油尾体系（${t.mainMaterial}）与配方产品体系（${p.mainMaterial}）不一致，不可加入`); return
    }
    if (t && p && t.materialCode !== p.code && (!t.colorSeries || t.colorSeries !== p.colorSeries)) {
      ElMessage.warning('油尾与配方产品既不同编码也不同色系，不可加入'); return
    }
  }
  const node = {
    uid: 'n' + (++uidSeq),
    nodeType: nodeForm.value.nodeType,
    materialCode: nodeForm.value.materialCode,
    materialName: nodeForm.value.materialName,
    spec: nodeForm.value.spec || '',
    category: nodeForm.value.category || '',
    subCategory: nodeForm.value.subCategory || '',
    unit: nodeForm.value.unit,
    qty: nodeForm.value.qty,
    refRecipeId: nodeForm.value.refRecipeId,
    remark: nodeForm.value.nodeType === 'OIL_TAIL' ? '油尾' : '',
    label: nodeForm.value.materialName || nodeForm.value.materialCode,
    children: []
  }
  if (nodeParent) {
    if (!nodeParent.children) nodeParent.children = []
    nodeParent.children.push(node)
  } else {
    treeData.value.push(node)
  }
  nodeDialogVisible.value = false
}

// ===== 制浆配方：表格底部行内快速添加原料 =====
function onAddRowMatChange(code) {
  const m = materials.value.find(x => x.code === code)
  if (m) {
    addRow.value.materialName = m.name || ''
    addRow.value.category = m.category || ''
    addRow.value.subCategory = m.subCategory || ''
    const p = Number(priceMap.value[code]) || 0
    addRow.value.unitPrice = p
    addRow.value.cost = +(p * (Number(addRow.value.qty) || 0)).toFixed(2)
  }
}
function recalcAddRowCost() {
  const p = Number(addRow.value.unitPrice) || 0
  addRow.value.cost = +(p * (Number(addRow.value.qty) || 0)).toFixed(2)
}
function confirmAddRow() {
  if (!addRow.value.materialCode) { ElMessage.warning('请选择物料'); return }
  if (!addRow.value.qty || addRow.value.qty <= 0) { ElMessage.warning('请输入用量'); return }
  const node = {
    uid: 'n' + (++uidSeq),
    nodeType: 'MATERIAL',
    materialCode: addRow.value.materialCode,
    materialName: addRow.value.materialName,
    spec: '',
    category: addRow.value.category,
    subCategory: addRow.value.subCategory,
    unit: 'kg',
    qty: addRow.value.qty,
    refRecipeId: null,
    remark: '',
    unitPrice: Number(addRow.value.unitPrice) || 0,
    cost: Number(addRow.value.cost) || 0,
    label: addRow.value.materialName || addRow.value.materialCode,
    children: []
  }
  treeData.value.push(node)
  addRow.value = { materialCode: '', materialName: '', qty: 1, unit: 'kg', category: '', subCategory: '', unitPrice: 0, cost: 0 }
  ElMessage.success('已添加，记得点「保存树」生效')
}

function removeTreeNode(data) {
  const removeFrom = (arr) => {
    const idx = arr.findIndex(n => n.uid === data.uid)
    if (idx >= 0) { arr.splice(idx, 1); return true }
    for (const n of arr) {
      if (n.children && removeFrom(n.children)) return true
    }
    return false
  }
  removeFrom(treeData.value)
}

function stripUid(nodes) {
  return nodes.map(n => {
    const { uid, label, ...rest } = n
    if (rest.children && rest.children.length) {
      rest.children = stripUid(rest.children)
    } else {
      delete rest.children
    }
    return rest
  })
}

// ===== v5.1：平替物料（物料维护中配置，仅原材料；配方树中一键切换） =====
const altDialogVisible = ref(false)
const altNode = ref(null)
const altList = ref([])

/** 某物料的平替物料列表（按 alternativeCodes 解析物料主档） */
function altOf(code) {
  const m = materials.value.find(x => x.code === code)
  if (!m || !m.alternativeCodes) return []
  return String(m.alternativeCodes).split(',')
    .map(s => s.trim()).filter(Boolean)
    .map(c => materials.value.find(x => x.code === c))
    .filter(Boolean)
}

function openAltDialog(data) {
  altNode.value = data
  altList.value = altOf(data.materialCode)
  altDialogVisible.value = true
}

/** 切换节点物料为平替物料：替换物料信息，用量保持不变（成本在保存树后由后端重算） */
function switchAlternative(alt) {
  const node = altNode.value
  if (!node) return
  node.materialCode = alt.code
  node.materialName = alt.name
  node.spec = ''
  node.unit = 'kg'
  node.category = alt.category || ''
  node.subCategory = alt.subCategory || ''
  node.unitPrice = null
  node.cost = null
  altDialogVisible.value = false
  ElMessage.success(`已切换为平替物料「${alt.name}」（${alt.code}），用量保持不变，请按需调整后保存树`)
}

async function saveTree() {
  if (!currentVersion.value) return
  // 校验：根节点物料用量之和必须等于标准批量
  const batchQty = Number(currentVersion.value.batchQty) || 100
  const totalQty = treeData.value.reduce((sum, n) => sum + (Number(n.qty) || 0), 0)
  if (Math.abs(totalQty - batchQty) > 0.001) {
    ElMessage.warning(`配方树物料添加量之和（${totalQty}）必须等于标准批量（${batchQty}），请调整后再保存`)
    return
  }
  treeSaving.value = true
  try {
    await api.post(`/recipe/version/${currentVersion.value.id}/tree`, stripUid(treeData.value))
    ElMessage.success('配方树已保存')
    fetchTree(currentVersion.value.id)
  } catch {} finally { treeSaving.value = false }
}

// ==================== 打印配方单 ====================
// 物料大类编码转中文名（A=助剂、P=颜料、F=填料、R=树脂、S=溶剂、B=半成品、C=成品）
const CATEGORY_LABEL_MAP = { A: '助剂', P: '颜料', F: '填料', R: '树脂', S: '溶剂', B: '半成品', C: '成品' }
function categoryLabel(code) {  if (!code) return '-'
  // 若已是中文（非单字母编码）直接返回原值
  return CATEGORY_LABEL_MAP[code] || code
}

// 物料小类代码转中文名（数据字典 material_sub_category；硬编码兜底保证字典未加载也显示中文）
const SUB_CATEGORY_LABEL_MAP = {
  AC: '催化剂', AD: '分散剂', AF: '消泡剂', AH: '固化剂', AM: '蜡', AR: '密着剂', AT: '防沉剂',
  AV: '表面控制', AW: '抗氧化剂', AX: '功能助剂', PJ: '有机颜料', PK: '无机颜料', PM: '金属颜料',
  PW: '珠光颜料', FB: '消光粉', FS: '砂面粉', FT: '增量填料', RA: '丙烯酸树脂', RC: '纤维素酯类',
  RE: '环氧树脂', RF: '氟碳树脂', RP: '聚酯树脂', RZ: '氨基树脂', SA: '芳烃',
  SE: '酯类', SG: '酮类', SH: '醇类', SX: '醚类', SY: '混合溶剂', BW: '白浆', BR: '红浆',
  BY: '黄浆', BG: '绿浆', BB: '黑浆', BL: '蓝浆', CD: '底漆', CQ: '清漆', CB: '背漆',
  CW: '面漆-白色系', CR: '面漆-红色系', CY: '面漆-黄色系', CG: '面漆-绿色系', CL: '面漆-蓝色系',
  CK: '面漆-黑色系', CO: '面漆-其他色', PR: '预混浆'
}
function subLabel(code) {
  if (!code) return '-'
  const item = (dicts.value.material_sub_category || []).find(d => d.value === code)
  return item ? item.label : (SUB_CATEGORY_LABEL_MAP[code] || code)
}

// v5.82.1 打印辅助：质检模板名 / 包装标准（套价+组合明细）
function printQcTemplateName() {
  const id = current.value?.qcTemplateId
  if (!id) return '自动匹配（按小类/主材/色系）'
  const t = (qcTemplates.value || []).find(x => x.id === id)
  return t ? t.name : '已绑定（模板已删除）'
}
function printPackagingText() {
  const id = current.value?.packagingStandardId
  if (!id) return '-'
  const ps = (packagingStandards.value || []).find(x => x.id === id)
  if (!ps) return '-'
  return ps.name + '（' + (ps.capacityKg ? ps.capacityKg + 'kg/套 ' : '') + '套￥' + fmt(ps.setPrice ?? ps.unitPrice ?? 0) + '）'
}
// v5.87 打印区块：标准工艺（详细版=原工艺指导单内容：步序+参数+工序质检项），配方单与工艺单合一
function printProcessSectionHtml() {
  const t = processTpl.value
  if (!t || !t.stages || !t.stages.length) return ''
  let rows = ''
  t.stages.forEach((stg, si) => {
    const steps = stg.steps || []
    if (!steps.length) {
      rows += stageCell(stg, si) + '<td colspan="3" style="font-size:12px">—</td></tr>'
      return
    }
    steps.forEach((stp, ti) => {
      const qc = (stg.qcItems || []).map(q => escHtml(q.name) + ' ' + escHtml(q.standard || '') + (q.testTimes > 1 ? '×' + q.testTimes : '')).join('；')
      rows += (ti === 0 ? stageCell(stg, si) : '<td></td>') +
        '<td class="center">' + escHtml(stp.stepCode || '') + '</td>' +
        '<td style="font-size:12px">' + escHtml(renderStepDesc(stp.description) || '') + (stp.params ? '（<b>' + escHtml(stp.params) + '</b>）' : '') +
        (qc && ti === 0 ? '<div style="color:#8a5a00;font-size:11px;">工序质检：' + qc + '</div>' : '') + '</td></tr>'
    })
  })
  if (!rows) return ''
  return '<div class="sec-title">二、标准工艺：' + escHtml(t.name) + '</div>' +
    '<table class="main"><thead><tr><th style="width:120px">工序/岗位</th><th style="width:55px">步序</th><th>操作说明（含参数与工序质检）</th></tr></thead><tbody>' + rows + '</tbody></table>'
}
function stageCell(stg, si) {
  return '<tr><td><b>' + escHtml(stg.stageNo || (si + 1)) + ' ' + escHtml(stg.stageName || '') + '</b>' +
    (stg.roleHint ? '<div style="font-size:11px;color:#666">' + escHtml(stg.roleHint) + '</div>' : '') + '</td>'
}
// 质检检测项
function printQcItemsSectionHtml() {
  const t = boundQcTemplate.value
  if (!t) return '<div class="sec-title">三、质检检测项</div><div style="font-size:12px;color:#666">未绑定模板——按物料小类/主材/色系自动匹配</div>'
  const items = t.items || []
  const rows = items.length ? items.map((it, i) => '<tr><td class="center">' + (i + 1) + '</td><td>' + escHtml(it.name || '') +
    '</td><td>' + escHtml(it.standard || '') + '</td><td class="center">' + escHtml(it.unit || '') + '</td><td>' + escHtml(it.method || '') + '</td></tr>').join('')
    : ''
  return '<div class="sec-title">三、质检检测项：' + escHtml(t.name) + '</div>' +
    (rows ? '<table class="main"><thead><tr><th style="width:40px">#</th><th>检测项目</th><th>标准要求</th><th style="width:55px">单位</th><th>检验方法</th></tr></thead><tbody>' + rows + '</tbody></table>'
      : '<div style="font-size:12px;color:#666">模板无检测项</div>')
}
// 包装组合
function printPackagingSectionHtml() {
  const ps = boundPackaging.value
  if (!ps) return '<div class="sec-title">四、包装标准</div><div style="font-size:12px;color:#666">未绑定</div>'
  const items = ps.items || []
  const rows = items.length ? items.map((it, i) => '<tr><td class="center">' + (i + 1) + '</td><td>' + escHtml(it.name || '') +
    '</td><td>' + escHtml(it.spec || '') + '</td><td class="num">' + Number(it.qty) + '</td><td class="num">' + fmt(it.unitPrice) +
    '</td><td class="num">' + (Number(it.qty) * Number(it.unitPrice)).toFixed(2) + '</td></tr>').join('') : ''
  return '<div class="sec-title">四、包装标准：' + escHtml(ps.name) + '（' + (ps.capacityKg ? ps.capacityKg + 'kg/套 ' : '') + '套￥' + fmt(ps.setPrice ?? ps.unitPrice ?? 0) + '）</div>' +
    (rows ? '<table class="main"><thead><tr><th style="width:40px">#</th><th>包装物料</th><th>规格</th><th style="width:70px">每套数量</th><th style="width:70px">单价</th><th style="width:70px">小计</th></tr></thead><tbody>' + rows + '</tbody></table>' : '')
}

function printPackagingItemsRow() {
  const id = current.value?.packagingStandardId
  const ps = (packagingStandards.value || []).find(x => x.id === id)
  if (!ps || !ps.items || !ps.items.length) return ''
  const txt = ps.items.map(i => i.name + (Number(i.qty) === 1 ? '' : '×' + Number(i.qty)) + ' ￥' + fmt(i.unitPrice)).join('；')
  return '<tr><td class="k">包装组合</td><td colspan="3" style="font-size:12px;">' + escHtml(txt) + '</td></tr>'
}

// 将配方树展平为表格行（半成品节点保留汇总行，子节点缩进展示）
function flattenTreeForPrint(nodes, depth = 0) {
  const rows = []
  nodes.forEach(n => {
    rows.push({ ...n, depth })
    if (n.children && n.children.length) rows.push(...flattenTreeForPrint(n.children, depth + 1))
  })
  return rows
}

// ==================== 标准工艺（按配方绑定路线只读展示 + 打印工艺指导单） ====================
const processTpl = ref(null)
function fetchProcess() {
  loadBoundQcDetail()   // v5.83 绑定模板检测项
  const pid = current.value?.processTemplateId
  const url = pid ? `/process/route/${pid}` : `/process/template/${current.value?.recipeType || 'GRINDING'}`
  api.get(url).then(d => { processTpl.value = d }).catch(() => { processTpl.value = null })
}
// 工艺路线下拉数据（按配方类型过滤，停用路线不出现）
const processRoutes = ref([])
// v5.81 质检模板 + 包装标准；v5.83 详情区展示完整模板/包装组合
const qcTemplates = ref([])
const packagingStandards = ref([])
const boundQcTemplateDetail = ref(null)
const boundQcTemplate = computed(() => {
  const id = current.value?.qcTemplateId
  if (!id) return null
  const base = (qcTemplates.value || []).find(x => x.id === id) || null
  if (!base) return null
  return boundQcTemplateDetail.value && boundQcTemplateDetail.value.id === id
    ? { ...base, items: boundQcTemplateDetail.value.items || [] }
    : base
})
const qcTplDimText = computed(() => {
  const t = (qcTemplates.value || []).find(x => x.id === current.value?.qcTemplateId)
  if (!t) return ''
  const parts = [t.subCategory && ('小类 ' + t.subCategory), t.mainMaterial && ('主材 ' + t.mainMaterial), t.colorSeries && ('色系 ' + t.colorSeries)].filter(Boolean)
  return parts.length ? '匹配：' + parts.join('／') : '大类通用'
})
const boundPackaging = computed(() => {
  const id = current.value?.packagingStandardId
  if (!id) return null
  return (packagingStandards.value || []).find(x => x.id === id) || null
})
async function loadBoundQcDetail() {
  const id = current.value?.qcTemplateId
  if (!id) { boundQcTemplateDetail.value = null; return }
  try { boundQcTemplateDetail.value = await api.get(`/qc-template/${id}`) } catch { boundQcTemplateDetail.value = null }
}
async function loadQcPackaging() {
  try { qcTemplates.value = await api.get('/qc-template/list') } catch { qcTemplates.value = [] }
  try { packagingStandards.value = await api.get('/packaging-standard', { params: { enabled: true } }) } catch { packagingStandards.value = [] }
}
// 按产品三维过滤模板：大类相同 且（模板维度空 或 与产品相同）；半成品不比色系
const matchedQcTemplates = computed(() => {
  const prod = productMaterials.value.find(m => m.code === recipeForm.value.productCode)
  const cat = recipeForm.value.recipeType === 'GRINDING' ? 'B' : 'C'
  return (qcTemplates.value || []).filter(t => {
    if (!t.enabled || t.applyCategory !== cat) return false
    if (prod) {
      if (t.subCategory && t.subCategory !== prod.subCategory) return false
      if (t.mainMaterial && t.mainMaterial !== prod.mainMaterial) return false
      if (cat === 'C' && t.colorSeries && t.colorSeries !== prod.colorSeries) return false
    }
    return true
  })
})
function tplDimSuffix(t) {
  const parts = [t.subCategory, t.mainMaterial, t.colorSeries].filter(Boolean)
  return parts.length ? '（' + parts.join('/') + '）' : '（不限）'
}
async function fetchProcessRoutes(type) {
  try {
    const list = await api.get(`/process/routes?recipeType=${type}`)
    processRoutes.value = (list || []).filter(r => r.enabled !== false)
  } catch { processRoutes.value = [] }
}
function autoSelectDefaultRoute() {
  const def = processRoutes.value.find(r => r.isDefault)
  recipeForm.value.processTemplateId = def ? def.id : null
}
/** 步骤描述里的 {{N}} 替换为配方顶层第 N 个物料名（投料顺序） */
// v5.88 工艺说明里的 {{N}} 显示配方明细序号（N=1/2/3…对应配方树序号列），工人按号交叉查配方表，不挤长物料名
function renderStepDesc(desc) {
  if (!desc) return ''
  return desc.replace(/\{\{(\d+)\}\}/g, (m, n) => {
    const node = treeData.value[parseInt(n) - 1]
    return node ? n : m   // 有对应明细→显示序号；无（越界）保留原文
  })
}
function printProcessSheet() {
  if (!current.value) { ElMessage.warning('请先选择配方'); return }
  if (!processTpl.value || !processTpl.value.stages || !processTpl.value.stages.length) {
    ElMessage.warning('当前配方未绑定工艺路线'); return
  }
  printing.value = true
  api.post('/print-count', { docType: 'RECIPE', docNo: current.value.recipeNo }).then(n => { current.value.printCount = n }).catch(() => {})
  const v = currentVersion.value
  const tpl = processTpl.value
  const now = new Date()
  const pad = x => String(x).padStart(2, '0')
  const nowStr = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate()) + ' ' + pad(now.getHours()) + ':' + pad(now.getMinutes())
  const typeName = current.value.recipeType === 'GRINDING' ? '制浆（研磨）' : '制漆（调色）'
  const matRows = treeData.value.map((m, i) =>
    '<tr><td class="center">' + (i + 1) + '</td><td>' + escHtml(m.materialCode || '') + '</td><td>' + escHtml(m.materialName || '-') + '</td><td class="center">' + escHtml(categoryLabel(m.category)) + (m.subCategory ? '/' + escHtml(subLabel(m.subCategory)) : '') + '</td><td class="num">' + (m.qty || '') + '</td><td class="center">' + escHtml(m.unit || '') + '</td></tr>'
  ).join('')
  const stageHtml = tpl.stages.map(stg => {
    const steps = (stg.steps || []).map(stp =>
      '<div class="p-step"><b>' + escHtml(stp.stepCode || '') + '</b>. ' + escHtml(renderStepDesc(stp.description)) + (stp.params ? ' <span class="p-param">[' + escHtml(stp.params) + ']</span>' : '') + '</div>'
    ).join('')
    const qcs = (stg.qcItems || []).map(qc => {
      const times = qc.testTimes || 1
      let rows = ''
      for (let i = 0; i < times; i++) {
        rows += '<tr><td class="center">' + (i + 1) + '</td><td>' + escHtml(qc.name || '') + '</td><td class="center">' + escHtml(qc.standard || '') + '</td><td></td><td></td></tr>'
      }
      return '<table class="qc-table"><thead><tr><th style="width:40px">序号</th><th style="width:90px">检测项</th><th style="width:90px">标准</th><th>实测值</th><th style="width:80px">检测人</th></tr></thead><tbody>' + rows + '</tbody></table>'
    }).join('')
    return '<div class="p-stage"><div class="p-stage-h"><span class="p-no">' + escHtml(stg.stageNo || '') + '</span><b>' + escHtml(stg.stageName || '') + '</b>' + (stg.roleHint ? '<span class="p-role">（' + escHtml(stg.roleHint) + '）</span>' : '') + '</div>' + steps + qcs + '</div>'
  }).join('')
  const html = [
    '<!DOCTYPE html><html><head><meta charset="utf-8"><title>工艺指导单</title><style>',
    'body{font-family:"Microsoft YaHei","SimSun",sans-serif;color:#111;margin:24px 30px;}',
    'h1{text-align:center;font-size:20px;margin:0 0 4px;}',
    '.sub{text-align:center;font-size:12px;color:#555;margin-bottom:14px;}',
    '.info{width:100%;border-collapse:collapse;margin-bottom:14px;}',
    '.info td{border:1px solid #888;padding:5px 10px;font-size:13px;}',
    '.info .k{width:90px;color:#666;background:#f5f5f5;}',
    'table.mats{width:100%;border-collapse:collapse;margin-bottom:16px;}',
    'table.mats th,table.mats td{border:1px solid #888;padding:4px 8px;font-size:12px;}',
    'table.mats th{background:#f0f0f0;}',
    '.num{text-align:right;}.center{text-align:center;}',
    '.p-stage{border:1px solid #aaa;border-radius:4px;padding:10px 12px;margin-bottom:12px;}',
    '.p-stage-h{font-size:14px;margin-bottom:6px;padding-bottom:4px;border-bottom:1px dashed #ccc;}',
    '.p-stage-h .p-no{display:inline-block;background:#4a6785;color:#fff;border-radius:3px;padding:1px 8px;margin-right:8px;font-size:12px;}',
    '.p-role{color:#64748b;font-weight:400;font-size:12px;}',
    '.p-step{font-size:13px;line-height:1.7;}',
    '.p-param{color:#4a6785;font-size:12px;}',
    '.qc-table{width:100%;border-collapse:collapse;margin-top:8px;font-size:12px;}',
    '.qc-table th,.qc-table td{border:1px solid #999;padding:3px 6px;}',
    '.qc-table th{background:#f5f5f5;}',
    '.sign{display:flex;justify-content:space-between;margin-top:40px;font-size:13px;}',
    '.sign span{border-top:1px solid #888;padding-top:6px;min-width:160px;text-align:center;display:inline-block;}',
    '@media print{body{margin:8px 12px;}}',
    '</style></head><body>',
    '<h1>' + typeName + '工艺指导单</h1>',
    '<div class="sub">打印时间：' + nowStr + '</div>',
    '<table class="info">',
    '<tr><td class="k">配方编号</td><td>' + escHtml(current.value.recipeNo) + '</td><td class="k">品名</td><td>' + escHtml(current.value.productName) + '</td></tr>',
    '<tr><td class="k">版本</td><td>' + escHtml(v ? v.versionNo : '-') + (v ? '（' + vStatusLabel(v.status) + '）' : '') + '</td><td class="k">标准批量</td><td>' + (v ? v.batchQty : '-') + ' ' + escHtml(v ? (v.unit || 'kg') : 'kg') + '</td></tr>',
    '</table>',
    '<table class="mats"><thead><tr><th style="width:40px">序号</th><th style="width:110px">物料编码</th><th>品名</th><th style="width:120px">分类</th><th style="width:80px">用量</th><th style="width:50px">单位</th></tr></thead><tbody>',
    matRows,
    '</tbody></table>',
    stageHtml,
    '<div class="sign"><span>制单人：</span><span>操作人：</span><span>审核人：</span></div>',
    '</body></html>'
  ].join('')
  const win = window.open('', '_blank')
  if (!win) { ElMessage.warning('浏览器拦截了弹窗，请允许后重试'); printing.value = false; return }
  win.document.write(html)
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); printing.value = false }, 300)
}

function escHtml(s) {
  return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function printRecipe() {
  if (!current.value) { ElMessage.warning('请先选择配方'); return }
  if (!currentVersion.value || !treeData.value.length) { ElMessage.warning('当前配方无版本或配方树为空'); return }
  printing.value = true
  // v5.26：记录打印次数（失败不阻断打印）
  api.post('/print-count', { docType: 'RECIPE', docNo: current.value.recipeNo })
    .then(n => { current.value.printCount = n }).catch(() => {})
  const v = currentVersion.value
  const now = new Date()
  const nowStr = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0') + ' ' + String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0')
  const typeName = (current.value.recipeType === 'GRINDING' ? '制浆（研磨）' : '制漆（调色）')
  const rows = flattenTreeForPrint(treeData.value)
  // 保密打印（v5.0 调整）：原料不显示名称仅保留编码（工人按编码找料）；
  // 半成品显示名称（加粗）并可展开子配方——其下级原料明细缩进展示，层级结构一目了然
  const bodyRows = rows.map((r, idx) => {
    const isSub = r.nodeType === 'SUB_RECIPE'
    const isTail = r.nodeType === 'OIL_TAIL'
    const typeLabel = isSub ? '半成品' : isTail ? '油尾' : '原料'
    const indentCls = r.depth > 0 ? ' indent-' + Math.min(r.depth, 3) : ''
    const rowCls = isSub ? ' sub-row' : ''
    const codeCell = isSub
      ? '<td><strong>' + escHtml(r.materialName || '-') + '</strong>'
        + (r.materialCode ? ' <span class="sub-code">' + escHtml(r.materialCode) + '</span>' : '') + '</td>'
      : '<td>' + escHtml(r.materialCode || '-') + '</td>'
    return '<tr class="' + rowCls + '">'
      + '<td class="center">' + (idx + 1) + '</td>'
      + codeCell
      + '<td class="center">' + typeLabel + '</td>'
      + '<td class="center' + indentCls + '">' + escHtml(categoryLabel(r.category)) + (r.subCategory ? '/' + escHtml(r.subCategory) : '') + '</td>'
      + '<td class="num">' + r.qty + '</td>'
      + '<td class="center">' + escHtml(r.unit || '') + '</td></tr>'
  }).join('')
  const html = ['<!DOCTYPE html><html><head><meta charset="utf-8"><title>配方打印单</title><style>',
    'body{font-family:"Microsoft YaHei","SimSun",sans-serif;color:#111;margin:24px 30px;}',
    'h1{text-align:center;font-size:20px;margin:0 0 4px;}',
    '.sub{text-align:center;font-size:12px;color:#555;margin-bottom:14px;}',
    '.info{width:100%;border-collapse:collapse;margin-bottom:14px;}',
    '.info td{border:1px solid #888;padding:5px 10px;font-size:13px;}',
    '.info .k{width:90px;color:#666;background:#f5f5f5;}',
    'table.main{width:100%;border-collapse:collapse;}',
    'table.main th,table.main td{border:1px solid #888;padding:4px 8px;font-size:12px;}',
    'table.main th{background:#f0f0f0;}',
    '.num{text-align:right;}.center{text-align:center;}',
    '.indent{color:#888;}',
    '.sub-row td{background:#f2f5fa;font-weight:600;}',
    '.sub-code{color:#64748b;font-size:11px;font-weight:400;}',
    '.indent-1{padding-left:24px !important;}',
    '.indent-2{padding-left:40px !important;}',
    '.indent-3{padding-left:56px !important;}',
    '.cost-row{margin:12px 0;font-size:14px;font-weight:bold;}',
    '.sec-title{font-size:14px;font-weight:bold;margin:14px 0 6px;border-left:4px solid #333;padding-left:8px;}',
    '.sign{display:flex;justify-content:space-between;margin-top:50px;font-size:13px;}',
    '.sign span{border-top:1px solid #888;padding-top:6px;min-width:150px;text-align:center;display:inline-block;}',
    '@media print{ body{margin:8px 12px;} }',
    '</style></head><body>',
    '<h1>' + typeName + '配方单</h1>',
    '<div class="sub">打印时间：' + nowStr + '</div>',
    '<table class="info">',
    '<tr><td class="k">配方编号</td><td>' + escHtml(current.value.recipeNo) + '</td><td class="k">品名</td><td>' + escHtml(current.value.productName) + '</td></tr>',
    '<tr><td class="k">产品编码</td><td>' + escHtml(current.value.productCode || '-') + '</td><td class="k">分类</td><td>' + escHtml(current.value.category || '-') + '</td></tr>',
    '<tr><td class="k">版本</td><td>' + escHtml(v.versionNo) + '（' + vStatusLabel(v.status) + '）</td><td class="k">标准批量</td><td>' + v.batchQty + ' ' + escHtml(v.unit || 'kg') + '</td></tr>',
    '<tr><td class="k">描述</td><td colspan="3">' + escHtml(current.value.description || '-') + '</td></tr>',
    // v5.82.1 质检模板 + 包装标准（组合明细随单打印）
    '<tr><td class="k">质检模板</td><td>' + escHtml(printQcTemplateName()) + '</td><td class="k">包装标准</td><td>' + escHtml(printPackagingText()) + '</td></tr>',
    printPackagingItemsRow(),
    '</table>',
    // 一、配方树
    '<div class="sec-title">一、配方明细（投料）</div>',
    '<table class="main"><thead><tr><th>序号</th><th>物料</th><th>类型</th><th>分类</th><th>用量</th><th>单位</th></tr></thead><tbody>',
    bodyRows,
    '</tbody></table>',
    // 二、标准工艺
    printProcessSectionHtml(),
    // 三、质检检测项
    printQcItemsSectionHtml(),
    // 四、包装组合
    printPackagingSectionHtml(),
    '<div class="sign"><span>制单人：</span><span>审核人：</span></div>',
    '</body></html>'].join('')
  const win = window.open('', '_blank')
  if (!win) { ElMessage.warning('浏览器拦截了弹窗，请允许后重试'); printing.value = false; return }
  win.document.write(html)
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); printing.value = false }, 300)
}

// ==================== 初始化 ====================
onMounted(async () => {
  loadQcPackaging()
  try {
    const mats = await api.get('/material', { params: { enabled: true } })
    materials.value = mats.map(m => ({ code: m.code, name: m.name, brand: m.brand, category: m.category, subCategory: m.subCategory, shelfLifeDays: m.shelfLifeDays, alternativeCodes: m.alternativeCodes }))
  } catch {}
  try {
    const ds = await api.get('/dict')
    const grouped = {}
    ds.forEach(d => { if (!grouped[d.type]) grouped[d.type] = []; grouped[d.type].push(d) })
    dicts.value = grouped
  } catch {}
  fetchPriceMap()
  fetchList()
})
</script>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.recipe-layout { display: flex; gap: 16px; align-items: flex-start; }
.recipe-left { flex: 0 0 40%; min-width: 360px; }
.recipe-right { flex: 1; min-width: 0; }
.recipe-empty { display: flex; align-items: center; justify-content: center; min-height: 300px; background: #fafafa; border-radius: 8px; }
.search-bar { display: flex; gap: 8px; margin-bottom: 12px; }
.type-tabs { display: flex; gap: 0; margin-bottom: 12px; background: #f1f5f9; border-radius: 8px; padding: 3px; }
.type-tab { flex: 1; padding: 8px 0; font-size: 13px; font-weight: 600; border: none; border-radius: 6px; cursor: pointer; background: transparent; color: #64748b; transition: all 0.2s; }
.type-tab:hover { color: #334155; }
.type-tab.active { background: #fff; color: #4a6785; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.status-tabs { display: flex; gap: 0; margin-bottom: 12px; background: #f1f5f9; border-radius: 8px; padding: 3px; }
.status-tab { flex: 1; padding: 6px 0; font-size: 12px; border: none; border-radius: 6px; cursor: pointer; background: transparent; color: #64748b; transition: all 0.2s; }
.status-tab:hover { color: #334155; }
.status-tab.active { background: #fff; color: #4a6785; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.detail-card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.detail-header { display: flex; align-items: center; justify-content: space-between; }
.detail-header h3 { margin: 0; font-size: 16px; }
.recipe-no { font-size: 12px; color: #64748b; font-weight: 400; margin-left: 8px; }
.detail-actions { display: flex; gap: 4px; }
.detail-meta { margin-top: 10px; display: flex; flex-wrap: wrap; gap: 16px; font-size: 13px; color: #475569; }
.version-section { margin-bottom: 16px; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.section-header h4 { margin: 0; font-size: 15px; }
.ver-label { font-size: 12px; color: #7288a5; background: #eef0f6; padding: 2px 8px; border-radius: 4px; margin-left: 8px; }
.tree-section { background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; }
.tree-actions { display: flex; gap: 8px; }
.tree-meta { display: flex; align-items: center; margin-bottom: 12px; font-size: 13px; color: #475569; }
.recipe-tree-table { margin-top: 8px; }
/* 半成品节点整行浅蓝底，层级清晰（与打印单 .sub-row 一致） */
.recipe-tree-table :deep(.recipe-sub-row) > td { background: #f2f5fa !important; }
.recipe-tree-table :deep(.recipe-sub-row:hover) > td { background: #e8eef7 !important; }
.row-type-tag { margin-right: 6px; vertical-align: middle; }
.row-name { font-weight: 400; vertical-align: middle; }
.row-name-sub { font-weight: 600; vertical-align: middle; }
.row-price { color: #64748b; }
.row-cost { color: #a8744f; font-weight: 600; }
.row-dash { color: #cbd5e1; }
.row-seq { display: inline-block; min-width: 22px; height: 22px; line-height: 22px; text-align: center; background: #4a6785; color: #fff; border-radius: 50%; font-size: 12px; font-weight: 600; }
/* 制浆配方：表格底部行内新增条 */
.tree-add-row { display: flex; align-items: center; gap: 8px; margin-top: 8px; padding: 8px 10px; background: #f8fafc; border: 1px dashed #b3c5d9; border-radius: 6px; }
.tree-add-row .add-mat { flex: 1; min-width: 180px; }
.tree-add-row .add-qty { width: 130px; flex-shrink: 0; }
.tree-add-row .add-price, .tree-add-row .add-cost { width: 90px; flex-shrink: 0; text-align: right; font-size: 13px; }
.tree-add-row .add-btn { flex-shrink: 0; }
/* 用量合计（实时） */
.tree-total { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 10px; padding: 8px 12px; background: #eff4f7; border: 1px solid #c2d5de; border-radius: 6px; font-size: 13px; color: #0c4a6e; }
.tree-total strong { color: #0369a1; font-size: 15px; }
.tree-total-sep { color: #94a3b8; }
.tree-total-warn { color: #a85d50; font-weight: 600; }
/* 标准工艺流程展示 */
.process-section { background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; margin-top: 16px; }
.process-stages { display: flex; flex-direction: column; gap: 12px; }
.proc-stage { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 10px 12px; }
.proc-stage-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.proc-no { background: #4a6785; color: #fff; font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 4px; }
.proc-name { font-weight: 600; font-size: 14px; }
.proc-role { color: #64748b; font-size: 12px; margin-left: auto; }
.proc-step { font-size: 13px; line-height: 1.7; padding: 2px 0; display: flex; gap: 8px; align-items: flex-start; }
.proc-step-code { color: #7288a5; font-weight: 600; flex-shrink: 0; }
.proc-step-desc { flex: 1; }
.proc-step-params { color: #4a6785; font-size: 12px; background: #eff2f6; padding: 0 6px; border-radius: 3px; flex-shrink: 0; align-self: center; }
.proc-qc { margin-top: 8px; display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.proc-qc-title { font-size: 12px; color: #92400e; font-weight: 600; }
.proc-qc-tag { margin: 0; }
.cost-cell { color: #a8744f; font-weight: 600; cursor: default; }
.usage-count { color: #5b7a9c; font-weight: 600; }
.cost-empty { color: #cbd5e1; }
.sf-product-lock { padding: 4px 10px; background: #f0f5f0; border: 1px solid #c4d6c8; border-radius: 6px; font-size: 13px; color: #14532d; }
.sf-product-from { color: #16a34a; font-size: 12px; }
.form-tip { font-size: 12px; color: #94a3b8; line-height: 1.5; margin-top: 2px; }
.cost-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; background: #f5f1ea; border: 1px solid #fed7aa; border-radius: 6px; padding: 8px 12px; margin-bottom: 10px; font-size: 13px; }
.cost-bar-label { color: #9a3412; font-weight: 600; }
.cost-bar-total { color: #a8744f; font-weight: 700; font-size: 15px; }
.cost-bar-unit { color: #9a3412; }
.cost-bar-hint { color: #94a3b8; font-size: 12px; }
.alt-tip { font-size: 13px; color: #475569; background: #eff4f7; border: 1px solid #c2d5de; border-radius: 6px; padding: 8px 12px; margin: 0 0 12px; }
@media (max-width: 900px) {
  .recipe-layout { flex-direction: column; }
  .recipe-left { flex: none; width: 100%; }
}

.bind-info-card { background:#f5f7fa; border:1px solid #e4e7ed; border-radius:6px; padding:8px 14px; margin:10px 0; font-size:13px; color:#333; }
.dim-desc { font-size:12px; color:#888; }
</style>
