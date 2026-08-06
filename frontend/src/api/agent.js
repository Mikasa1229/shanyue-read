import http from './http'

export const apiCreateAgentSession = (dto = {}) => http.post('/agent/sessions', dto)
export const apiListAgentSessions = () => http.get('/agent/sessions')
export const apiSearchAgentSessions = (keyword) => http.get('/agent/sessions/search', { params: { keyword } })
export const apiGetAgentMessages = (sessionId) => http.get(`/agent/sessions/${sessionId}/messages`)
export const apiRenameAgentSession = (sessionId, dto) => http.put(`/agent/sessions/${sessionId}/title`, dto)
export const apiUpdateAgentMessage = (sessionId, messageId, dto) => http.put(`/agent/sessions/${sessionId}/messages/${messageId}`, dto)
export const apiExportAgentSession = (sessionId) => http.get(`/agent/sessions/${sessionId}/export`)
export const apiDeleteAgentSession = (sessionId) => http.delete(`/agent/sessions/${sessionId}`)
export const apiListAgentModels = () => http.get('/agent/models')
export const apiSaveAgentModel = (dto) => http.post('/agent/models', dto)
export const apiDeleteAgentModel = (id) => http.delete(`/agent/models/${id}`)
export const apiTestAgentModel = (id) => http.post(`/agent/models/${id}:test`)
export const apiSetAgentModelEnabled = (id, enabled) => http.put(`/agent/models/${id}/enabled`, null, { params: { enabled } })
export const apiGetAgentCredits = () => http.get('/users/me/credits')
export const apiGetAgentInfrastructure = () => http.get('/agent/infrastructure')
export const apiGetAgentAdminOverview = () => http.get('/agent/admin/overview')
export const apiGetAgentAdminJobs = () => http.get('/agent/admin/index-jobs')
export const apiRetryAgentAdminJob = (id) => http.post(`/agent/admin/index-jobs/${id}/retry`)
export const apiRebuildAgentGraph = (bookId) => http.post(`/agent/admin/books/${bookId}/graph:rebuild`)
export const apiGetAgentGraphReviewClaims = (bookId, limit = 30) => http.get(`/agent/admin/books/${bookId}/graph-review-claims`, { params: { limit } })
export const apiReviewAgentGraphClaim = (bookId, dto) => http.post(`/agent/admin/books/${bookId}/graph-review-claims/review`, dto)
export const apiListAgentAdminRoles = () => http.get('/agent/admin/roles')
export const apiSaveAgentAdminRole = (dto) => http.post('/agent/admin/roles', dto)
export const apiDeleteAgentAdminRole = (userId) => http.delete(`/agent/admin/roles/${userId}`)
export const apiListAgentPromptVersions = () => http.get('/agent/admin/prompt-versions')
export const apiCreateAgentPromptVersion = (dto) => http.post('/agent/admin/prompt-versions', dto)
export const apiActivateAgentPromptVersion = (id) => http.post(`/agent/admin/prompt-versions/${id}/activate`)
export const apiListAgentModelRoutes = () => http.get('/agent/admin/model-routes')
export const apiSaveAgentModelRoute = (dto) => http.post('/agent/admin/model-routes', dto)
export const apiListAgentModelPricing = () => http.get('/agent/admin/model-pricing')
export const apiSaveAgentModelPricing = (dto) => http.post('/agent/admin/model-pricing', dto)
export const apiGetAgentUsageSummary = (days = 7) => http.get('/agent/admin/usage-summary', { params: { days } })
export const apiGetAgentUsageBreakdown = (days = 7) => http.get('/agent/admin/usage-breakdown', { params: { days } })
export const apiGetRecommendationExperiment = () => http.get('/agent/admin/recommendation-experiment')
export const apiSaveRecommendationExperiment = (dto) => http.post('/agent/admin/recommendation-experiment', dto)
export const apiGetRecommendationExperimentMetrics = (days = 7) => http.get('/agent/admin/recommendation-experiment/metrics', { params: { days } })
export const apiGetAgentEvaluations = () => http.get('/agent/admin/evaluations')
export const apiGetAgentEvaluationCases = (runId) => http.get(`/agent/admin/evaluations/${runId}/cases`)
export const apiRunAgentPolicyEvaluation = () => http.post('/agent/admin/evaluations/policy-suite')
export const apiRecordAgentAnswerEvaluation = (dto) => http.post('/agent/admin/evaluations/answer-suite', dto)
export const apiGetQuickRecommendations = () => http.post('/agent/quick-recommendations')
export const apiGetAgentReadingPlan = () => http.get('/agent/reading-plan')
export const apiGetAgentShelfGroups = () => http.get('/agent/shelf-groups')
export const apiSaveAgentShelfGroup = (dto) => http.put('/agent/shelf-groups', dto)
export const apiSaveRecommendationFeedback = (dto) => http.post('/agent/recommendations/feedback', dto)
export const apiGetAgentPreferences = () => http.get('/agent/preferences')
export const apiSaveAgentPreferences = (dto) => http.put('/agent/preferences', dto)
export const apiEraseAgentPersonalData = (eraseConversations = true) => http.delete('/agent/preferences/personal-data', { params: { eraseConversations } })
const insightParams = (chapter, spoilersConfirmed = false) => ({ currentChapter: chapter, spoilersConfirmed })
export const apiGetAgentGraph = (bookId, chapter, spoilersConfirmed = false) => http.get(`/agent/books/${bookId}/graph`, { params: insightParams(chapter, spoilersConfirmed) })
export const apiGetAgentClues = (bookId, chapter, spoilersConfirmed = false) => http.get(`/agent/books/${bookId}/clues`, { params: insightParams(chapter, spoilersConfirmed) })
export const apiGetAgentTimeline = (bookId, chapter, spoilersConfirmed = false) => http.get(`/agent/books/${bookId}/timeline`, { params: insightParams(chapter, spoilersConfirmed) })
export const apiGetAgentReadingMap = (bookId, chapter, spoilersConfirmed = false) => http.get(`/agent/books/${bookId}/reading-map`, { params: insightParams(chapter, spoilersConfirmed) })
export const apiGetPlotCapsule = (bookId, chapter, spoilersConfirmed = false) => http.get(`/agent/books/${bookId}/capsule`, { params: insightParams(chapter, spoilersConfirmed) })
export const apiGetSimilarBooks = (bookId, chapter, spoilersConfirmed = false) => http.get(`/agent/books/${bookId}/similar`, { params: insightParams(chapter, spoilersConfirmed) })
export const apiGetAgentReaderLink = (bookId) => http.get(`/agent/books/${bookId}/reader-link`)
export const apiPrepareBookKnowledgeBuild = (bookId, range = {}) => http.get(`/agent/books/${bookId}/knowledge-build:prepare`, { params: range })
export const apiStartBookKnowledgeBuild = (bookId, dto) => http.post(`/agent/books/${bookId}/knowledge-build`, dto)
export const apiGetBookKnowledgeTasks = (limit = 30) => http.get('/agent/knowledge-build/tasks', { params: { limit } })
export const apiDeleteBookKnowledgeTask = (taskId) => http.delete(`/agent/knowledge-build/tasks/${taskId}`)
export const apiGetBookKnowledgeStatuses = (ids) => http.get('/agent/books/knowledge-status', { params: { canonicalBookIds: Array.isArray(ids) ? ids.join(',') : ids } })
export const apiGetBookKnowledgeStatus = (bookId) => http.get(`/agent/books/${bookId}/knowledge-status`)

export async function streamAgentMessage(sessionId, dto, handlers = {}) {
  const token = localStorage.getItem('token')
  const response = await fetch(`/api/agent/sessions/${sessionId}/messages:stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}`, satoken: token } : {})
    },
    body: JSON.stringify(dto)
  })
  if (!response.ok || !response.body) {
    if (response.status === 404) throw new Error('Agent 服务未通过网关连接，请确认后端网关与 Agent 使用同一份配置')
    if (response.status === 401) throw new Error('请先登录后再使用 Agent')
    throw new Error(`Agent 请求失败（${response.status}）`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''
    events.forEach((event) => {
      const eventName = event.match(/^event:\s*(.+)$/m)?.[1]
      const rawData = event.match(/^data:\s*(.+)$/m)?.[1]
      if (!eventName || rawData == null) return
      let data = rawData
      try { data = JSON.parse(rawData) } catch (_) {}
      if (eventName === 'delta') handlers.onDelta?.(typeof data === 'string' ? data : String(data))
      if (eventName === 'tool_status') handlers.onStatus?.(data)
      if (eventName === 'recommendations') handlers.onRecommendations?.(data)
      if (eventName === 'graph') handlers.onGraph?.(data)
      if (eventName === 'done') handlers.onDone?.(data)
      if (eventName === 'error') handlers.onError?.(data)
    })
  }
}
