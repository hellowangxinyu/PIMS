/** LocalDate(2026-08-27) → 中文日期 2026年08月27日 */
function cnDate(v) {
  if (!v) return '-'
  const m = String(v).match(/(\d{4})-(\d{2})-(\d{2})/)
  return m ? m[1] + '年' + m[2] + '月' + m[3] + '日' : String(v)
}

/**
 * 采购请购单/采购订单打印（v5.71）
 * v5.74：打印件一律不含单价/金额（价格信息不允许出现在任何打印件上）
 * 原料采购与成品采购共用：按合同号聚合该单全部物料行，一张 A4 打印后可直接发给供应商
 * rows 必须是同一 orderNo 的全部行（页面打印前按 orderNo 拉全）；
 * opts.showPrice 无 purchase:price 权限时传 false，隐藏单价/金额列与大写合计
 */

function escHtml(s) {
  return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function fmtQty(v) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 3 })
}

function fmtAmt(v) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 人民币金额大写（精确到分，四舍五入；按万/亿四位分段，段单位不因段内零而丢失） */
function amtToCn(n) {
  n = Number(n || 0)
  if (n === 0) return '零元整'
  const neg = n < 0; n = Math.abs(n)
  const cents = Math.round(n * 100)
  const fen = cents % 10, jiao = Math.floor(cents / 10) % 10
  let yuan = Math.floor(cents / 100)
  const digits = '零壹贰叁肆伍陆柒捌玖'
  const units = ['', '拾', '佰', '仟']
  const bigs = ['', '万', '亿', '兆']
  // 按 4 位分段（低位在前），逐段转中文：段单位由段内是否有非零数字决定，跨零不丢"万/亿"
  const segs = []
  while (yuan > 0) { segs.push(yuan % 10000); yuan = Math.floor(yuan / 10000) }
  let cn = ''
  let zeroPending = false
  for (let si = segs.length - 1; si >= 0; si--) {
    const seg = segs[si]
    if (seg === 0) { if (cn) zeroPending = true; continue }
    // 段间跳位零：上一非零段与本段最高非零位之间跨了零（本段千位为 0，如 0001/0005 万段紧接亿段）
    if (cn && seg < 1000) zeroPending = true
    if (zeroPending) cn += '零'
    zeroPending = false
    const str = String(seg).padStart(4, '0')
    let segCn = ''
    let segZero = false
    for (let i = 0; i < 4; i++) {
      const d = +str[i]
      if (d === 0) { if (segCn) segZero = true }
      else {
        if (segZero) segCn += '零'
        segZero = false
        segCn += digits[d] + units[3 - i]
      }
    }
    cn += segCn + bigs[Math.min(si, 3)]
  }
  if (!cn) cn = '零'
  cn += '元'
  if (jiao === 0 && fen === 0) {
    cn += '整'
  } else {
    if (jiao > 0) cn += digits[jiao] + '角'
    if (fen > 0) cn += (jiao === 0 ? '零' : '') + digits[fen] + '分'
  }
  return (neg ? '负' : '') + cn
}

/**
 * @param rows   同一合同号的采购行数组（RawMaterialPurchase 或 FinishedProductPurchase 字段）
 * @param opts   { showPrice=true, kind='RAW'|'FINISHED', supplier:{contactPerson,phone,address,paymentTerms,paymentMethod}, warehouseName, receiver:{address,contact,phone} 收货信息（由交货仓库档案带出） }
 */
export function printPurchaseOrder(rows, opts = {}) {
  if (!rows || !rows.length) return
  const showPrice = opts.showPrice !== false
  const kind = opts.kind || 'RAW'
  const head = rows[0] || {}
  const supplier = opts.supplier || {}

  const totalQty = rows.reduce((a, r) => a + Number(r.qty || 0), 0)
  const totalAmt = rows.reduce((a, r) => a + Number(r.totalAmount || (r.qty || 0) * (r.unitPrice || 0)), 0)

  // v5.74：打印件不含单价/金额——明细只保留 序号/编码/品名/牌号/数量/单位/备注
  const bodyRows = rows.map((r, i) =>
    '<tr><td class="center">' + (i + 1) + '</td>' +
    '<td>' + escHtml(r.materialCode || '-') + '</td>' +
    '<td>' + escHtml(r.materialName || '-') + '</td>' +
    '<td>' + escHtml(r.brand || '-') + '</td>' +
    '<td class="amt">' + fmtQty(r.qty) + '</td>' +
    '<td class="center">' + escHtml(r.unit || 'kg') + '</td>' +
    '<td>' + escHtml(r.remark || '') + '</td></tr>').join('')
  const priceHead = ''
  const priceFoot = ''
  const cnLine = ''

  const now = new Date()
  const nowStr = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0')

  const html = '<!DOCTYPE html><html><head><meta charset="utf-8"><title>采购订单 ' + escHtml(head.orderNo) + '</title><style>' +
    '@page{size:A4 portrait;margin:14mm;}' +
    'body{font-family:"Microsoft YaHei","SimSun",sans-serif;color:#111;margin:0;font-size:13px;}' +
    '.company{text-align:center;font-size:24px;font-weight:bold;letter-spacing:6px;margin:0 0 2px;}' +
    'h1{text-align:center;font-size:18px;margin:0 0 2px;letter-spacing:2px;}' +
    '.sub{text-align:center;font-size:12px;color:#555;margin-bottom:14px;}' +
    'table{width:100%;border-collapse:collapse;table-layout:fixed;}' +
    'td,th{border:1px solid #888;padding:6px 8px;word-break:break-all;}' +
    '.info td.k{color:#555;background:#f5f5f5;text-align:center;}' +
    'table.main th{background:#f0f0f0;font-size:12px;}' +
    '.main td{height:28px;}' +
    '.center{text-align:center;}' +
    '.amt{text-align:right;}' +
    '.terms{margin:14px 0 0;font-size:12px;line-height:1.9;}' +
    '.terms b{display:inline-block;width:95px;text-align:right;margin-right:8px;color:#555;}' +
    '.sign{display:flex;justify-content:space-between;margin-top:56px;font-size:13px;}' +
    '.sign div{min-width:220px;}' +
    '.sign .t{border-bottom:1px solid #888;padding-bottom:4px;margin-bottom:6px;}' +
    '.note{margin-top:10px;font-size:11px;color:#777;}' +
    '</style></head><body>' +
    '<div class="company">广东芃远新材料有限公司</div>' +
    '<h1>' + (kind === 'FINISHED' ? '成品采购订单' : '原材料采购订单') + '</h1>' +
    '<div class="sub">Purchase Order　·　合同号：' + escHtml(head.orderNo) + '　·　打印日期：' + cnDate(nowStr) + '</div>' +
    '<table class="info"><colgroup><col style="width:100px"><col style="width:38%"><col style="width:100px"><col></colgroup>' +
    '<tr><td class="k">供应商</td><td colspan="3">' + escHtml(head.supplierName || '-') + '</td></tr>' +
    '<tr><td class="k">采购日期</td><td>' + cnDate(head.purchaseDate) + '</td><td class="k">交货仓库</td><td>' + escHtml(opts.warehouseName || '-') + '</td></tr>' +
    '<tr><td class="k">收货地址</td><td colspan="3">' + escHtml((opts.receiver && opts.receiver.address) || '-') + '</td></tr>' +
    '<tr><td class="k">收货联系人</td><td>' + escHtml(((opts.receiver && opts.receiver.contact) || '-') + '　' + ((opts.receiver && opts.receiver.phone) || '')) + '</td><td class="k">制单人</td><td>' + escHtml(head.createdBy || '-') + '</td></tr>' +
    '</table>' +
    '<table class="main" style="margin-top:12px;"><colgroup><col style="width:44px"><col style="width:110px"><col><col style="width:100px"><col style="width:90px"><col style="width:55px"><col style="width:130px"></colgroup>' +
    '<thead><tr>' +
    '<th>序号</th><th>物料编码</th><th>品名</th><th>牌号</th>' +
    '<th>数量</th><th>单位</th>' + priceHead + '<th>备注</th>' +
    '</tr></thead><tbody>' +
    bodyRows +
    '<tr><td colspan="4" style="text-align:right;font-weight:bold">合　计</td>' +
    '<td class="amt" style="font-weight:bold">' + fmtQty(totalQty) + '</td><td></td><td></td></tr>' +
    '</tbody></table>' +
    '<div class="terms">' +
    (supplier.paymentTerms ? '<div><b>付款条件：</b>' + escHtml(supplier.paymentTerms) + '</div>' : '') +
    (supplier.paymentMethod ? '<div><b>付款方式：</b>' + escHtml(supplier.paymentMethod) + '</div>' : '') +
    '<div><b>质量要求：</b>所提供的产品出现质量问题，采购方有权要求供方调换货、退货。</div>' +
    '<div><b>违约责任：</b>双方协调解决，若无效，按《合同法》执行。</div>' +
    '<div><b>随货要求：</b>随货携带合格证、质检单；送货请注明本合同号与批号，送至收货地址并由收货联系人签收。</div>' +
    '</div>' +
    '<div class="sign">' +
    '<div>采购经办人：' + escHtml(head.createdBy || '') + '　　日期：' + cnDate(nowStr) + '</div>' +
    '</div>' +
    '</body></html>'

  const iframe = document.createElement('iframe')
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;'
  document.body.appendChild(iframe)
  iframe.contentDocument.write(html)
  iframe.contentDocument.close()
  iframe.contentWindow.focus()
  iframe.contentWindow.print()
  setTimeout(() => iframe.remove(), 3000)
}
