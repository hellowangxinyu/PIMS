import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

let redirecting = false

api.interceptors.request.use(config => {
  const token = localStorage.getItem('pims-token')
  if (token) config.headers['pims-token'] = token
  return config
})

api.interceptors.response.use(
  res => {
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
