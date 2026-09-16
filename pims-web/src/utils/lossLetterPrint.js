/**
 * 损失沟通函打印（v5.59）
 * 照 statementPrint 模式：纯字符串拼完整 HTML（A4 纵向），window.open + document.write + print
 * 骨架固定（公司抬头/致供应商/批次采购信息表/损失金额强调/落款盖章栏），
 * 正文四段来自 LossLetterTemplate（开头语/问题与损失正文/处理要求/结尾语），支持 {{占位符}} 与换行。
 */

function escHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

function money(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtDate(v) {
  if (!v) return '—'
  return String(v).length >= 10 ? String(v).slice(0, 10) : String(v)
}

/** 模板段落渲染：替换 {{key}} 占位符（缺失置空），换行转 <br> */
function fillSection(text, data) {
  return escHtml(text)
    .replace(/\{\{\s*(\w+)\s*\}\}/g, (m, k) => escHtml(data[k]))   // v8.3（C3）：值也转义
    .replace(/\n/g, '<br>')
}

/**
 * @param trace   追溯单（SupplierQualityTrace 实体 JSON）
 * @param template 模板（LossLetterTemplate 实体 JSON）
 */
export function buildLossLetterHtml(trace, template) {
  const t = trace || {}
  const tpl = template || {}
  const data = {
    traceNo: t.traceNo, supplierName: t.supplierName, purchaseOrderNo: t.purchaseOrderNo || '—',
    materialCode: t.materialCode, materialName: t.materialName, batchNo: t.batchNo,
    purchaseQty: t.purchaseQty != null ? `${t.purchaseQty}${t.unit || ''}` : '—',
    purchaseUnitPrice: t.purchaseUnitPrice != null ? money(t.purchaseUnitPrice) : '—',
    purchaseAmount: t.purchaseAmount != null ? money(t.purchaseAmount) : '—',
    arrivalDate: fmtDate(t.arrivalDate), qcInspectionNo: t.qcInspectionNo || '—',
    qcResult: t.qcStatus || '—', category: t.category || '—',
    description: t.description || '', lossAmount: t.lossAmount != null ? money(t.lossAmount) : '0.00',
    issueDate: fmtDate(t.issueDate)
  }
  const sec = (v) => `<p class="sec">${fillSection(v || '', data)}</p>`
  // v5.59.2 委外批次：单价/金额是加工费口径，标签随之切换（成品漆大头走委外）
  const isOutsource = t.orderCategory === 'OUTSOURCE'
  const lblOrder = isOutsource ? '委外单号' : '采购单号'
  const lblPrice = isOutsource ? '加工费单价' : '采购单价'
  const lblAmount = isOutsource ? '加工费金额' : '采购金额'
  const lblDate = isOutsource ? '入库日期' : '到货日期'

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>损失沟通函 ${escHtml(t.traceNo)}</title>
<style>
  @page { size: A4 portrait; margin: 16mm }
  body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; color: #111827; font-size: 12.5px; line-height: 1.9; margin: 0 }
  .head { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 2px solid #111827; padding-bottom: 10px; margin-bottom: 16px }
  .head h1 { font-size: 22px; margin: 0; letter-spacing: 4px }
  .head .co { font-size: 15px; font-weight: 700 }
  .meta { font-size: 11px; color: #6b7280; margin-bottom: 4px }
  .to { font-size: 14px; font-weight: 600; margin: 14px 0 4px }
  .sec { margin: 10px 0; text-align: justify }
  table { width: 100%; border-collapse: collapse; font-size: 11.5px; margin: 10px 0 }
  th { background: #f3f4f6; padding: 6px 8px; border: 1px solid #d1d5db; font-weight: 600; text-align: left; width: 15%; white-space: nowrap }
  td { padding: 6px 8px; border: 1px solid #d1d5db }
  .loss-row td { background: #f6eded; font-size: 13px }
  .loss-amount { color: #a85d50; font-weight: 700; font-size: 16px }
  .sign { margin-top: 52px; display: flex; justify-content: space-between; font-size: 12.5px }
  .sign div { width: 240px }
  .footnote { margin-top: 26px; font-size: 10px; color: #6b7280; line-height: 1.7 }
  @media print { .no-print { display: none } }
</style>
</head>
<body>
  <div class="head">
    <div>
      <h1>损失沟通函</h1>
      <div class="meta">单号：${escHtml(t.traceNo)}　　发现日期：${escHtml(data.issueDate)}</div>
    </div>
    <div class="co">广东芃远新材料有限公司</div>
  </div>
  <div class="to">致：${escHtml(t.supplierName)}</div>
  ${sec(tpl.openingText)}
  <table>
    <tr><th>物料编码</th><td>${escHtml(t.materialCode)}</td><th>物料名称</th><td>${escHtml(t.materialName)}</td></tr>
    <tr><th>批　号</th><td><b>${escHtml(t.batchNo)}</b></td><th>${lblOrder}</th><td>${escHtml(data.purchaseOrderNo)}</td></tr>
    <tr><th>${lblDate}</th><td>${escHtml(data.arrivalDate)}</td><th>入库数量</th><td>${escHtml(data.purchaseQty)}</td></tr>
    <tr><th>${lblPrice}</th><td>¥ ${escHtml(String(data.purchaseUnitPrice))}</td><th>${lblAmount}</th><td>¥ ${escHtml(String(data.purchaseAmount))}</td></tr>
    <tr><th>质检单号</th><td>${escHtml(data.qcInspectionNo)}</td><th>问题类型</th><td>${escHtml(data.category)}</td></tr>
    <tr class="loss-row"><th>损失金额</th><td colspan="3">本次批次质量问题造成损失合计 <span class="loss-amount">¥ ${data.lossAmount}</span></td></tr>
  </table>
  ${sec(tpl.bodyText)}
  ${sec(tpl.requireText)}
  ${sec(tpl.closingText)}
  <div class="sign">
    <div>品控（发函）：____________</div>
    <div>日期：____________</div>
    <div>供应商确认（盖章）：____________</div>
  </div>
  <div class="footnote">
    本函所列批次采购信息由系统按批号自动带出（与采购入库台账一致）；
    请贵司在要求期限内书面回复处理意见，双方协商结果将由我司采购部门与贵司确认后执行。
  </div>
</body>
</html>`
}

export function printLossLetter(trace, template) {
  const win = window.open('', '_blank')
  if (!win) return false
  win.document.write(buildLossLetterHtml(trace, template))
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); win.close() }, 200)
  return true
}
