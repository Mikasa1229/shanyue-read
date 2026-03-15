import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
    config.headers['satoken'] = token
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data.code === 200) return data.data
    if (data.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
    }
    return Promise.reject(new Error(data.message || '请求失败'))
  },
  (err) => {
    const msg = err.response?.data?.message || err.message || '网络异常'
    return Promise.reject(new Error(msg))
  }
)

export default http
