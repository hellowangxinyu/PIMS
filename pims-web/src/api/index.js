import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

let redirecting = false

// v6.4 全局加载指示：请求计数广播（顶栏小 spinner 一处生效，所有请求自动有反馈）
let inflight = 0
function tick(delta) {
  inflight = Math.max(0, inflight + delta)
  window.dispatchEvent(new CustomEvent('pims-loading', { detail: inflight > 0 }))
}
setTimeout(() => tick(0), 0)   // 初始同步一次

api.interceptors.request.use(config => {
  const token = localStorage.getItem('pims-token')
  if (token) config.headers['pims-token'] = token
  tick(1)
  return config
})

api.interceptors.response.use(
  res => {
    tick(-1)
    const data = res.data
    if (data.code !== 200) {
      if (data.code === 401 || data.code == 401) {
        if (!redirecting) {
          redirecting = true
          localStorage.removeItem('pims-token')
          localStorage.removeItem('user')
          window.location.href = '/login'
          setTimeout(() => { redirecting = false }, 3000)
        }
      } else {
        ElMessage.error(data.msg || '请求失败')
      }
      return Promise.reject(new Error(data.msg || '请求失败'))
    }
    return data.data
  },
  err => {
    tick(-1)
    if (err.response && (err.response.status === 401 || err.response.status == 401)) {
      if (!redirecting) {
        redirecting = true
        localStorage.removeItem('pims-token')
        localStorage.removeItem('user')
        window.location.href = '/login'
        setTimeout(() => { redirecting = false }, 3000)
      }
    } else {
      const msg = err.response?.data?.msg || '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default api
