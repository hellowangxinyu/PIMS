// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'

// 每个用例重装模块：api/index.js 模块级有 redirecting 旗标与 inflight 计数，必须隔离
async function loadApi() {
  vi.resetModules()
  const mod = await import('../../src/api/index.js')
  return mod.default
}

function mockResponse(api, body, status = 200) {
  const adapter = vi.fn().mockResolvedValue({ data: body, status, statusText: '', headers: {}, config: {} })
  api.defaults.adapter = adapter
  return adapter
}
function mockHttpError(api, err) {
  const adapter = vi.fn().mockRejectedValue(err)
  api.defaults.adapter = adapter
  return adapter
}

function hrefSpy() {
  const log = []
  vi.stubGlobal('location', {
    set href(v) { log.push(v) },
    get href() { return log[log.length - 1] },
  })
  return log
}

// 等模块加载时的初始 loading 事件（setTimeout tick(0)）先放掉，再开始采集
async function collectLoadingEvents(api) {
  await new Promise(r => setTimeout(r, 0))
  const events = []
  window.addEventListener('pims-loading', e => events.push(e.detail))
  return events
}

describe('api 拦截器（全站共享层）', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.spyOn(ElMessage, 'error').mockImplementation(() => {})
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('code 200 → resolve data.data，请求带 token，loading 先 true 后 false', async () => {
    const api = await loadApi()
    localStorage.setItem('pims-token', 'tk-123')
    const events = await collectLoadingEvents(api)
    const adapter = mockResponse(api, { code: 200, data: { x: 1 } })

    await expect(api.get('/ok')).resolves.toEqual({ x: 1 })

    expect(adapter.mock.calls[0][0].headers['pims-token']).toBe('tk-123')
    expect(events).toEqual([true, false])
  })

  it('code 500 → reject 且 ElMessage.error 带后端 msg', async () => {
    const api = await loadApi()
    mockResponse(api, { code: 500, msg: '库存不足' })
    await expect(api.get('/x')).rejects.toThrow('库存不足')
    expect(ElMessage.error).toHaveBeenCalledWith('库存不足')
  })

  it('code 401 → 清 token/user 并跳 /login', async () => {
    const api = await loadApi()
    const hrefLog = hrefSpy()
    localStorage.setItem('pims-token', 'tk')
    localStorage.setItem('user', JSON.stringify({ username: 'u' }))
    mockResponse(api, { code: 401, msg: '登录过期' })

    await expect(api.get('/x')).rejects.toThrow()

    expect(localStorage.getItem('pims-token')).toBeNull()
    expect(localStorage.getItem('user')).toBeNull()
    expect(hrefLog).toEqual(['/login'])
  })

  it('401 三秒防重：窗口期内重复 401 不再跳，窗口过后恢复', async () => {
    vi.useFakeTimers()
    const api = await loadApi()
    const hrefLog = hrefSpy()
    mockResponse(api, { code: 401, msg: '登录过期' })

    await expect(api.get('/x')).rejects.toThrow()
    await expect(api.get('/x')).rejects.toThrow()
    expect(hrefLog).toEqual(['/login'])

    vi.advanceTimersByTime(3100)
    await expect(api.get('/x')).rejects.toThrow()
    expect(hrefLog).toEqual(['/login', '/login'])
  })

  it('HTTP 状态 401 → 同样清态跳 /login', async () => {
    const api = await loadApi()
    const hrefLog = hrefSpy()
    mockHttpError(api, { response: { status: 401, data: { msg: '未认证' } } })
    await expect(api.get('/x')).rejects.toThrow()
    expect(hrefLog).toEqual(['/login'])
  })

  it('HTTP 500 带 msg → 显示后端 msg；无 response → 兜底"网络异常"', async () => {
    const api = await loadApi()
    mockHttpError(api, { response: { status: 500, data: { msg: '服务端炸了' } } })
    await expect(api.get('/x')).rejects.toThrow()
    expect(ElMessage.error).toHaveBeenCalledWith('服务端炸了')

    mockHttpError(api, new Error('Network Error'))
    await expect(api.get('/y')).rejects.toThrow()
    expect(ElMessage.error).toHaveBeenCalledWith('网络异常')
  })

  it('并发请求 loading 语义：任一在途即 true，全部结束才 false', async () => {
    const api = await loadApi()
    const events = await collectLoadingEvents(api)
    mockResponse(api, { code: 200, data: 1 })
    await Promise.allSettled([api.get('/a'), api.get('/b')])
    // spinner 广播是"任一在途"语义而非每请求成对：
    // reqA(T) reqB(T) resA(仍 true——B 未结束) resB(F)。序列确定，防回归改回"每请求成对"或计数失衡。
    expect(events).toEqual([true, true, true, false])
  })
})
