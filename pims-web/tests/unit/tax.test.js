import { beforeEach, describe, expect, it, vi } from 'vitest'

// 每个用例动态 import：tax.js 模块级有税率缓存（cachedRate），隔离用例间状态
async function loadTax() {
  return import('../../src/utils/tax.js')
}

describe('netOfTax 含税→不含税', () => {
  it('13% 常规折算', async () => {
    const { netOfTax } = await loadTax()
    expect(netOfTax(11.3, 13)).toBe(10)
    expect(netOfTax(100, 13)).toBe(88.5)
  })
  it('v6.1.6 回归：结果恒为 2 位小数，无浮点长尾', async () => {
    const { netOfTax } = await loadTax()
    const v = netOfTax(10, 13)
    expect(v).toBe(8.85)
    expect(String(v)).toMatch(/^\d+(\.\d{1,2})?$/)
  })
  it('0/null/非数字返回 null', async () => {
    const { netOfTax } = await loadTax()
    expect(netOfTax(0, 13)).toBeNull()
    expect(netOfTax(null, 13)).toBeNull()
    expect(netOfTax('abc', 13)).toBeNull()
    expect(netOfTax(undefined, 13)).toBeNull()
  })
  it('税率 0 按原价返回（2 位）', async () => {
    const { netOfTax } = await loadTax()
    expect(netOfTax(5, 0)).toBe(5)
    expect(netOfTax(5, null)).toBe(5)
  })
  it('负数照常折算（记录现状行为）', async () => {
    const { netOfTax } = await loadTax()
    expect(netOfTax(-11.3, 13)).toBe(-10)
  })
})

describe('taxOf 税额（单价口径）', () => {
  it('11.3 含税 13% → 税额 1.3', async () => {
    const { taxOf } = await loadTax()
    expect(taxOf(11.3, 13)).toBe(1.3)
    expect(taxOf(100, 13)).toBe(11.5)
  })
  it('无效入参返回 null', async () => {
    const { taxOf } = await loadTax()
    expect(taxOf(0, 13)).toBeNull()
    expect(taxOf(null, 13)).toBeNull()
  })
})

describe('fmtTax 展示', () => {
  it('null/NaN 显示 -', async () => {
    const { fmtTax } = await loadTax()
    expect(fmtTax(null)).toBe('-')
    expect(fmtTax(undefined)).toBe('-')
    expect(fmtTax(NaN)).toBe('-')
  })
  it('数值恒 2 位小数', async () => {
    const { fmtTax } = await loadTax()
    expect(fmtTax(1.3)).toBe('1.30')
    expect(fmtTax(0)).toBe('0.00')
  })
})

describe('loadTaxRate 字典税率', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  it('取字典 tax_rate 首个 value', async () => {
    const { loadTaxRate } = await loadTax()
    const api = { get: vi.fn().mockResolvedValue([{ value: '9' }]) }
    expect(await loadTaxRate(api)).toBe(9)
    expect(api.get).toHaveBeenCalledWith('/dict', { params: { type: 'tax_rate' } })
  })

  it('字典空/非法值回退 13', async () => {
    const { loadTaxRate } = await loadTax()
    expect(await loadTaxRate({ get: vi.fn().mockResolvedValue([]) })).toBe(13)
    expect(await loadTaxRate({ get: vi.fn().mockResolvedValue([{ value: 'abc' }]) })).toBe(13)
    expect(await loadTaxRate({ get: vi.fn().mockResolvedValue([{ value: -1 }]) })).toBe(13)
  })

  it('接口异常回退 13', async () => {
    const { loadTaxRate } = await loadTax()
    expect(await loadTaxRate({ get: vi.fn().mockRejectedValue(new Error('网络异常')) })).toBe(13)
  })

  it('TTL 5 分钟内缓存，过期后重新拉取', async () => {
    vi.useFakeTimers()
    try {
      const { loadTaxRate } = await loadTax()
      const api = { get: vi.fn().mockResolvedValue([{ value: 10 }]) }
      expect(await loadTaxRate(api)).toBe(10)
      expect(await loadTaxRate(api)).toBe(10)
      expect(api.get).toHaveBeenCalledTimes(1)

      vi.advanceTimersByTime(6 * 60 * 1000)   // 超过 5 分钟 TTL
      expect(await loadTaxRate(api)).toBe(10)
      expect(api.get).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })
})
