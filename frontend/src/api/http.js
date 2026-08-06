import axios from 'axios'

// 将 JSON 中大整数 ID 字段转为字符串，防止 JS Number 精度丢失
function safeParseBigIds(text) {
  return text.replace(/"(id|sourceId)"\s*:\s*(\d{16,})/g, '"$1":"$2"')
}

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
  transformResponse: [
    (data) => {
      if (typeof data === 'string') {
        try { return JSON.parse(safeParseBigIds(data)) } catch { return data }
      }
      return data
    }
  ]
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
    const requestUrl = err.config?.url || ''
    const status = err.response?.status
    const responseBody = err.response?.data
    // The Agent security filter deliberately emits this exact opaque response when
    // a request bypasses the trusted gateway. Other 404s are ordinary missing
    // resources and must not be misreported as a gateway configuration failure.
    const gatewayRejected = requestUrl.includes('/agent') && status === 404 &&
      responseBody?.code === 404 && responseBody?.message === 'Not found'
    const msg = gatewayRejected
      ? 'Agent 服务未通过网关连接，请确认网关与 Agent 使用同一份 AGENT_GATEWAY_TOKEN 配置并重启服务'
      : responseBody?.message || err.message || '网络异常'
    return Promise.reject(new Error(msg))
  }
)

export default http
