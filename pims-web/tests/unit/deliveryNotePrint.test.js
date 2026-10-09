import { describe, expect, it } from 'vitest'
import { buildDeliveryNoteHtml, escHtml } from '../../src/utils/deliveryNotePrint.js'

const ROW = {
  docNo: 'SO-OUT-20261009-001',
  salesOrderNo: 'SO-20261009-001',
  customerName: '山东亚泰新材料科技有限公司',
  materialCode: 'M3004001',
  materialName: '预滚涂铝镁锰板',
  batchNo: 'B20261001',
  qty: 1200.5,
  unit: 'kg',
  warehouseName: '一号成品库',
  createBy: '王新宇',
  dateText: '2026-10-09',
}

describe('escHtml 转义（防注入）', () => {
  it('转义 5 个危险字符', () => {
    expect(escHtml(`<a href="x" onclick='y'>&`)).toBe('&lt;a href=&quot;x&quot; onclick=&#39;y&#39;&gt;&amp;')
  })
  it('null/undefined 转空串', () => {
    expect(escHtml(null)).toBe('')
    expect(escHtml(undefined)).toBe('')
  })
})

describe('buildDeliveryNoteHtml 送货单 HTML', () => {
  it('包含单号、客户、物料、批号、数量、仓库等关键字段', () => {
    const html = buildDeliveryNoteHtml(ROW)
    expect(html).toContain('送 货 单')
    expect(html).toContain(ROW.docNo)
    expect(html).toContain(ROW.customerName)
    expect(html).toContain(ROW.materialCode)
    expect(html).toContain(ROW.materialName)
    expect(html).toContain(ROW.batchNo)
    expect(html).toContain(ROW.warehouseName)
    expect(html).toContain(ROW.dateText)
  })

  it('用户输入全部转义：物料名注入不产出可执行标签', () => {
    const html = buildDeliveryNoteHtml({ ...ROW, materialName: '<script>alert(1)</script>' })
    expect(html).not.toContain('<script>alert')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
  })

  it('数量 3 位小数去尾零（打印口径，非千分位）', () => {
    expect(buildDeliveryNoteHtml({ ...ROW, qty: 1200.5 })).toContain('1200.5')
    expect(buildDeliveryNoteHtml({ ...ROW, qty: 2 })).toContain('>2<')
    expect(buildDeliveryNoteHtml({ ...ROW, qty: 1.234 })).toContain('1.234')
  })

  it('批号为空显示 —', () => {
    const html = buildDeliveryNoteHtml({ ...ROW, batchNo: null })
    expect(html).toContain('—')
    expect(html).not.toContain(ROW.batchNo)
  })

  it('有收货地址时展示地址块，无地址时不出现该块', () => {
    const withAddr = buildDeliveryNoteHtml({ ...ROW, address: '临沂高新区科技佳苑', contactPerson: '张三', contactPhone: '13800000000' })
    expect(withAddr).toContain('收货地址')
    expect(withAddr).toContain('临沂高新区科技佳苑')
    expect(withAddr).toContain('13800000000')
    const noAddr = buildDeliveryNoteHtml(ROW)
    expect(noAddr).not.toContain('收货地址')
  })

  it('落款含制单人、签收栏', () => {
    const html = buildDeliveryNoteHtml(ROW)
    expect(html).toContain(`制单：<u>${ROW.createBy}</u>`)
    expect(html).toContain('客户签收')
  })
})
