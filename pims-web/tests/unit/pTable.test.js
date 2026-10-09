// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { h, nextTick } from 'vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/test-route' }) }))

import PTable from '../../src/components/PTable.js'

// 假列组件：injectWidth 只读写 vnode.props，无需真实 el-table-column（其依赖 ElTable provide 上下文）
const Col = (props) => h('div', { class: 'colx', 'data-k': props.prop || props.label, 'data-w': props.width })

// 自定义 ElTable 桩：VTU 默认桩不渲染插槽，这里显式渲染 default 并声明 props 以便取 onHeaderDragend
const ElTableStub = {
  name: 'ElTable',
  inheritAttrs: false,
  props: ['size', 'data', 'border', 'height', 'onHeaderDragend'],
  setup(_props, { slots }) {
    return () => h('div', { class: 'elt' }, slots.default ? slots.default() : [])
  },
}

const loadingDir = { mounted: vi.fn() }

function mountTable(attrs = {}) {
  return mount(PTable, {
    attrs,
    slots: {
      default: () => [
        h(Col, { prop: 'name', label: '名称' }),
        h(Col, { prop: 'qty', label: '数量' }),
      ],
    },
    global: {
      stubs: { ElTable: ElTableStub },
      directives: { loading: loadingDir },
    },
  })
}

function fetchStub(impl) {
  vi.stubGlobal('fetch', vi.fn(impl))
}

describe('PTable 列宽持久化（v6.7 云端 + v7.2 让位）', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('user', JSON.stringify({ username: 'tester' }))
    loadingDir.mounted.mockClear()
    fetchStub(() => Promise.resolve({ json: () => Promise.resolve({ code: 200, data: null }) }))
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('默认 size=small，attrs 透传给 el-table', () => {
    const wrapper = mountTable({ data: [{ a: 1 }], border: true })
    const stub = wrapper.findComponent({ name: 'ElTable' })
    expect(stub.props('size')).toBe('small')
    expect(stub.props('border')).toBe(true)
    expect(stub.props('data')).toEqual([{ a: 1 }])
  })

  it('本机有缓存时按列标识注入 width', () => {
    localStorage.setItem('colw:/test-route', JSON.stringify({ name: 180 }))
    const wrapper = mountTable()
    const cols = wrapper.findAll('.colx')
    expect(cols[0].attributes('data-w')).toBe('180')
    expect(cols[1].attributes('data-w')).toBeUndefined()
  })

  it('header-dragend → 四舍五入写本机 + 立即生效 + 800ms debounce 上行账号级', async () => {
    vi.useFakeTimers()
    const wrapper = mountTable()
    const drag = wrapper.findComponent({ name: 'ElTable' }).props('onHeaderDragend')

    drag(233.6, 100, { property: 'name' })

    expect(JSON.parse(localStorage.getItem('colw:/test-route'))).toEqual({ name: 234 })
    await nextTick()
    expect(wrapper.findAll('.colx')[0].attributes('data-w')).toBe('234')   // 响应式即时生效

    vi.advanceTimersByTime(800)
    const post = window.fetch.mock.calls.find(c => c[1] && c[1].method === 'POST')
    expect(post, '未上行列宽').toBeTruthy()
    expect(String(post[0])).toContain('/api/ui-config')
    const body = JSON.parse(post[1].body)
    expect(body.key).toBe('pims.ui.cols.tester./test-route')
    expect(body.value).toBe('{"name":234}')
  })

  it('操作列与无标识列不参与持久化', () => {
    const wrapper = mountTable()
    const drag = wrapper.findComponent({ name: 'ElTable' }).props('onHeaderDragend')

    drag(300, 100, { label: '操作' })
    drag(300, 100, {})
    expect(localStorage.getItem('colw:/test-route')).toBeNull()
  })

  it('让位模式（页面自带 onHeaderDragend）：不恢复、不写存、不上行，但透传调用页面 handler', async () => {
    const pageHandler = vi.fn()
    const wrapper = mountTable({ onHeaderDragend: pageHandler })
    await Promise.resolve()   // 让潜在的恢复 fetch 兑现

    expect(window.fetch, '让位页面不应发恢复请求').not.toHaveBeenCalled()

    const drag = wrapper.findComponent({ name: 'ElTable' }).props('onHeaderDragend')
    drag(300, 100, { property: 'name' })
    expect(pageHandler).toHaveBeenCalledWith(300, 100, { property: 'name' })
    expect(localStorage.getItem('colw:/test-route')).toBeNull()
  })

  it('本机无缓存时服务端配置回填本机并生效', async () => {
    fetchStub(() => Promise.resolve({ json: () => Promise.resolve({ code: 200, data: '{"name":222}' }) }))
    const wrapper = mountTable()
    await vi.waitFor(() => {
      expect(wrapper.findAll('.colx')[0].attributes('data-w')).toBe('222')
    })
    expect(localStorage.getItem('colw:/test-route')).toBe('{"name":222}')
  })

  it('本机已有缓存时服务端响应不覆盖（本机优先）', async () => {
    localStorage.setItem('colw:/test-route', JSON.stringify({ name: 111 }))
    fetchStub(() => Promise.resolve({ json: () => Promise.resolve({ code: 200, data: '{"name":222}' }) }))
    const wrapper = mountTable()
    await Promise.resolve()
    await Promise.resolve()
    expect(wrapper.findAll('.colx')[0].attributes('data-w')).toBe('111')
  })

  it('卸载后迟到的服务端响应被丢弃（alive 标志，防跨页污染）', async () => {
    let resolveJson
    fetchStub(() => new Promise(res => { resolveJson = () => res({ json: () => Promise.resolve({ code: 200, data: '{"name":999}' }) }) }))
    const wrapper = mountTable()
    wrapper.unmount()
    resolveJson()
    await Promise.resolve()
    await Promise.resolve()
    expect(localStorage.getItem('colw:/test-route')).toBeNull()
  })

  it('loading 属性挂 v-loading 指令并传递布尔值', () => {
    mountTable({ loading: true })
    expect(loadingDir.mounted).toHaveBeenCalled()
    expect(loadingDir.mounted.mock.calls[0][1].value).toBe(true)
  })

  it('无 loading 属性不挂指令', () => {
    mountTable({})
    expect(loadingDir.mounted).not.toHaveBeenCalled()
  })
})
