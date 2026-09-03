// 文件下载工具（v5.23 Excel 导出用）
// 必须绕过 src/api/index.js 的 JSON 剥壳拦截器：blob 响应没有 code 字段，
// 会被 `data.code !== 200` 误判 reject。这里用原生 axios 携带 pims-token，
// responseType: 'blob' 直接下载，并对后端错误响应（JSON blob）解析出 msg 提示。
import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 下载后端导出的 Excel 文件
 * @param {string} url 接口路径（不含 /api 前缀，如 /inventory/export）
 * @param {object} params 查询参数（当前列表筛选条件）
 * @param {string} filename 下载文件名（含 .xlsx）
 */
export function downloadFile(url, params = {}, filename = '导出.xlsx') {
  const token = localStorage.getItem('pims-token')
  return axios.get(url, {
    baseURL: '/api',
    params,
    responseType: 'blob',
    timeout: 60000,
    headers: token ? { 'pims-token': token } : {}
  }).then(res => {
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
    ElMessage.success('导出成功')
  }).catch(err => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('pims-token')
      localStorage.removeItem('user')
      window.location.href = '/login'
      return Promise.reject(err)
    }
    const data = err.response?.data
    if (data && data instanceof Blob) {
      // 后端业务错误以 JSON 返回，但被 blob 接住，需解析出 msg
      data.text().then(t => {
        try {
          const j = JSON.parse(t)
          ElMessage.error(j.msg || '导出失败')
        } catch {
          ElMessage.error('导出失败')
        }
      })
    } else {
      ElMessage.error(err.response?.data?.msg || '导出失败，请检查网络')
    }
    return Promise.reject(err)
  })
}
