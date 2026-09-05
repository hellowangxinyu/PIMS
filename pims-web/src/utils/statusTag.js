/**
 * 全局状态色语义映射（v6.4 统一规范——此前 22 处逐页手写导致同状态异色误导）
 * 语义：灰=未生效/归档；橙=待办（等处理）；蓝=进行中；绿=完成/通过；红=异常/否决/作废
 */
const MAP = {
  // 通用流转
  DRAFT: 'info',            // 草稿
  PENDING: 'warning',       // 待处理/待检
  APPROVED: 'success',      // 已审核（该环节完成）
  CONFIRMED: 'success',     // 已确认
  CANCELLED: 'danger',      // 作废
  DONE: 'success',          // 完成
  COMPLETED: 'success',
  CLOSED: 'info',           // 已结束（业务收尾，非异常）
  ENABLED: 'success',
  DISABLED: 'info',
  // 生产/委外（进行中=蓝）
  SCHEDULED: 'primary',     // 已排产（进行中）
  OUTSOURCED: 'primary',    // 委外加工中
  PROCESSING: 'primary',
  // 质检
  PASS: 'success',
  CONCESSION: 'warning',    // 让步接收（放行但需关注）
  REJECT: 'danger',
  // 销售/发货
  SHIPPED: 'success',
  // 财务
  UNPAID: 'warning',
  PARTIAL: 'warning',
  PAID: 'success',
  POSTED: 'success',        // 已记账
  FLUSHED: 'info',          // 已红冲
  NORMAL: 'success',
  // 配方
  RELEASED: 'success',
  ARCHIVED: 'info',
  EXPIRED: 'danger',
  RECEIVED: 'success',      // 到齐/收货完成（v6.6 补：采购页在用）
  REJECTED: 'danger',       // 已拒（质检拒收变体，与 REJECT 同义）
  PENDING_QC: 'warning',    // 待质检（待办）
  // 打样（v7.7：ASSIGNED 派发待接收=橙、FORMULATED 已录配方=绿；APPLIED/COLORING 顺手补齐）
  APPLIED: 'info',
  ASSIGNED: 'warning',
  COLORING: 'primary',
  FORMULATED: 'success',
  SATISFIED: 'success',
  ADJUST: 'warning',
  WON: 'success',
  LOST: 'info',
  // 库存
  PASS_QUALIFIED: 'success',
}

/**
 * 状态 → el-tag type。局部业务特殊状态可传 overrides 叠加（同名优先）。
 */
export function statusType(s, overrides) {
  if (s == null) return 'info'
  const key = String(s).toUpperCase()
  if (overrides && Object.prototype.hasOwnProperty.call(overrides, key)) return overrides[key]
  return MAP[key] || 'info'
}
