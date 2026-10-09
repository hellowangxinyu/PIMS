import { describe, expect, it } from 'vitest'
import { statusType } from '../../src/utils/statusTag.js'

// 语义对照表（与文件头注释口径一致：灰=info 橙=warning 蓝=primary 绿=success 红=danger）
const SEMANTICS = [
  // 灰：未生效/归档
  ['DRAFT', 'info'], ['CLOSED', 'info'], ['DISABLED', 'info'], ['ARCHIVED', 'info'],
  ['FLUSHED', 'info'], ['LOST', 'info'], ['APPLIED', 'info'],
  // 橙：待办/需关注
  ['PENDING', 'warning'], ['PENDING_QC', 'warning'], ['CONCESSION', 'warning'],
  ['UNPAID', 'warning'], ['PARTIAL', 'warning'], ['ASSIGNED', 'warning'], ['ADJUST', 'warning'],
  // 蓝：进行中
  ['SCHEDULED', 'primary'], ['OUTSOURCED', 'primary'], ['PROCESSING', 'primary'], ['COLORING', 'primary'],
  // 绿：完成/通过
  ['APPROVED', 'success'], ['CONFIRMED', 'success'], ['DONE', 'success'], ['COMPLETED', 'success'],
  ['ENABLED', 'success'], ['PASS', 'success'], ['SHIPPED', 'success'], ['PAID', 'success'],
  ['POSTED', 'success'], ['NORMAL', 'success'], ['RELEASED', 'success'], ['RECEIVED', 'success'],
  ['FORMULATED', 'success'], ['SATISFIED', 'success'], ['WON', 'success'], ['PASS_QUALIFIED', 'success'],
  // 红：异常/否决/作废
  ['CANCELLED', 'danger'], ['REJECT', 'danger'], ['REJECTED', 'danger'], ['EXPIRED', 'danger'],
]

describe('statusType 状态色语义', () => {
  it.each(SEMANTICS)('%s → %s', (status, expected) => {
    expect(statusType(status)).toBe(expected)
  })

  it('大小写不敏感', () => {
    expect(statusType('draft')).toBe('info')
    expect(statusType('Approved')).toBe('success')
    expect(statusType('pending_qc')).toBe('warning')
  })

  it('null/undefined/未知状态兜底 info（SOP 铁律 10：显示异常要可见而非炸）', () => {
    expect(statusType(null)).toBe('info')
    expect(statusType(undefined)).toBe('info')
    expect(statusType('')).toBe('info')
    expect(statusType('FOOBAR')).toBe('info')
  })

  it('overrides 局部覆盖同名状态', () => {
    expect(statusType('DRAFT', { DRAFT: 'danger' })).toBe('danger')
  })

  it('overrides 不影响其他状态', () => {
    expect(statusType('PASS', { DRAFT: 'danger' })).toBe('success')
  })
})
