/**
 * v5.73 采购含税口径工具：录入单价默认含税，税率从数据字典 tax_rate 维护（默认 13%）
 * 含税单价 → 不含税 = 含税 ÷ (1 + 税率%)；税额 = 含税 − 不含税
 */
let cachedRate = null
let cachedAt = 0
const CACHE_TTL = 5 * 60 * 1000   // v6.1.6：字典改税率后 5 分钟内生效（原缓存永不过期）

/** 拉取字典税率（百分数，如 13）；失败回退 13 */
export async function loadTaxRate(api) {
  if (cachedRate != null && Date.now() - cachedAt < CACHE_TTL) return cachedRate
  try {
    const list = await api.get('/dict', { params: { type: 'tax_rate' } })
    const v = Number((list || [])[0]?.value)
    cachedRate = Number.isFinite(v) && v >= 0 ? v : 13
    cachedAt = Date.now()
  } catch { cachedRate = 13; cachedAt = Date.now() }
  return cachedRate
}

/** 不含税单价（含税价 null/0 返回 null） */
export function netOfTax(taxPrice, rate) {
  const p = Number(taxPrice)
  if (!Number.isFinite(p) || p === 0) return null
  const r = Number(rate) || 0
  // v6.1.6：舍入到 2 位（原返回浮点长尾如 8.849999999，下游金额累计出现分位误差）
  return r > 0 ? Math.round((p / (1 + r / 100)) * 100) / 100 : Math.round(p * 100) / 100
}

/** 税额（单价口径） */
export function taxOf(taxPrice, rate) {
  const n = netOfTax(taxPrice, rate)
  return n == null ? null : pRound(Number(taxPrice) - n)
}

function pRound(v) { return Math.round(v * 10000) / 10000 }

/** 展示格式：null 显示 '-'，否则两位小数 */
export function fmtTax(v) {
  return v == null || !Number.isFinite(v) ? '-' : v.toFixed(2)
}
