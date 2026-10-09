import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { usePaging } from '../../src/composables/usePaging.js'

function makeList(n) {
  return ref(Array.from({ length: n }, (_, i) => i + 1))
}

describe('usePaging 通用分页', () => {
  it('空列表返回空数组，页码停在 1', () => {
    const { page, pagedRows } = usePaging(ref([]))
    expect(pagedRows.value).toEqual([])
    expect(page.value).toBe(1)
  })

  it('默认每页 25 条', () => {
    const { pageSize, pagedRows } = usePaging(makeList(30))
    expect(pageSize.value).toBe(25)
    expect(pagedRows.value).toHaveLength(25)
  })

  it('翻页切片正确', () => {
    const list = makeList(51)
    const { page, pagedRows } = usePaging(list)
    page.value = 2
    expect(pagedRows.value).toHaveLength(25)
    expect(pagedRows.value[0]).toBe(26)
    page.value = 3
    expect(pagedRows.value).toEqual([51])
  })

  it('翻页超出范围返回空数组（不抛错）', () => {
    const { page, pagedRows } = usePaging(makeList(10))
    page.value = 3
    expect(pagedRows.value).toEqual([])
  })

  it('resetPage 回到第一页', () => {
    const { page, resetPage } = usePaging(makeList(100))
    page.value = 4
    resetPage()
    expect(page.value).toBe(1)
  })

  it('源数据变化后 pagedRows 联动（删除到只剩一页时页码可能悬空）', () => {
    const list = makeList(50)
    const { page, pagedRows } = usePaging(list)
    page.value = 2
    expect(pagedRows.value).toHaveLength(25)
    list.value = list.value.slice(0, 10)
    expect(pagedRows.value).toEqual([])   // 页码 2 已超界——现状行为如实记录
  })

  it('自定义 pageSize 切片正确', () => {
    const list = makeList(25)
    const { page, pageSize, pagedRows } = usePaging(list)
    pageSize.value = 10
    page.value = 3
    expect(pagedRows.value).toEqual([21, 22, 23, 24, 25])
  })
})
