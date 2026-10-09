// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

vi.mock('axios', () => ({ default: { post: vi.fn() } }))

import { useExcelImport } from '../../src/composables/useExcelImport.js'

const UPLOAD = { raw: new File(['x'], 'test.xlsx') }

describe('useExcelImport 主数据导入', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.spyOn(ElMessageBox, 'alert').mockResolvedValue()
    vi.spyOn(ElMessage, 'success').mockImplementation(() => {})
    vi.spyOn(ElMessage, 'error').mockImplementation(() => {})
  })

  it('用户取消确认 → 不发请求、不回调', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue(new Error('cancel'))
    const onDone = vi.fn()
    const { onFile } = useExcelImport('/supplier', 'tpl.xlsx', '供应商', onDone)

    await onFile(UPLOAD)

    expect(axios.post).not.toHaveBeenCalled()
    expect(onDone).not.toHaveBeenCalled()
  })

  it('确认导入 → POST /api{path}/import，带 token 与 120s 超时', async () => {
    localStorage.setItem('pims-token', 'tk-9')
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue()
    axios.post.mockResolvedValue({ data: { code: 200, data: { count: 3 } } })
    const onDone = vi.fn()
    const { onFile, importing } = useExcelImport('/supplier', 'tpl.xlsx', '供应商', onDone)

    await onFile(UPLOAD)

    expect(axios.post).toHaveBeenCalledWith(
      '/api/supplier/import',
      expect.any(FormData),
      expect.objectContaining({ headers: { 'pims-token': 'tk-9' }, timeout: 120000 }),
    )
    expect(importing.value).toBe(false)
  })

  it('导入成功 → 提示条数并回调 onDone', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue()
    axios.post.mockResolvedValue({ data: { code: 200, data: { count: 3 } } })
    const onDone = vi.fn()
    const { onFile } = useExcelImport('/material', 'tpl.xlsx', '物料', onDone)

    await onFile(UPLOAD)

    expect(ElMessage.success).toHaveBeenCalledWith(expect.stringContaining('3'))
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('校验失败 → 逐行错误清单（含行号与原样 reason），不回调', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue()
    axios.post.mockResolvedValue({
      data: { code: 400, msg: '校验未通过', data: [{ row: 2, reason: '物料编码必填' }, { row: 5, reason: '<b>异常</b>' }] },
    })
    const onDone = vi.fn()
    const { onFile } = useExcelImport('/material', 'tpl.xlsx', '物料', onDone)

    await onFile(UPLOAD)

    expect(ElMessageBox.alert).toHaveBeenCalledTimes(1)
    const msg = ElMessageBox.alert.mock.calls[0][0]
    expect(msg).toContain('校验未通过')
    expect(msg).toContain('第 2 行：物料编码必填')
    expect(msg).toContain('第 5 行：<b>异常</b>')   // v6.1 防注入：reason 原样纯文本
    expect(onDone).not.toHaveBeenCalled()
  })

  it('HTTP 异常且 body 带校验清单 → 展示清单；裸网络异常 → 兜底提示', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue()
    axios.post.mockRejectedValue({ response: { data: { code: 400, msg: '失败', data: [{ row: 1, reason: '格式错' }] } } })
    const { onFile } = useExcelImport('/supplier', 'tpl.xlsx', '供应商', () => {})
    await onFile(UPLOAD)
    expect(ElMessageBox.alert).toHaveBeenCalledWith(expect.stringContaining('第 1 行：格式错'), '导入校验失败', expect.anything())

    vi.clearAllMocks()
    axios.post.mockRejectedValue(new Error('net down'))
    const { onFile: onFile2 } = useExcelImport('/supplier', 'tpl.xlsx', '供应商', () => {})
    await onFile2(UPLOAD)
    expect(ElMessage.error).toHaveBeenCalledWith('导入失败，请检查网络')
  })
})
