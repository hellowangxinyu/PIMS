import { describe, expect, it } from 'vitest'
import { fmtMoney, fmtQty, fmt } from '../../src/utils/fmt.js'

describe('fmtMoney 金额（千分位恒 2 位）', () => {
  it('0/空值兜底为 0.00', () => {
    expect(fmtMoney(0)).toBe('0.00')
    expect(fmtMoney(undefined)).toBe('0.00')
    expect(fmtMoney(null)).toBe('0.00')
    expect(fmtMoney('')).toBe('0.00')
  })
  it('千分位 + 恒 2 位小数', () => {
    expect(fmtMoney(12345.6)).toBe('12,345.60')
    expect(fmtMoney(1000000)).toBe('1,000,000.00')
  })
  it('第 3 位小数四舍五入', () => {
    expect(fmtMoney(1234567.891)).toBe('1,234,567.89')
    expect(fmtMoney(0.999)).toBe('1.00')
  })
  it('负数带千分位', () => {
    expect(fmtMoney(-1000.5)).toBe('-1,000.50')
  })
  it('字符串数字可格式化', () => {
    expect(fmtMoney('1234.5')).toBe('1,234.50')
  })
})

describe('fmtQty 数量（千分位最多 3 位小数）', () => {
  it('整数不带小数', () => {
    expect(fmtQty(1200)).toBe('1,200')
    expect(fmtQty(5)).toBe('5')
  })
  it('保留有效小数、去尾零', () => {
    expect(fmtQty(1200.5)).toBe('1,200.5')
    expect(fmtQty(3.1416)).toBe('3.142')
  })
  it('空值兜底 0', () => {
    expect(fmtQty(undefined)).toBe('0')
    expect(fmtQty(null)).toBe('0')
  })
})

describe('fmt 别名', () => {
  it('fmt === fmtMoney', () => {
    expect(fmt).toBe(fmtMoney)
    expect(fmt(1000)).toBe('1,000.00')
  })
})
