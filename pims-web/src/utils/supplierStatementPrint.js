/**
 * 供应商对账单打印（v6.3）
 * 照抄 statementPrint 模式：纯字符串拼完整 HTML 文档（A4 纵向），window.open + document.write + print
 * 数据来自 /finance-report/supplier-statement：{ supplier, from, to, opening, lines, debit, credit, closing }
 */

function escHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

function money(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function buildSupplierStatementHtml(data) {
  const sup = data.supplier || {}
  const lines = data.lines || []
  let balance = Number(data.opening || 0)
  const rows = lines.map(l => {
    balance += Number(l.debit || 0) - Number(l.credit || 0)
    return `<tr>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${escHtml(l.date)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${escHtml(l.docNo)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${escHtml(l.type)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db">${escHtml(l.note)}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right">${l.debit ? money(l.debit) : ''}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right">${l.credit ? money(l.credit) : ''}</td>
      <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${balance < 0 ? 'color:#dc2626' : ''}">${money(balance)}</td>
    </tr>`
  }).join('')

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>供应商对账单</title>
<style>
  @page { size: A4 portrait; margin: 14mm }
  body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; color: #111827; font-size: 12px; margin: 0 }
  .head { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 2px solid #111827; padding-bottom: 10px; margin-bottom: 14px }
  .head h1 { font-size: 20px; margin: 0; letter-spacing: 2px }
  .head .co { font-size: 15px; font-weight: 700 }
  .meta { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 24px; margin-bottom: 14px; font-size: 12px }
  .meta b { color: #374151 }
  table { width: 100%; border-collapse: collapse; font-size: 11px }
  thead th { background: #f3f4f6; padding: 7px 8px; border: 1px solid #d1d5db; font-weight: 600 }
  .summary { margin-top: 14px; display: flex; gap: 28px; font-size: 12px }
  .summary .item b { font-size: 14px }
  .closing { color: #dc2626 }
  .sign { margin-top: 46px; display: flex; justify-content: space-between; font-size: 12px }
  .sign div { width: 220px }
  .footnote { margin-top: 24px; font-size: 10px; color: #6b7280; line-height: 1.7 }
  @media print { .no-print { display: none } }
</style>
</head>
<body>
  <div class="head">
    <div>
      <h1>供应商对账单</h1>
      <div style="font-size:11px;color:#6b7280;margin-top:4px">对账期间：${escHtml(data.from)} 至 ${escHtml(data.to)}</div>
    </div>
    <div class="co">广东芃远新材料有限公司</div>
  </div>
  <div class="meta">
    <div>供应商名称：<b>${escHtml(sup.name)}</b></div>
    <div>供应商编码：${escHtml(sup.code)}</div>
    <div>制单日期：${new Date().toISOString().slice(0, 10)}</div>
  </div>
  <table>
    <thead>
      <tr>
        <th style="width:12%">日期</th><th style="width:18%">单据号</th><th style="width:12%">业务类型</th>
        <th style="width:26%">摘要</th><th style="width:10%">应付增加</th><th style="width:10%">付/退</th><th style="width:12%">结转余额</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td colspan="4" style="padding:6px 8px;border:1px solid #d1d5db"><b>期初余额</b></td>
        <td colspan="2" style="border:1px solid #d1d5db"></td>
        <td style="padding:6px 8px;border:1px solid #d1d5db;text-align:right;${Number(data.opening) < 0 ? 'color:#dc2626' : ''}"><b>${money(data.opening)}</b></td>
      </tr>
      ${rows || '<tr><td colspan="7" style="padding:14px;text-align:center;border:1px solid #d1d5db;color:#9ca3af">本期无往来记录</td></tr>'}
      <tr>
        <td colspan="4" style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"><b>本期合计</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${money(data.debit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb;text-align:right"><b>${money(data.credit)}</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#f9fafb"></td>
      </tr>
      <tr>
        <td colspan="6" style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2"><b>期末余额（我司应付供应商）</b></td>
        <td style="padding:7px 8px;border:1px solid #d1d5db;background:#fef2f2;text-align:right" class="closing"><b>¥ ${money(data.closing)}</b></td>
      </tr>
    </tbody>
  </table>
  <div class="summary">
    <div class="item">本期应付增加：<b>¥ ${money(data.debit)}</b></div>
    <div class="item">本期付款：<b>¥ ${money((lines || []).filter(l => l.type === '付款').reduce((s, l) => s + Number(l.credit || 0), 0))}</b></div>
    <div class="item">本期退货冲减：<b>¥ ${money((lines || []).filter(l => l.type === '退货冲减').reduce((s, l) => s + Number(l.credit || 0), 0))}</b></div>
  </div>
  <div class="sign">
    <div>制表人：____________</div>
    <div>供应商确认（盖章）：____________</div>
    <div>日期：____________</div>
  </div>
  <div class="footnote">
    对账口径：应付余额 = 应付立账累计 − 付款累计 − 采购退货冲减累计，与系统应付台账一致；
    如对以上数据有异议，请于收到对账单 7 日内与我司财务核对。
  </div>
</body>
</html>`
}

export function printSupplierStatement(data) {
  const win = window.open('', '_blank')
  if (!win) return false
  win.document.write(buildSupplierStatementHtml(data))
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); win.close() }, 200)
  return true
}
