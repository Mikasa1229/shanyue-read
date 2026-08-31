import axios from 'axios'

// Stored media URLs from local deployments may contain localhost. Resolve them
// through the current frontend host so local Vite and remote Nginx use the same path.
export function normalizeAssetUrl(value) {
  if (typeof value !== 'string') return value
  return value.replace(/^https?:\/\/(?:localhost|127\.0\.0\.1)(?::9000)?(\/reader-assets\/)/i, '$1')
}

function normalizeAssetUrls(value) {
  if (typeof value === 'string') return normalizeAssetUrl(value)
  if (Array.isArray(value)) return value.map(normalizeAssetUrls)
  if (value && typeof value === 'object') {
    Object.keys(value).forEach((key) => { value[key] = normalizeAssetUrls(value[key]) })
  }
  return value
}

// 将 JSON 中大整数 ID 字段转为字符串，防止 JS Number 精度丢失
function safeParseBigIds(text) {
  // Graph edge endpoints are Snowflake IDs too. Preserve them before JSON parsing.
  return text.replace(/"(id|source|target|sourceId|targetId|sourceNodeId|targetNodeId|canonicalBookId)"\s*:\s*(\d{16,})/g, '"$1":"$2"')
}

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
  transformResponse: [
    (data) => {
      if (typeof data === 'string') {
        try { return normalizeAssetUrls(JSON.parse(safeParseBigIds(data))) } catch { return data }
      }
      return normalizeAssetUrls(data)
    }
  ]
})

export function getAuthHeaders() {
  const storedToken = localStorage.getItem('token')?.trim()
  if (!storedToken) return {}

  // Login responses store a bare token, but normalize legacy values that
  // already include the scheme so requests never become "Bearer Bearer ...".
  const token = storedToken.replace(/^Bearer\s+/i, '')
  return { Authorization: `Bearer ${token}` }
}

function bearerToken(value) {
  return typeof value === 'string' ? value.trim().replace(/^Bearer\s+/i, '') : ''
}

function requestToken(config) {
  const headers = config?.headers
  const authorization = typeof headers?.get === 'function'
    ? headers.get('Authorization')
    : headers?.Authorization || headers?.authorization
  return bearerToken(authorization)
}

let redirectingToLogin = false

function handleUnauthorized(config) {
  const failedToken = requestToken(config)
  const currentToken = bearerToken(localStorage.getItem('token'))

  // An older request may finish after a new login. It must never erase the
  // newly issued token or send the user back into a login redirect loop.
  if (failedToken && currentToken && failedToken !== currentToken) return

  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')

  if (!window.location.pathname.startsWith('/login') && !redirectingToLogin) {
    redirectingToLogin = true
    const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
    window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)
  }
}

http.interceptors.request.use((config) => {
  if (config.skipAuth) {
    // Login/register must not carry an expired session token. Sa-Token may
    // otherwise reuse the incoming token context while issuing the session.
    delete config.headers?.Authorization
    delete config.headers?.authorization
    delete config.headers?.satoken
    return config
  }

  config.headers = { ...getAuthHeaders(), ...config.headers }
  return config
})

http.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data.code === 200) return data.data
    if (data.code === 401) {
      handleUnauthorized(res.config)
    }
    return Promise.reject(new Error(data.message || '请求失败'))
  },
  (err) => {
    const requestUrl = err.config?.url || ''
    const status = err.response?.status
    const responseBody = err.response?.data
    if (status === 401) {
      handleUnauthorized(err.config)
      return Promise.reject(new Error(responseBody?.message || '登录已过期，请重新登录'))
    }
    // The Agent security filter deliberately emits this exact opaque response when
    // a request bypasses the trusted gateway. Other 404s are ordinary missing
    // resources and must not be misreported as a gateway configuration failure.
    const gatewayRejected = requestUrl.includes('/agent') && status === 404 &&
      responseBody?.code === 404 && responseBody?.message === 'Not found'
    const timeoutMessage = err.code === 'ECONNABORTED' || err.code === 'ETIMEDOUT'
      ? '搜索服务响应超时，请稍后重试或检查已启用书源'
      : null
    const msg = timeoutMessage
      || (gatewayRejected
        ? 'Agent 服务未通过网关连接，请确认网关与 Agent 使用同一份 AGENT_GATEWAY_TOKEN 配置并重启服务'
        : responseBody?.message || err.message || '网络异常')
    return Promise.reject(new Error(msg))
  }
)

export default http
