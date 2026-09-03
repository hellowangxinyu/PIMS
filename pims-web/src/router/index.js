import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    children: [
      { path: '', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'supplier', name: 'Supplier', component: () => import('../views/SupplierList.vue') },
      { path: 'customer', name: 'Customer', component: () => import('../views/CustomerList.vue') },
      { path: 'material', name: 'Material', component: () => import('../views/MaterialList.vue') },
      { path: 'warehouse', name: 'Warehouse', component: () => import('../views/WarehouseList.vue') },
      { path: 'inventory', name: 'Inventory', component: () => import('../views/Inventory.vue') },
      { path: 'stock-check', name: 'StockCheck', component: () => import('../views/StockCheck.vue') },
      { path: 'recipe', name: 'Recipe', component: () => import('../views/RecipeList.vue') },
      { path: 'process', name: 'Process', component: () => import('../views/ProcessTemplate.vue') },
      { path: 'production-order', name: 'ProductionOrder', component: () => import('../views/ProductionOrder.vue') },
      { path: 'abnormal-order', name: 'AbnormalOrder', component: () => import('../views/AbnormalOrder.vue') },
      { path: 'schedule', name: 'Schedule', component: () => import('../views/ScheduleView.vue') },
      { path: 'production-outbound', name: 'ProductionOutbound', component: () => import('../views/ProductionOutbound.vue') },
      { path: 'production-inbound', name: 'ProductionInbound', component: () => import('../views/ProductionInbound.vue') },
      { path: 'sales-outbound', name: 'SalesOutbound', component: () => import('../views/SalesOutbound.vue') },
      { path: 'quotation', name: 'QuotationList', component: () => import('../views/QuotationList.vue') },
      { path: 'sample', name: 'SampleRequestList', component: () => import('../views/SampleRequestList.vue') },
      { path: 'complaint', name: 'ComplaintList', component: () => import('../views/ComplaintList.vue') },
      { path: 'outsource-outbound', name: 'OutsourceOutbound', component: () => import('../views/OutsourceOutbound.vue') },
      { path: 'outsource-inbound', name: 'OutsourceInbound', component: () => import('../views/OutsourceInbound.vue') },
      { path: 'other-outbound', name: 'OtherOutbound', component: () => import('../views/OtherOutbound.vue') },
      { path: 'other-inbound', name: 'OtherInbound', component: () => import('../views/OtherInbound.vue') },
      { path: 'return-order', name: 'ReturnOrder', component: () => import('../views/ReturnOrderList.vue') },
      { path: 'supplier-quality-trace', name: 'SupplierQualityTraceList', component: () => import('../views/SupplierQualityTraceList.vue') },
      { path: 'sales-return', name: 'SalesReturn', component: () => import('../views/SalesReturnList.vue') },
      { path: 'tailing-return', name: 'TailingReturn', component: () => import('../views/TailingReturn.vue') },
      { path: 'purchase', name: 'Purchase', component: () => import('../views/PurchaseOrderList.vue') },
      { path: 'raw-material-purchase', name: 'RawMaterialPurchase', component: () => import('../views/RawMaterialPurchase.vue') },
      { path: 'finished-product-purchase', name: 'FinishedProductPurchase', component: () => import('../views/FinishedProductPurchase.vue') },
      { path: 'purchase-arrival', name: 'PurchaseArrival', component: () => import('../views/PurchaseArrival.vue') },
      { path: 'sales', name: 'Sales', component: () => import('../views/SalesOrderList.vue') },
      { path: 'outsource', name: 'Outsource', component: () => import('../views/OutsourceOrderList.vue') },
      { path: 'users', name: 'Users', component: () => import('../views/UserList.vue') },
      { path: 'roles', name: 'Roles', component: () => import('../views/RoleList.vue') },
      { path: 'dict', name: 'Dict', component: () => import('../views/DictList.vue') },
      { path: 'ai', name: 'AiAssistant', component: () => import('../views/AiAssistant.vue') },
      { path: 'ai-settings', name: 'AiSettings', component: () => import('../views/AiSettings.vue') },
      { path: 'logs', name: 'OperationLog', component: () => import('../views/OperationLog.vue') },
      { path: 'coding-rule', name: 'CodingRule', component: () => import('../views/CodingRuleList.vue') },
      { path: 'packaging-standard', name: 'PackagingStandard', component: () => import('../views/PackagingStandardList.vue') },
      { path: 'report-purchase', name: 'ReportPurchase', component: () => import('../views/ReportPurchase.vue') },
      { path: 'report-inventory', name: 'ReportInventory', component: () => import('../views/ReportInventory.vue') },
      { path: 'report-ar', name: 'ReportAR', component: () => import('../views/ReportAR.vue') },
      { path: 'report-ap', name: 'ReportAP', component: () => import('../views/ReportAP.vue') },
      { path: 'report-ar-total', name: 'ReportARTotal', component: () => import('../views/ReportARTotal.vue') },
      { path: 'report-ap-total', name: 'ReportAPTotal', component: () => import('../views/ReportAPTotal.vue') },
      { path: 'report-finance-trend', name: 'ReportFinanceTrend', component: () => import('../views/ReportFinanceTrend.vue') },
      { path: 'payment-receipt', name: 'PaymentReceipt', component: () => import('../views/PaymentReceiptList.vue') },
      { path: 'payment-disbursement', name: 'PaymentDisbursement', component: () => import('../views/PaymentDisbursementList.vue') },
      { path: 'invoice', name: 'Invoice', component: () => import('../views/InvoiceList.vue') },
      { path: 'expense', name: 'Expense', component: () => import('../views/ExpenseList.vue') },
      { path: 'advance', name: 'Advance', component: () => import('../views/AdvanceList.vue') },
      { path: 'cost-accounting', name: 'CostAccounting', component: () => import('../views/CostAccounting.vue') },
      { path: 'crm-pipeline', name: 'CrmPipeline', component: () => import('../views/CrmPipeline.vue') },
      { path: 'crm-contact', name: 'CrmContact', component: () => import('../views/CrmContactList.vue') },
      { path: 'weekly-topic', name: 'WeeklyTopic', component: () => import('../views/WeeklyTopicList.vue') },
      { path: 'rd-progress', name: 'RdProgress', component: () => import('../views/RdProgressList.vue') },
      { path: 'report-profit-trial', name: 'ReportProfitTrial', component: () => import('../views/ReportProfitTrial.vue') },
      { path: 'customer-statement', name: 'CustomerStatement', component: () => import('../views/CustomerStatement.vue') },
      { path: 'supplier-statement', name: 'SupplierStatement', component: () => import('../views/SupplierStatement.vue') },
      { path: 'report-production', name: 'ReportProduction', component: () => import('../views/ReportProduction.vue') },
      { path: 'production-progress', name: 'ProductionProgress', component: () => import('../views/ProductionProgress.vue') },
      { path: 'report-low-stock', name: 'ReportLowStock', component: () => import('../views/ReportLowStock.vue') },
      { path: 'report-expiry', name: 'ReportExpiry', component: () => import('../views/ReportExpiry.vue') },
      { path: 'report-sales', name: 'ReportSales', component: () => import('../views/ReportSales.vue') },
      { path: 'report-qc', name: 'ReportQc', component: () => import('../views/ReportQc.vue') },
      { path: 'report-aging', name: 'ReportAging', component: () => import('../views/ReportAging.vue') },
      { path: 'report-stock-analysis', name: 'ReportStockAnalysis', component: () => import('../views/ReportStockAnalysis.vue') },
      { path: 'report-purchase-analysis', name: 'ReportPurchaseAnalysis', component: () => import('../views/ReportPurchaseAnalysis.vue') },
      { path: 'report-outsource', name: 'ReportOutsource', component: () => import('../views/ReportOutsource.vue') },
      { path: 'report-overview', name: 'ReportOverview', component: () => import('../views/ReportOverview.vue') },
      { path: 'quality-inspection', name: 'QualityInspection', component: () => import('../views/QualityInspection.vue') },
      { path: 'quality-statistics', name: 'QualityStatistics', component: () => import('../views/QualityStatistics.vue') },
      { path: 'qc-template', name: 'QcTemplateList', component: () => import('../views/QcTemplateList.vue') },
      { path: 'voucher', name: 'VoucherList', component: () => import('../views/VoucherList.vue') },
      { path: 'account-subject', name: 'AccountSubjectList', component: () => import('../views/AccountSubjectList.vue') },
      { path: 'opening-balance', name: 'OpeningBalance', component: () => import('../views/OpeningBalance.vue') },
      { path: 'period-close', name: 'PeriodClose', component: () => import('../views/PeriodClose.vue') },
      { path: 'report-account-balance', name: 'ReportAccountBalance', component: () => import('../views/ReportAccountBalance.vue') },
      { path: 'report-account-detail', name: 'ReportAccountDetail', component: () => import('../views/ReportAccountDetail.vue') },
      { path: 'report-balance-sheet', name: 'ReportBalanceSheet', component: () => import('../views/ReportBalanceSheet.vue') },
      { path: 'report-income-statement', name: 'ReportIncomeStatement', component: () => import('../views/ReportIncomeStatement.vue') },
      { path: 'report-cash-flow', name: 'ReportCashFlow', component: () => import('../views/ReportCashFlow.vue') },
      { path: 'employee', name: 'EmployeeList', component: () => import('../views/EmployeeList.vue') },
      { path: 'salary', name: 'SalarySheetList', component: () => import('../views/SalarySheetList.vue') },
      { path: 'asset', name: 'AssetList', component: () => import('../views/AssetList.vue') },
      { path: 'costing-settings', name: 'CostingSettings', component: () => import('../views/CostingSettings.vue') },
      { path: 'report-material-variance', name: 'ReportMaterialVariance', component: () => import('../views/ReportMaterialVariance.vue') },
      { path: 'shipping', name: 'ShippingLogList', component: () => import('../views/ShippingLogList.vue') },
      { path: 'task', name: 'TaskList', component: () => import('../views/TaskList.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  // v6.2：登录态改判 user（token 已走 HttpOnly Cookie，localStorage 不再有；后端 LoginPageInterceptor 302 兜底）
  const user = localStorage.getItem('user')
  if (to.path !== '/login' && !user) {
    next('/login')
  } else {
    next()
  }
})

// 前端更新后，旧页面懒加载的 chunk 已不存在（404）：自动刷新拉取最新 index.html（服务端 no-cache），
// 避免出现"点击菜单无反应"（ChunkLoadError / No static resource 404）
router.onError((error) => {
  const msg = error?.message || ''
  const isChunkMissing = error?.name === 'ChunkLoadError'
    || msg.includes('Loading chunk')
    || msg.includes('No static resource')
    || msg.includes('404')
  if (isChunkMissing) {
    window.location.reload()
  }
})

export default router
