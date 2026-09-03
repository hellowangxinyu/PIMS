/**
 * 全局数字/金额格式（v6.4 统一——此前千分位派与 toFixed 裸写派并存，财务场景刺眼）
 * 金额一律千分位 + 2 位小数：¥12,345.67
 */

/** 金额（千分位 2 位） */
export function fmtMoney(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 数量（千分位最多 3 位小数，去尾零：1,200 / 1,200.5） */
export function fmtQty(v) {
  return Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 3 })
}

/** 别名：各页惯用的 fmt 即金额格式 */
export const fmt = fmtMoney
