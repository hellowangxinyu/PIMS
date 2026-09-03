// 质检报告 HTML 生成（v5.32：质检管理打印 + 质检模板预览 共用）
// row 传质检单信息（打印）或模拟信息（预览）；items 为检测项 [{name, standard, unit, method, measuredValue, itemResult}]

export function escHtml(s) {
  return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/**
 * 生成质检报告 HTML
 * @param row   {inspectionNo, refDocNo, materialCode, materialName, batchNo, qty, unit,
 *               warehouseName, produceDate, inspector, inspectDate, status, resultRemark, remark}
 * @param items 检测项（质检单快照含实测值；模板预览只传 name/standard/unit/method，实测值留空）
 * @param opts  {preview:true} 模板预览模式——顶部提示条+打印按钮，结论区不勾选
 */
export function buildQcReportHtml(row, items, opts = {}) {
  const now = new Date()
  const nowStr = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0')
  const statusMap = { PASS: '合格', CONCESSION: '让步接收', REJECT: '不合格' }
  const verdict = opts.preview ? '' : (statusMap[row.status] || row.status || '未知')
  // 判定结论：实际判定 ☑，其余 ☐（打印后也可手改勾选）
  const checks = ['PASS', 'CONCESSION', 'REJECT'].map(s => {
    const hit = !opts.preview && row.status === s
    return `<span class="check${hit ? ' hit' : ''}">${hit ? '☑' : '☐'} ${statusMap[s]}</span>`
  }).join('')
  // 有检测项按项渲染（含实测值与单项判定），否则 10 行空白表检验员手填（行业 COA 惯例）
  const itemRows = (items && items.length)
    ? items.map((it, i) => {
        const v = it.itemResult === 'PASS' ? '合格' : it.itemResult === 'FAIL' ? '不合格' : ''
        return '<tr><td class="center">' + (i + 1) + '</td><td>' + escHtml(it.name) +
          (it.method ? '<div style="font-size:11px;color:#777;">' + escHtml(it.method) + '</div>' : '') +
          '</td><td>' + escHtml(it.standard || '') + '</td><td>' + escHtml(it.measuredValue || '') +
          '</td><td class="center">' + escHtml(v) + '</td></tr>'
      }).join('')
    : Array.from({ length: 10 }, (_, i) =>
        '<tr><td class="center">' + (i + 1) + '</td><td></td><td></td><td></td><td></td></tr>').join('')
  const previewBar = opts.preview
    ? '<div class="preview-bar">模板预览——质检单创建时将按此模板生成检测项，实测值与单项判定由检验员逐项填写' +
      '<button class="preview-print" onclick="window.print()">打印本页</button></div>'
    : ''
  return [
    '<!DOCTYPE html><html><head><meta charset="utf-8"><title>' + (opts.preview ? '质检模板预览 ' : '质检报告单 ') + escHtml(row.inspectionNo || row.materialName || '') + '</title><style>',
    'body{font-family:"Microsoft YaHei","SimSun",sans-serif;color:#111;margin:28px 34px;}',
    '.company{text-align:center;font-size:24px;font-weight:bold;letter-spacing:6px;margin:0 0 2px;}',
    'h1{text-align:center;font-size:20px;margin:0 0 2px;letter-spacing:2px;}',
    '.sub{text-align:center;font-size:12px;color:#555;margin-bottom:16px;}',
    'table{width:100%;border-collapse:collapse;table-layout:fixed;}',
    'td,th{border:1px solid #888;padding:6px 10px;font-size:13px;}',
    '.info td.k{width:110px;color:#555;background:#f5f5f5;text-align:center;}',
    'table.main th{background:#f0f0f0;font-size:12px;}',
    '.main td{height:30px;}',
    '.center{text-align:center;}',
    '.conclusion{margin:16px 0 0;font-size:14px;}',
    '.checks{display:flex;justify-content:space-around;margin:10px 0 14px;font-size:14px;}',
    '.check.hit{font-weight:bold;}',
    '.sign{display:flex;justify-content:space-between;margin-top:60px;font-size:13px;}',
    '.sign span{border-top:1px solid #888;padding-top:6px;min-width:160px;text-align:center;display:inline-block;}',
    '.preview-bar{background:#fef8e7;border:1px solid #f0d896;border-radius:6px;padding:10px 16px;font-size:13px;color:#8a6d1a;margin-bottom:16px;display:flex;align-items:center;justify-content:space-between;}',
    '.preview-print{background:#fff;border:1px solid #d9b75c;color:#8a6d1a;border-radius:4px;padding:5px 16px;cursor:pointer;font-size:13px;}',
    '.preview-print:hover{background:#fdf3d9;}',
    '@media print{ body{margin:10px 14px;} .preview-bar{display:none;} }',
    '</style></head><body>',
    previewBar,
    '<div class="company">广东芃远新材料有限公司</div>',
    '<h1>质检报告单</h1>',
    '<div class="sub">Quality Inspection Report　·　' + (opts.preview ? '模板预览' : '报告编号：' + escHtml(row.inspectionNo)) + '　·　' + (opts.preview ? '生成日期：' : '打印日期：') + nowStr + '</div>',
    '<table class="info"><colgroup><col style="width:110px"><col style="width:44%"><col style="width:110px"><col></colgroup>',
    '<tr><td class="k">报告编号</td><td>' + escHtml(row.inspectionNo || '-') + '</td><td class="k">关联单号</td><td>' + escHtml(row.refDocNo || '-') + '</td></tr>',
    '<tr><td class="k">物料编码</td><td>' + escHtml(row.materialCode || '-') + '</td><td class="k">品名</td><td>' + escHtml(row.materialName || '-') + '</td></tr>',
    '<tr><td class="k">批号</td><td>' + escHtml(row.batchNo || '-') + '</td><td class="k">数量</td><td>' + escHtml(row.qty || '-') + ' ' + escHtml(row.unit || 'kg') + '</td></tr>',
    // v5.71.8 生产入库/委外入库的质检报告保留生产日期（自产产品）；来料质检不显示
    ((row.refDocType === 'PRODUCTION_INBOUND' || row.refDocType === 'OUTSOURCE_INBOUND') && row.produceDate
      ? '<tr><td class="k">生产日期</td><td>' + escHtml(row.produceDate) + '</td><td class="k">入库仓库</td><td>' + escHtml(row.warehouseName || '-') + '</td></tr>'
      : '<tr><td class="k">入库仓库</td><td colspan="3">' + escHtml(row.warehouseName || '-') + '</td></tr>'),
    '<tr><td class="k">检验员</td><td>' + escHtml(row.inspector || '-') + '</td><td class="k">检验日期</td><td>' + escHtml(row.inspectDate || '-') + '</td></tr>',
    '<tr><td class="k">判定结果</td><td>' + escHtml(verdict || '-') + '</td><td class="k">判定说明</td><td>' + escHtml(row.resultRemark || '-') + '</td></tr>',
    '<tr><td class="k">备注</td><td colspan="3">' + escHtml(row.remark || '-') + '</td></tr>',
    '</table>',
    '<table class="main"><thead><tr><th style="width:50px">序号</th><th>检验项目</th><th>标准值</th><th>实测值</th><th style="width:90px">单项判定</th></tr></thead><tbody>',
    itemRows,
    '</tbody></table>',
    '<div class="conclusion"><strong>检验结论：</strong></div>',
    '<div class="checks">' + checks + '</div>',
    '<div class="sign"><span>检验员签字</span><span>审核人签字</span><span>日期</span></div>',
    '</body></html>'
  ].join('')
}
