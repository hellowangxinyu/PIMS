import { ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { downloadFile } from '../utils/download'

/**
 * v5.56 主数据 Excel 导入（供应商/物料/配方共用）
 * 与期初导入同款交互：模板下载 + 选择文件确认导入 + 校验错误清单逐行展示。
 * @param apiPath  模块 API 前缀（如 '/supplier'），模板=GET {apiPath}/import/template，导入=POST {apiPath}/import
 * @param tplFile  模板下载保存的文件名
 * @param entityLabel 实体中文名（用于确认弹窗与成功提示）
 * @param onDone  导入成功后的回调（通常是刷新列表）
 */
export function useExcelImport(apiPath, tplFile, entityLabel, onDone) {
  const importing = ref(false)

  function downloadTpl() {
    downloadFile(`${apiPath}/import/template`, {}, tplFile)
  }

  async function onFile(uploadFile) {
    const file = uploadFile?.raw
    if (!file) return
    try {
      await ElMessageBox.confirm(
        `${entityLabel}导入将新增数据；校验失败不会写入任何数据。确定导入？`,
        `导入${entityLabel}`, { type: 'warning', confirmButtonText: '导入', cancelButtonText: '取消' })
    } catch { return }

    importing.value = true
    try {
      const fd = new FormData()
      fd.append('file', file)
      // 原生 axios：绕过 api 拦截器，自行处理校验错误清单
      const res = await axios.post(`/api${apiPath}/import`, fd, {
        headers: { 'pims-token': localStorage.getItem('pims-token') || '' },
        timeout: 120000
      })
      const d = res.data
      if (d.code === 200) {
        ElMessage.success(`成功导入 ${d.data.count} 条${entityLabel}记录`)
        onDone && onDone()
      } else {
        showErrors(d.msg, d.data)
      }
    } catch (e) {
      const d = e.response?.data
      if (d && d.code !== 200) showErrors(d.msg, d.data)
      else ElMessage.error(d?.msg || '导入失败，请检查网络')
    } finally {
      importing.value = false
    }
  }

  function showErrors(msg, errors) {
    // v6.1 安全：reason 可能回显文件中用户输入的内容，改纯文本渲染防注入
    const lines = Array.isArray(errors) && errors.length
      ? errors.map(x => `第 ${x.row} 行：${x.reason}`).join('\n') : ''
    ElMessageBox.alert(
      (msg ? msg + '\n\n' : '') + lines,
      '导入校验失败', {
        type: 'error',
        customStyle: { whiteSpace: 'pre-wrap', maxHeight: '360px', overflow: 'auto' }
      })
  }

  return { importing, downloadTpl, onFile }
}
