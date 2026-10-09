import { describe, expect, it } from 'vitest'
import { APP_VERSION } from '../../src/utils/version.js'

describe('APP_VERSION 版本注入', () => {
  it('vitest 未走 vite.config.js 注入 → 回退 dev（不抛 ReferenceError）', () => {
    expect(APP_VERSION).toBe('dev')
  })
})
