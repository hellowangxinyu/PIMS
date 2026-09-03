/**
 * 送货单打印（v5.47）
 * 照 statementPrint 模式：纯字符串拼 A4 HTML，window.open + document.write + print
 * 单物料一张出库单（SalesOutbound 无明细子表），行数据直接可打
 */

export function escHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

function fmtQty(v) {
  const n = Number(v || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(3).replace(/0+$/, '').replace(/\.$/, '')
}

/**
 * @param row 销售出库单行：docNo/salesOrderNo/customerName/materialCode/materialName/batchNo/qty/unit/warehouseName/createBy/dateText
 */
export function buildDeliveryNoteHtml(row) {
  const items = [{ code: row.materialCode, name: row.materialName, batch: row.batchNo, qty: row.qty, unit: row.unit }]
  const rows = items.map(it => `
    <tr>
      <td style="padding:8px;border:1px solid #d1d5db;text-align:center">1</td>
      <td style="padding:8px;border:1px solid #d1d5db">${escHtml(it.code)}</td>
      <td style="padding:8px;border:1px solid #d1d5db">${escHtml(it.name)}</td>
      <td style="padding:8px;border:1px solid #d1d5db;text-align:center">${escHtml(it.batch || '—')}</td>
      <td style="padding:8px;border:1px solid #d1d5db;text-align:right">${fmtQty(it.qty)}</td>
      <td style="padding:8px;border:1px solid #d1d5db;text-align:center">${escHtml(it.unit || '')}</td>
      <td style="padding:8px;border:1px solid #d1d5db"></td>
    </tr>`).join('')

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>送货单</title>
<style>
  @page { size: A4 portrait; margin: 16mm }
  body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; color: #111827; font-size: 13px; margin: 0 }
  .head { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 2px solid #111827; padding-bottom: 10px; margin-bottom: 14px }
  .head h1 { font-size: 22px; margin: 0; letter-spacing: 6px }
  .co { font-size: 16px; font-weight: 700 }
  .meta { display: grid; grid-template-columns: 1fr 1fr; gap: 5px 24px; margin-bottom: 14px }
  table { width: 100%; border-collapse: collapse; font-size: 12px }
  thead th { background: #f3f4f6; padding: 8px; border: 1px solid #d1d5db; font-weight: 600 }
  .sign { margin-top: 52px; display: flex; justify-content: space-between; font-size: 13px }
  .sign div { width: 200px; line-height: 2 }
  .footnote { margin-top: 22px; font-size: 10px; color: #6b7280 }
  @media print { .no-print { display: none } }
</style>
</head>
<body>
  <div class="head">
    <div>
      <h1>送 货 单</h1>
      <div style="font-size:11px;color:#6b7280;margin-top:4px">单号：${escHtml(row.docNo)}</div>
    </div>
    <div class="co">广东芃远新材料有限公司</div>
  </div>
  <div class="meta">
    <div>客户名称：<b>${escHtml(row.customerName)}</b></div>
    <div>关联销售订单：${escHtml(row.salesOrderNo || '—')}</div>
    <div>发货仓库：${escHtml(row.warehouseName || '')}</div>
    <div>送货日期：${escHtml(row.dateText || '')}</div>
    ${row.address ? `<div style="grid-column:1/-1">收货地址：${escHtml(row.address)}${row.contactPerson ? '　' + escHtml(row.contactPerson) : ''}${row.contactPhone ? '　' + escHtml(row.contactPhone) : ''}</div>` : ''}
  </div>
  <table>
    <thead>
      <tr>
        <th style="width:36px">序号</th><th style="width:90px">物料编码</th><th>品名</th>
        <th style="width:110px">批号</th><th style="width:80px">数量</th><th style="width:50px">单位</th><th style="width:70px">签收核验</th>
      </tr>
    </thead>
    <tbody>
      ${rows}
      <tr>
        <td colspan="4" style="padding:7px 8px;border:1px solid #d1d5db;text-align:right;background:#f9fafb"><b>合计</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;text-align:right;background:#f9fafb"><b>${fmtQty(row.qty)}</b></td>
        <td style="border:1px solid #d1d5db;background:#f9fafb;text-align:center">${escHtml(row.unit || '')}</td>
        <td style="border:1px solid #d1d5db;background:#f9fafb"></td>
      </tr>
    </tbody>
  </table>
  <div class="sign">
    <div>制单：<u>${escHtml(row.createBy || '')}</u></div>
    <div>送货人：____________</div>
    <div>客户签收：____________</div>
    <div>签收日期：____________</div>
  </div>
  <div class="footnote">
    请核对批号与数量后签收；如与订单不符请于收货当日联系我司。本单为客户签收凭证，请妥善保存。
  </div>
</body>
</html>`
}

export function printDeliveryNote(row) {
  const win = window.open('', '_blank')
  if (!win) return false
  win.document.write(buildDeliveryNoteHtml(row))
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); win.close() }, 200)
  return true
}
