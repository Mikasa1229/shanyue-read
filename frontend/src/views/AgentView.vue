<template>
  <main class="agent-center page">
    <div class="container">
      <header class="agent-hero">
        <div>
          <span class="agent-eyebrow">SHANYUE INTELLIGENCE</span>
          <h1>你的阅读副驾驶</h1>
          <p>在这里继续长对话、管理模型和积分，并探索书籍洞察能力。</p>
        </div>
        <div class="credit-card">
          <span>可用积分</span>
          <strong>{{ credits?.availableCredits ?? '--' }}</strong>
          <small>平台模型调用消耗积分；自配模型不扣平台积分</small>
        </div>
      </header>
      <p v-if="loadNotice" class="agent-load-notice">{{ loadNotice }}</p>

      <nav class="agent-tabs">
        <button v-for="tab in tabs" :key="tab.id" :class="{ active: activeTab === tab.id }" @click="activeTab = tab.id">{{ tab.label }}</button>
      </nav>

      <section v-if="activeTab === 'chats'" class="agent-workbench">
        <aside class="session-list card">
          <button class="new-session" @click="newSession">+ 新建对话</button>
          <input v-model="sessionSearch" class="session-search" placeholder="Search conversations" @input="searchSessions" />
          <button v-for="session in sessions" :key="session.id" :class="{ selected: session.id === activeSession?.id }" @click="selectSession(session)">{{ session.title }}</button>
        </aside>
        <section class="chat-pane card">
          <template v-if="activeSession">
            <div class="chat-actions"><button class="btn btn-ghost btn-sm" @click="exportConversation">Export JSON</button><button class="btn btn-ghost btn-sm" @click="deleteConversation">Delete conversation</button></div>
            <div class="chat-history">
              <article v-for="message in messages" :key="message.id" :class="['center-message', message.role === 'USER' ? 'user' : 'assistant']"><span>{{ message.content }}</span><small v-if="message.role === 'ASSISTANT' && isInterviewResponse(message.content)" class="interview-contract">角色访谈：原文事实、推断与未知内容已分区。</small><div v-if="citationItems(message).length" class="citation-list"><button v-for="citation in citationItems(message)" :key="`${citation.canonicalBookId}-${citation.chapterIndex}-${citation.excerpt}`" class="citation-link" @click="openCitation(citation)">Source: Ch. {{ citation.chapterIndex + 1 }} - {{ citation.excerpt }}</button></div></article>
            </div>
            <form class="center-input" @submit.prevent="send">
              <textarea v-model="draft" rows="3" placeholder="继续和阅见助手聊聊…" @keydown.enter.exact.prevent="send" />
              <label class="chat-model-select">模型
                <select v-model="selectedModelConfigId">
                  <option value="">平台模型（消耗积分）</option>
                  <option v-for="model in enabledModels" :key="model.id" :value="String(model.id)">{{ model.provider }} · {{ model.model }}</option>
                </select>
              </label>
              <button class="btn btn-gold" :disabled="sending || !draft.trim()">{{ sending ? '思考中…' : '发送' }}</button>
            </form>
          </template>
          <div v-else class="empty-state">创建一段对话，开始整理你的阅读世界。</div>
        </section>
      </section>

      <section v-else-if="activeTab === 'models'" class="model-layout">
        <article class="card model-intro">
          <span class="agent-eyebrow">MODEL ACCESS</span>
          <h2>模型与试用额度</h2>
          <p>平台模型使用积分。你也可以添加自己的 DeepSeek 或 OpenAI-compatible Key，Key 仅以加密形式保存在服务端。</p>
          <ul>
            <li>新账号拥有 3 次平台标准调用体验额度。</li>
            <li>签到和有效阅读可以获得后续积分。</li>
            <li>切换自配模型后，不消耗平台模型积分。</li>
          </ul>
          <p v-if="infrastructure" class="text-muted">RAG: {{ infrastructure.milvusEnabled ? 'Milvus enabled' : 'local vector fallback' }} + {{ infrastructure.elasticsearchEnabled ? 'Elasticsearch lexical' : 'local lexical fallback' }} · Graph: {{ infrastructure.neo4jEnabled ? 'Neo4j enabled' : 'local fallback' }}{{ infrastructure.graphLlmEnabled ? ' + structured extraction' : '' }}</p>
        </article>
        <form class="card model-form" @submit.prevent="saveModel">
          <h2>添加自配模型</h2>
          <label>供应商<select v-model="modelForm.provider"><option value="deepseek">DeepSeek</option><option value="openai">OpenAI</option></select></label>
          <label>模型名称<input v-model="modelForm.model" required placeholder="deepseek-chat" /></label>
          <label>兼容 API 地址（可选）<input v-model="modelForm.baseUrl" maxlength="512" type="url" placeholder="https://api.deepseek.com" /></label>
          <label>API Key<input v-model="modelForm.apiKey" required type="password" autocomplete="new-password" placeholder="仅在保存时提交" /></label>
          <button class="btn btn-primary">加密保存</button>
        </form>
        <article class="card saved-models">
          <h2>已保存模型</h2>
          <p v-if="!models.length" class="text-muted">还没有保存个人模型。</p>
          <div v-for="model in models" :key="model.id" class="saved-model"><span><strong>{{ model.provider }}</strong> · {{ model.model }}<small>{{ model.keyHint }} · {{ model.enabled ? '已启用' : '已停用' }}</small></span><div class="model-actions"><button class="btn btn-ghost btn-sm" :disabled="testingModelId === model.id" @click="testModel(model.id)">{{ testingModelId === model.id ? '测试中…' : '测试' }}</button><button class="btn btn-ghost btn-sm" @click="toggleModel(model)">{{ model.enabled ? '停用' : '启用' }}</button><button class="btn btn-ghost btn-sm" @click="removeModel(model.id)">删除</button></div></div>
        </article>
      </section>

      <section v-else-if="activeTab === 'insights'" class="insights-panel">
        <form class="card insight-query" @submit.prevent="loadInsights">
          <div><span class="agent-eyebrow">READING-SAFE INSIGHTS</span><h2>Book insights</h2><p>Only indexed chapters up to your reading progress are queried.</p></div>
          <label>Book ID<input v-model="insightBookId" required inputmode="numeric" placeholder="canonicalBookId" /></label>
          <label>Read chapter<input v-model.number="insightChapter" required type="number" min="1" /></label>
          <button class="btn btn-gold" :disabled="insightLoading">{{ insightLoading ? 'Loading...' : 'Analyze' }}</button>
        </form>
        <div v-if="insightLoaded" class="insight-grid">
          <article class="insight-card card"><span>00</span><h2>Plot recap capsule</h2><p v-if="!capsule?.timeline?.length">No indexed reading-safe recap is available yet.</p><ul v-else><li v-for="item in capsule.timeline.slice(0, 4)" :key="item">{{ item }} <button class="evidence-jump" @click="openInsightChapter(timelineChapter(item))">Open evidence</button></li></ul><small>{{ capsule?.safetyNote }}</small></article>
          <article class="insight-card card graph-card"><span>01</span><h2>Character graph</h2><p v-if="!graph.nodes?.length">No verified relationship is visible yet.</p><template v-else><div class="graph-tools"><button v-for="type in graphTypes" :key="type" :class="{ active: graphTypeFilter === type }" @click="graphTypeFilter = type">{{ type === 'ALL' ? 'All' : type }}</button></div><svg class="relationship-map" viewBox="0 0 360 230" role="img" aria-label="Reading-safe character relationship graph"><line v-for="edge in visibleGraphEdges" :key="`${edge.source}-${edge.target}-${edge.relation}`" :class="edge.confidence < 0.7 ? 'tentative' : ''" :x1="graphPoint(graphNodeIndex(edge.source)).x" :y1="graphPoint(graphNodeIndex(edge.source)).y" :x2="graphPoint(graphNodeIndex(edge.target)).x" :y2="graphPoint(graphNodeIndex(edge.target)).y" @click="selectGraphEvidence(edge, 'EDGE')" /><g v-for="(node, index) in visibleGraphNodes" :key="node.id" :transform="`translate(${graphPoint(index).x} ${graphPoint(index).y})`" @click="selectGraphEvidence(node, 'NODE')"><circle :class="node.type === 'CHARACTER' ? 'character' : 'other'" r="20" /><text y="4">{{ node.name.slice(0, 4) }}</text></g></svg><div v-if="selectedGraphEvidence" class="graph-evidence"><b>{{ selectedGraphEvidence.label }}</b><span>Ch. {{ selectedGraphEvidence.chapter + 1 }} / {{ Math.round((selectedGraphEvidence.confidence || 0) * 100) }}% confidence</span><p>{{ selectedGraphEvidence.evidence || 'No readable evidence excerpt is available.' }}</p><button class="evidence-jump" @click="openInsightChapter(selectedGraphEvidence.chapter)">Open evidence</button></div><ul><li v-for="node in visibleGraphNodes.slice(0, 6)" :key="node.id"><span>{{ node.name }} · {{ node.type }} · Ch. {{ node.firstChapter + 1 }}</span><button class="evidence-jump" @click="openInsightChapter(node.firstChapter)">Evidence</button><button v-if="node.type === 'CHARACTER'" class="character-interview" @click="startCharacterInterview(node)">Interview</button></li></ul></template><small>{{ graph.edges?.length || 0 }} visible links, clipped to your reading progress; dashed links have lower confidence.</small></article>
          <article class="insight-card card"><span>02</span><h2>Clue radar</h2><p v-if="!clues.length">No strong clue signal has been found in this range.</p><ul v-else><li v-for="clue in clues.slice(0, 5)" :key="`${clue.chapterIndex}-${clue.excerpt}`">Ch. {{ clue.chapterIndex + 1 }} · {{ clue.excerpt }} <em class="clue-status">{{ clue.status }}</em><button class="evidence-jump" @click="openInsightChapter(clue.chapterIndex)">Evidence</button></li></ul><small>Extracted from readable chapters only; later resolutions stay hidden until read.</small></article>
          <article class="insight-card card reading-map-card"><span>03</span><h2>Reading map</h2><p v-if="!readingMap.events?.length">Read and index chapters to build an event map.</p><template v-else><div class="graph-tools"><button v-for="branch in readingMapBranches" :key="branch" :class="{ active: readingMapBranch === branch }" @click="readingMapBranch = branch">{{ branch === 'ALL' ? 'All threads' : branch }}</button></div><ol class="reading-map-events"><li v-for="event in visibleReadingMapEvents" :key="event.id"><strong>Ch. {{ event.chapterIndex + 1 }} · {{ event.name }}</strong><em class="clue-status">{{ event.branch }}</em><p>{{ event.evidence || 'No readable event evidence is available.' }}</p><small>{{ Math.round((event.confidence || 0) * 100) }}% confidence · {{ eventLinkCount(event.id) }} visible links</small><button class="evidence-jump" @click="openInsightChapter(event.chapterIndex)">Open evidence</button></li></ol></template><small>{{ visibleReadingMapLinks.length }} causal links, clipped to your reading progress</small></article>
          <article class="insight-card card"><span>04</span><h2>Similar-book DNA</h2><p v-if="!similarBooks.length">There are not enough indexed, readable works to compare yet.</p><ul v-else><li v-for="book in similarBooks" :key="book.canonicalBookId"><strong>{{ book.title || `Work #${book.canonicalBookId}` }}</strong><span v-if="book.author"> · {{ book.author }}</span> · {{ Math.round(book.similarity * 100) }}%<br />{{ book.explanation }} <button class="btn btn-ghost btn-sm" @click="openRecommendedBook(book)">Open</button></li></ul><small>Based on indexed text features, not invented tags</small></article>
          <article class="insight-card card"><span>05</span><h2>Dynamic shelf manager</h2><p v-if="!shelfRecommendations.length">Your shelf is ready for its first reading signal.</p><ul v-else><li v-for="item in shelfRecommendations" :key="item.title"><strong>{{ item.title }}</strong><br />{{ item.reason }}<span v-if="item.canonicalBookId" class="recommendation-actions"><button @click="saveRecommendationFeedback(item, 'OPEN')">Open</button><button @click="saveRecommendationFeedback(item, 'LIKE')">Useful</button><button @click="confirmAddToShelf(item)">Add to shelf</button><button @click="saveRecommendationFeedback(item, 'DISMISS')">Hide</button></span></li></ul><small>Adding a work always requires your explicit confirmation.</small></article>
          <article class="insight-card card reading-plan-card"><span>06</span><h2>Reading plan</h2><p>{{ readingPlan?.summary || 'Loading a plan from your verified shelf activity…' }}</p><ul v-if="readingPlan?.items?.length"><li v-for="item in readingPlan.items" :key="item.canonicalBookId"><strong>{{ item.title }}</strong><br />Today: {{ item.suggestedChaptersToday }} chapter{{ item.suggestedChaptersToday === 1 ? '' : 's' }} · currently at Ch. {{ item.currentChapter + 1 }}<span v-if="item.totalChapters"> / {{ item.totalChapters }}</span><br /><small>{{ item.reason }}</small></li></ul><small>Rule-based and spoiler-safe; it never reads ahead or sends shelf content to a model.</small></article>
          <article class="insight-card card shelf-groups-card"><span>07</span><h2>Shelf views</h2><p v-if="!shelfGroups.length">No readable shelf entries are available for grouping yet.</p><ul v-else><li v-for="item in shelfGroups" :key="item.canonicalBookId"><strong>{{ item.title }}</strong><label><select :value="item.groupCode" @change="saveShelfGroup(item, $event.target.value)"><option value="FOLLOWING">Following</option><option value="SHORT_SESSION">Short session</option><option value="WEEKEND">Weekend immersion</option><option value="RESTART">Restart</option><option value="CLEANUP">Consider cleanup</option><option value="AUTO">Automatic</option></select></label><small>{{ item.pinned ? 'Manually fixed; choose Automatic to resume safe suggestions.' : 'Suggested from safe shelf activity only.' }}</small></li></ul><small>This only changes the Agent view and ranking hint; it never moves or removes a shelf item.</small></article>
        </div>
      </section>

      <section v-else class="privacy-layout">
        <form class="card preference-form" @submit.prevent="savePreferences">
          <span class="agent-eyebrow">PREFERENCES & PRIVACY</span>
          <h2>Reading preferences</h2>
          <label>Preferred genres <input v-model="preferenceGenres" maxlength="160" placeholder="Fantasy, mystery, romance" /></label>
          <label>Avoided themes <input v-model="avoidedThemes" maxlength="160" placeholder="e.g. horror" /></label>
          <label>Spoiler boundary <select v-model="preferences.spoilerLevel"><option value="STRICT">Strict: only completed chapters</option><option value="STANDARD">Standard: completed chapters with broader discussion</option></select></label>
          <label class="switch-line"><input v-model="preferences.personalizationEnabled" type="checkbox" /> Use preferences for recommendations</label>
          <label class="switch-line"><input v-model="preferences.retainConversations" type="checkbox" /> Keep conversations in Agent history</label>
          <button class="btn btn-primary">Save preferences</button>
        </form>
        <article class="card privacy-danger"><h2>Personal data</h2><p>Erase Agent preferences now. You can also erase all Agent conversations; this cannot be undone.</p><label class="switch-line"><input v-model="eraseConversations" type="checkbox" /> Also erase conversations</label><button class="btn btn-ghost" @click="erasePersonalData">Erase Agent data</button></article>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import cytoscape from 'cytoscape'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { apiAddToShelf } from '@/api/bookshelf'
import { apiCreateAgentSession, apiDeleteAgentModel, apiDeleteAgentSession, apiEraseAgentPersonalData, apiExportAgentSession, apiGetAgentCredits, apiGetAgentGraph, apiGetAgentClues, apiGetAgentInfrastructure, apiGetAgentMessages, apiGetAgentPreferences, apiGetAgentTimeline, apiGetAgentReadingMap, apiGetAgentReadingPlan, apiGetAgentReaderLink, apiGetAgentShelfGroups, apiGetPlotCapsule, apiGetQuickRecommendations, apiGetSimilarBooks, apiListAgentModels, apiSaveAgentModel, apiSaveAgentPreferences, apiSaveAgentShelfGroup, apiSaveRecommendationFeedback, apiSetAgentModelEnabled, apiTestAgentModel, apiListAgentSessions, apiSearchAgentSessions, streamAgentMessage } from '@/api/agent'

const toast = useToast()
const router = useRouter()
const activeTab = ref('chats')
const tabs = [{ id: 'chats', label: '对话工作台' }, { id: 'models', label: '模型与积分' }, { id: 'insights', label: '书籍洞察' }, { id: 'preferences', label: '偏好与隐私' }]
const sessions = ref([])
const sessionSearch = ref('')
const activeSession = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const credits = ref(null)
const infrastructure = ref(null)
const models = ref([])
const selectedModelConfigId = ref('')
const testingModelId = ref(null)
const modelForm = ref({ provider: 'deepseek', model: 'deepseek-chat', apiKey: '', baseUrl: '' })
const insightBookId = ref('')
const insightChapter = ref(1)
const insightLoading = ref(false)
const insightLoaded = ref(false)
const graph = ref({ nodes: [], edges: [] })
const clues = ref([])
const timeline = ref([])
const readingMap = ref({ events: [], links: [] })
const similarBooks = ref([])
const capsule = ref(null)
const shelfRecommendations = ref([])
const readingPlan = ref(null)
const shelfGroups = ref([])
const graphTypeFilter = ref('ALL')
const readingMapBranch = ref('ALL')
const selectedGraphEvidence = ref(null)
const cytoscapeGraph = ref(null)
let cy = null
const preferences = ref({ preferredGenres: [], avoidedThemes: [], spoilerLevel: 'STRICT', personalizationEnabled: true, retainConversations: true })
const preferenceGenres = ref('')
const avoidedThemes = ref('')
const eraseConversations = ref(true)
const loadNotice = ref('')
const graphTypes = computed(() => ['ALL', ...new Set((graph.value.nodes || []).map(node => node.type).filter(Boolean))])
const enabledModels = computed(() => models.value.filter(model => model.enabled))
const visibleGraphNodes = computed(() => (graph.value.nodes || []).filter(node => graphTypeFilter.value === 'ALL' || node.type === graphTypeFilter.value).slice(0, 12))
const visibleGraphEdges = computed(() => {
  const nodeIds = new Set(visibleGraphNodes.value.map(node => node.id))
  return (graph.value.edges || []).filter(edge => nodeIds.has(edge.source) && nodeIds.has(edge.target))
})
const readingMapBranches = computed(() => ['ALL', ...new Set((readingMap.value.events || []).map(event => event.branch).filter(Boolean))])
const visibleReadingMapEvents = computed(() => (readingMap.value.events || []).filter(event => readingMapBranch.value === 'ALL' || event.branch === readingMapBranch.value))
const visibleReadingMapLinks = computed(() => {
  const ids = new Set(visibleReadingMapEvents.value.map(event => event.id))
  return (readingMap.value.links || []).filter(link => ids.has(link.source) && ids.has(link.target))
})
function renderCytoscapeGraph () {
  if (!cytoscapeGraph.value) {
    const svg = document.querySelector('.relationship-map')
    if (!svg?.parentElement) return
    cytoscapeGraph.value = document.createElement('div')
    cytoscapeGraph.value.className = 'cytoscape-graph'
    cytoscapeGraph.value.style.cssText = 'width:100%;height:230px;margin-top:8px;border:1px solid var(--paper-3);border-radius:var(--radius-sm);background:var(--paper-1);'
    svg.parentElement.insertBefore(cytoscapeGraph.value, svg)
    svg.style.display = 'none'
  }
  const elements = [...visibleGraphNodes.value.map(node => ({ data: { id: String(node.id), label: node.name, node } })), ...visibleGraphEdges.value.map((edge, index) => ({ data: { id: `${edge.source}-${edge.target}-${index}`, source: String(edge.source), target: String(edge.target), label: edge.relation, edge } }))]
  if (cy) cy.destroy()
  cy = cytoscape({ container: cytoscapeGraph.value, elements, layout: { name: 'cose', animate: false, padding: 16 }, style: [{ selector: 'node', style: { label: 'data(label)', 'background-color': '#b78836', color: '#33271c', 'font-size': 10, 'text-valign': 'center', 'text-max-width': 58 } }, { selector: 'edge', style: { width: 1.5, 'line-color': '#8a7965', 'target-arrow-color': '#8a7965', 'target-arrow-shape': 'triangle', label: 'data(label)', 'font-size': 8, color: '#6d5c49' } }] })
  cy.on('tap', 'node', event => selectGraphEvidence(event.target.data('node'), 'NODE'))
  cy.on('tap', 'edge', event => selectGraphEvidence(event.target.data('edge'), 'EDGE'))
}
watch([visibleGraphNodes, visibleGraphEdges], () => nextTick(renderCytoscapeGraph), { deep: true })

async function load() {
  const coreResults = await Promise.allSettled([apiListAgentSessions(), apiListAgentModels(), apiGetAgentCredits(), apiGetAgentPreferences()])
  const [sessionResult, modelResult, creditResult, preferenceResult] = coreResults
  const unavailable = []
  sessions.value = sessionResult.status === 'fulfilled' ? sessionResult.value : []
  models.value = modelResult.status === 'fulfilled' ? modelResult.value : []
  if (selectedModelConfigId.value && !models.value.some(model => String(model.id) === selectedModelConfigId.value)) selectedModelConfigId.value = ''
  credits.value = creditResult.status === 'fulfilled' ? creditResult.value : null
  if (preferenceResult.status === 'fulfilled') {
    preferences.value = preferenceResult.value
    preferenceGenres.value = preferenceResult.value.preferredGenres.join(', ')
    avoidedThemes.value = preferenceResult.value.avoidedThemes.join(', ')
  }
  ;[['会话', sessionResult], ['模型', modelResult], ['积分', creditResult], ['偏好', preferenceResult]].forEach(([name, result]) => {
    if (result.status === 'rejected') unavailable.push(name)
  })
  const optionalResults = await Promise.allSettled([apiGetAgentInfrastructure(), apiGetQuickRecommendations(), apiGetAgentReadingPlan(), apiGetAgentShelfGroups()])
  const [infrastructureResult, recommendationResult, planResult, shelfGroupResult] = optionalResults
  infrastructure.value = infrastructureResult.status === 'fulfilled' ? infrastructureResult.value : null
  shelfRecommendations.value = recommendationResult.status === 'fulfilled' ? recommendationResult.value : []
  readingPlan.value = planResult.status === 'fulfilled' ? planResult.value : null
  shelfGroups.value = shelfGroupResult.status === 'fulfilled' ? shelfGroupResult.value : []
  ;[['运行状态', infrastructureResult], ['推荐', recommendationResult], ['阅读计划', planResult], ['书架视图', shelfGroupResult]].forEach(([name, result]) => {
    if (result.status === 'rejected') unavailable.push(name)
  })
  loadNotice.value = unavailable.length ? `部分 Agent 能力暂不可用（${unavailable.join('、')}），其余功能仍可正常使用。` : ''
  const routeBookId = router.currentRoute.value.query.canonicalBookId
  const routeChapter = router.currentRoute.value.query.chapterIndex
  if (routeBookId) insightBookId.value = String(routeBookId)
  if (routeChapter !== undefined && routeChapter !== null && Number(routeChapter) >= 0) insightChapter.value = Number(routeChapter) + 1
  const requestedSessionId = router.currentRoute.value.query.sessionId
  const requestedSession = requestedSessionId && sessions.value.find(session => String(session.id) === String(requestedSessionId))
  if (requestedSession) await selectSession(requestedSession)
  else if (sessions.value.length) await selectSession(sessions.value[0])
}
async function newSession() {
  const session = await apiCreateAgentSession({})
  sessions.value.unshift(session)
  await selectSession(session)
}
async function selectSession(session) {
  activeSession.value = session
  messages.value = await apiGetAgentMessages(session.id)
  restoreSessionContext(session)
}

function restoreSessionContext(session) {
  if (!session?.context) return
  try {
    const context = typeof session.context === 'string' ? JSON.parse(session.context) : session.context
    if (context?.canonicalBookId) insightBookId.value = String(context.canonicalBookId)
    if (context?.currentChapter !== undefined && Number(context.currentChapter) >= 0) insightChapter.value = Number(context.currentChapter) + 1
  } catch (_) {
    // Historical sessions may contain legacy opaque page context.
  }
}
async function searchSessions() {
  sessions.value = sessionSearch.value.trim() ? await apiSearchAgentSessions(sessionSearch.value.trim()) : await apiListAgentSessions()
}
async function exportConversation() {
  if (!activeSession.value) return
  try {
    const data = await apiExportAgentSession(activeSession.value.id)
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
    const link = document.createElement('a')
    link.href = url; link.download = `agent-session-${activeSession.value.id}.json`; link.click()
    URL.revokeObjectURL(url)
  } catch (error) { toast.error(error.message) }
}
async function deleteConversation() {
  if (!activeSession.value || !window.confirm('Delete this conversation and its messages?')) return
  try {
    const deletedId = activeSession.value.id
    await apiDeleteAgentSession(deletedId)
    sessions.value = sessions.value.filter(session => session.id !== deletedId)
    activeSession.value = null
    messages.value = []
    if (sessions.value.length) await selectSession(sessions.value[0])
    toast.success('Conversation deleted')
  } catch (error) { toast.error(error.message) }
}
async function send(requestContext = {}) {
  if (!activeSession.value || !draft.value.trim() || sending.value) return
  const content = draft.value.trim()
  draft.value = ''
  const localMessageId = Date.now()
  messages.value.push({ id: `local-user-${localMessageId}`, role: 'USER', content })
  messages.value.push({ id: `local-assistant-${localMessageId}`, role: 'ASSISTANT', content: '' })
  sending.value = true
  let answer = ''
  try {
    const modelRequest = selectedModelConfigId.value
      ? { mode: 'BYOK', modelConfigId: Number(selectedModelConfigId.value) }
      : { mode: 'PLATFORM' }
    const readingContext = insightBookId.value && insightChapter.value >= 1
      ? { canonicalBookId: Number(insightBookId.value), currentChapter: Number(insightChapter.value) - 1 }
      : {}
    await streamAgentMessage(activeSession.value.id, { content, ...modelRequest, ...readingContext, ...requestContext }, {
      onDelta: (delta) => { answer += delta; messages.value[messages.value.length - 1].content = answer },
      onRecommendations: (data) => { if (Array.isArray(data)) shelfRecommendations.value = data },
      onDone: (reply) => { messages.value[messages.value.length - 1].citations = reply?.citations || [] }
    })
    credits.value = await apiGetAgentCredits()
  } catch (error) { messages.value[messages.value.length - 1].content = error.message }
  finally { sending.value = false }
}
async function saveModel() {
  try { const saved = await apiSaveAgentModel(modelForm.value); modelForm.value.apiKey = ''; models.value = await apiListAgentModels(); selectedModelConfigId.value = String(saved.id); toast.success('模型 Key 已加密保存，并已用于后续对话') } catch (error) { toast.error(error.message) }
}
async function removeModel(id) {
  await apiDeleteAgentModel(id)
  models.value = models.value.filter((item) => item.id !== id)
  if (selectedModelConfigId.value === String(id)) selectedModelConfigId.value = ''
}
async function testModel(id) {
  testingModelId.value = id
  try { await apiTestAgentModel(id); toast.success('模型连接测试成功') } catch (error) { toast.error(error.message || '模型连接测试失败') } finally { testingModelId.value = null }
}
async function toggleModel(model) {
  try {
    const updated = await apiSetAgentModelEnabled(model.id, !model.enabled)
    models.value = models.value.map(item => item.id === updated.id ? updated : item)
    if (!updated.enabled && selectedModelConfigId.value === String(updated.id)) selectedModelConfigId.value = ''
    toast.success(updated.enabled ? '模型已启用' : '模型已停用')
  } catch (error) { toast.error(error.message) }
}
function splitPreferences(value) { return value.split(',').map(item => item.trim()).filter(Boolean) }
async function savePreferences() {
  try {
    preferences.value = await apiSaveAgentPreferences({ ...preferences.value, preferredGenres: splitPreferences(preferenceGenres.value), avoidedThemes: splitPreferences(avoidedThemes.value) })
    if (!preferences.value.retainConversations) { sessions.value = []; activeSession.value = null; messages.value = [] }
    toast.success('Preferences saved')
  } catch (error) { toast.error(error.message) }
}
async function erasePersonalData() {
  if (!window.confirm('Erase Agent preferences and selected personal history?')) return
  try {
    await apiEraseAgentPersonalData(eraseConversations.value)
    preferences.value = { preferredGenres: [], avoidedThemes: [], spoilerLevel: 'STRICT', personalizationEnabled: true, retainConversations: true }
    preferenceGenres.value = ''; avoidedThemes.value = ''
    if (eraseConversations.value) { sessions.value = []; activeSession.value = null; messages.value = [] }
    toast.success('Agent data erased')
  } catch (error) { toast.error(error.message) }
}
async function confirmAddToShelf (item) {
  if (!item?.canonicalBookId || !window.confirm(`Add ${item.title} to your bookshelf?`)) return
  try {
    const detail = await apiGetAgentReaderLink(item.canonicalBookId)
    if (!detail?.sourceId || !detail?.sourceBookUrl) throw new Error('No readable source is available for this work.')
    await apiAddToShelf({ canonicalBookId: item.canonicalBookId, sourceId: detail.sourceId, bookName: detail.title || item.title, author: detail.author, coverUrl: detail.coverUrl, bookUrl: detail.sourceBookUrl })
    await saveRecommendationFeedback(item, 'ADD_TO_SHELF')
    toast.success('Added to your bookshelf')
  } catch (error) { toast.error(error.message) }
}
async function openRecommendedBook (item) {
  try {
    const detail = await apiGetAgentReaderLink(item.canonicalBookId)
    if (!detail?.sourceId || !detail?.sourceBookUrl) throw new Error('No readable source is available for this work.')
    router.push({ name: 'Reader', query: { sourceId: detail.sourceId, bookUrl: detail.sourceBookUrl, bookName: detail.title || item.title, author: detail.author, coverUrl: detail.coverUrl, intro: detail.summary, canonicalBookId: item.canonicalBookId } })
  } catch (error) { toast.error(error.message) }
}
function citationItems(message) {
  return Array.isArray(message.citations) ? message.citations : []
}
function isInterviewResponse(content) { return typeof content === 'string' && content.includes('【原文事实】') && content.includes('【基于事实的推断】') && content.includes('【不足以判断】') }
async function openCitation(citation) {
  try {
    const detail = await apiGetAgentReaderLink(citation.canonicalBookId)
    if (!detail?.sourceId || !detail?.sourceBookUrl) { toast.error('A readable source is not available for this citation yet.'); return }
    router.push({ name: 'Reader', query: { sourceId: detail.sourceId, bookUrl: detail.sourceBookUrl, bookName: detail.title, author: detail.author, coverUrl: detail.coverUrl, intro: detail.summary, canonicalBookId: citation.canonicalBookId, chapterIndex: citation.chapterIndex } })
  } catch (error) { toast.error(error.message) }
}
function timelineChapter (item) {
  const match = /^第(\d+)章/.exec(item || '')
  return match ? Math.max(0, Number(match[1]) - 1) : Math.max(0, Number(insightChapter.value) - 1)
}
async function openInsightChapter (chapterIndex) {
  try {
    const detail = await apiGetAgentReaderLink(Number(insightBookId.value))
    if (!detail?.sourceId || !detail?.sourceBookUrl) { toast.error('A readable source is not available for this evidence yet.'); return }
    router.push({ name: 'Reader', query: { sourceId: detail.sourceId, bookUrl: detail.sourceBookUrl, bookName: detail.title, author: detail.author, coverUrl: detail.coverUrl, intro: detail.summary, canonicalBookId: Number(insightBookId.value), chapterIndex: Math.max(0, Number(chapterIndex) || 0) } })
  } catch (error) { toast.error(error.message) }
}
function graphNodeIndex(nodeId) { return visibleGraphNodes.value.findIndex(node => node.id === nodeId) }
function selectGraphEvidence (item, kind) {
  selectedGraphEvidence.value = { label: kind === 'NODE' ? `${item.name} / ${item.type}` : item.relation, chapter: item.firstChapter || 0, confidence: item.confidence, evidence: item.evidence }
}
function graphPoint(index) {
  const total = Math.max(visibleGraphNodes.value.length, 1)
  const safeIndex = index < 0 ? 0 : index
  const angle = (Math.PI * 2 * safeIndex / total) - Math.PI / 2
  return { x: 180 + Math.cos(angle) * 125, y: 115 + Math.sin(angle) * 76 }
}
function eventLinkCount(eventId) { return visibleReadingMapLinks.value.filter(link => link.source === eventId || link.target === eventId).length }
async function startCharacterInterview(node) {
  if (!insightBookId.value || insightChapter.value < 1) return
  if (!activeSession.value) await newSession()
  activeTab.value = 'chats'
  draft.value = `Please interview ${node.name} in first person. Only use the chapters I have read; do not reveal later events or invent facts. Start with what matters most to you now.`
  await send({ canonicalBookId: Number(insightBookId.value), currentChapter: Number(insightChapter.value) - 1, interviewCharacter: node.name })
}
async function saveRecommendationFeedback(item, action) {
  try {
    await apiSaveRecommendationFeedback({ canonicalBookId: Number(item.canonicalBookId), action })
    shelfRecommendations.value = await apiGetQuickRecommendations()
    toast.success(action === 'LIKE' ? 'Preference saved' : 'Recommendation hidden')
  } catch (error) { toast.error(error.message) }
}
async function saveShelfGroup(item, groupCode) {
  try {
    await apiSaveAgentShelfGroup({ canonicalBookId: Number(item.canonicalBookId), groupCode })
    shelfGroups.value = await apiGetAgentShelfGroups()
    toast.success(groupCode === 'AUTO' ? 'Automatic shelf grouping restored' : 'Shelf view fixed')
  } catch (error) { toast.error(error.message) }
}
async function loadInsights() {
  if (!insightBookId.value || insightChapter.value < 1) return
  insightLoading.value = true
  try {
    const chapter = insightChapter.value - 1
    const results = await Promise.allSettled([
      apiGetAgentGraph(insightBookId.value, chapter),
      apiGetAgentClues(insightBookId.value, chapter),
      apiGetAgentTimeline(insightBookId.value, chapter),
      apiGetAgentReadingMap(insightBookId.value, chapter),
      apiGetSimilarBooks(insightBookId.value, chapter),
      apiGetPlotCapsule(insightBookId.value, chapter)
    ])
    const [graphResult, clueResult, timelineResult, readingMapResult, similarResult, capsuleResult] = results
    const unavailable = results.filter(result => result.status === 'rejected').length
    graph.value = graphResult.status === 'fulfilled' ? graphResult.value : { nodes: [], edges: [] }
    graphTypeFilter.value = 'ALL'
    selectedGraphEvidence.value = null
    clues.value = clueResult.status === 'fulfilled' ? clueResult.value : []
    timeline.value = timelineResult.status === 'fulfilled' ? timelineResult.value : []
    readingMap.value = readingMapResult.status === 'fulfilled' ? readingMapResult.value : { events: [], links: [] }
    readingMapBranch.value = 'ALL'
    similarBooks.value = similarResult.status === 'fulfilled' ? similarResult.value : []
    capsule.value = capsuleResult.status === 'fulfilled' ? capsuleResult.value : null
    insightLoaded.value = true
    if (unavailable) toast.error(`${unavailable} 项洞察暂不可用，已展示其余阅读安全结果。`)
  } catch (error) {
    toast.error(error.message)
  } finally {
    insightLoading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.agent-center { background: radial-gradient(circle at 10% 0%, var(--gold-3), transparent 28%), var(--paper-1); }
.agent-load-notice { margin: -12px 0 var(--space-5); padding: 9px 12px; border-left: 3px solid var(--gold-0); border-radius: var(--radius-sm); background: var(--paper-2); color: var(--ink-3); font-size: .82rem; }
.agent-hero { display: flex; justify-content: space-between; align-items: end; gap: var(--space-8); margin-bottom: var(--space-8); }
.agent-eyebrow { color: var(--gold-0); font-size: .7rem; font-weight: 700; letter-spacing: .14em; }
.agent-hero h1 { margin: var(--space-2) 0; font-size: clamp(2rem, 4vw, 3.5rem); }
.credit-card { min-width: 200px; padding: var(--space-5); border-radius: var(--radius-lg); color: var(--paper-0); background: var(--ink-0); }
.credit-card span,.credit-card small { display: block; opacity: .75; font-size: .75rem; }.credit-card strong { display:block; margin: 3px 0; font-family: var(--font-serif); font-size: 2.2rem; color: var(--gold-2); }
.agent-tabs { display: flex; gap: var(--space-2); border-bottom: 1px solid var(--paper-3); margin-bottom: var(--space-6); overflow-x: auto; }.agent-tabs button { border: 0; padding: var(--space-3) var(--space-4); background: transparent; color: var(--ink-3); cursor: pointer; white-space: nowrap; }.agent-tabs button.active { color: var(--ink-0); border-bottom: 2px solid var(--gold-0); }
.agent-workbench { display: grid; grid-template-columns: 240px 1fr; gap: var(--space-4); min-height: 560px; }.session-list { padding: var(--space-3); display: flex; flex-direction: column; gap: 6px; }.session-list button { border: 0; background: transparent; text-align: left; padding: 10px; border-radius: var(--radius-sm); color: var(--ink-2); cursor: pointer; }.session-list button.selected { background: var(--gold-3); color: var(--ink-0); }.session-list .new-session { background: var(--ink-0); color: var(--paper-0); text-align:center; }.session-search { width:100%; box-sizing:border-box; border:1px solid var(--paper-3); border-radius:var(--radius-sm); padding:8px; background:var(--paper-0); font:inherit; }.chat-actions { display:flex; justify-content:flex-end; margin-bottom:8px; }
.chat-pane { display:flex; flex-direction:column; padding:0; overflow:hidden; }.chat-history { flex:1; min-height:440px; padding:var(--space-5); overflow:auto; display:flex; flex-direction:column; gap:var(--space-3); }.center-message { max-width:78%; padding:12px; border-radius:var(--radius-md); white-space:pre-wrap; }.center-message small { display:block; margin-top:8px; color:var(--ink-4); font-size:.72rem; white-space:normal; }.citation-list { margin-top:8px; padding-top:6px; border-top:1px solid var(--paper-3); }.center-message.user { align-self:flex-end; background:var(--ink-0); color:var(--paper-0); }.center-message.assistant { background:var(--paper-2); }.center-input { display:flex; gap:var(--space-3); padding:var(--space-4); border-top:1px solid var(--paper-3); }.center-input textarea { flex:1; padding:10px; border:1px solid var(--paper-3); border-radius:var(--radius-md); font:inherit; }.chat-model-select { display:flex; flex:0 0 160px; flex-direction:column; gap:3px; color:var(--ink-3); font-size:.68rem; }.chat-model-select select { min-width:0; padding:8px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-0); color:var(--ink-1); font:inherit; font-size:.75rem; }.empty-state { display:grid; place-items:center; min-height:500px; color:var(--ink-4); }
.citation-link { display:block; width:100%; border:0; padding:4px 0; background:transparent; color:var(--sage-0); text-align:left; font:inherit; font-size:.72rem; cursor:pointer; }.citation-link:hover { text-decoration:underline; }.interview-contract { margin-top:8px; color:var(--sage-0); font-size:.68rem; }
.model-layout { display:grid; grid-template-columns:1fr 1fr; gap:var(--space-4); }.model-intro { grid-row:span 2; }.model-intro ul { margin:var(--space-4) 0 0 var(--space-5); color:var(--ink-2); }.model-form { display:flex; flex-direction:column; gap:var(--space-3); }.model-form label { display:flex; flex-direction:column; gap:6px; font-size:.85rem; color:var(--ink-2); }.model-form input,.model-form select { padding:10px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-0); font:inherit; }.saved-model { display:flex; justify-content:space-between; align-items:center; gap:var(--space-3); padding:var(--space-3) 0; border-bottom:1px solid var(--paper-3); }.saved-model small { display:block; color:var(--ink-4); margin-top:3px; }.model-actions { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:6px; }
.insights-panel { display:grid; gap:var(--space-4); }.insight-query { display:grid; grid-template-columns:1fr 180px 180px auto; gap:var(--space-3); align-items:end; }.insight-query h2 { margin:var(--space-2) 0; }.insight-query label { display:flex; flex-direction:column; gap:6px; font-size:.82rem; }.insight-query input { padding:10px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); font:inherit; }.insight-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:var(--space-4); }.insight-card span { color:var(--gold-0); font-family:var(--font-serif); font-size:1.25rem; }.insight-card h2 { margin:var(--space-2) 0; }.insight-card small { color:var(--sage-0); }.insight-card ul { margin:var(--space-3) 0; padding-left:var(--space-5); color:var(--ink-2); font-size:.84rem; }.insight-card li { margin-bottom:8px; }.clue-status { margin-left:4px; color:var(--gold-0); font-size:.72rem; font-style:normal; }.relationship-map { width:100%; height:auto; margin:var(--space-3) 0 0; border-radius:var(--radius-md); background:linear-gradient(135deg, var(--paper-1), var(--gold-3)); }.relationship-map line { stroke:var(--sage-0); stroke-width:1.5; opacity:.6; }.relationship-map line.tentative { stroke-dasharray:5 4; opacity:.35; }.relationship-map circle.character { fill:var(--ink-0); }.relationship-map circle.other { fill:var(--sage-0); }.relationship-map text { fill:var(--paper-0); font-size:10px; text-anchor:middle; font-family:var(--font-serif); }.character-interview,.recommendation-actions button { margin-left:8px; border:1px solid var(--gold-1); border-radius:99px; padding:2px 7px; background:transparent; color:var(--gold-0); font:inherit; font-size:.7rem; cursor:pointer; }.recommendation-actions { display:inline-flex; margin-top:5px; }.recommendation-actions button { margin-left:0; margin-right:5px; }
.graph-tools { display:flex; flex-wrap:wrap; gap:5px; margin:8px 0; }.graph-tools button,.graph-card li button { border:1px solid var(--paper-3); border-radius:99px; padding:3px 7px; background:var(--paper-0); color:var(--ink-2); font:inherit; font-size:.68rem; cursor:pointer; }.graph-tools button.active { background:var(--ink-0); color:var(--paper-0); border-color:var(--ink-0); }.relationship-map line,.relationship-map g { cursor:pointer; }.graph-evidence { margin-top:10px; padding:9px; border-left:3px solid var(--gold-0); background:var(--paper-1); font-size:.78rem; }.graph-evidence b,.graph-evidence span { display:block; }.graph-evidence span { color:var(--ink-4); margin-top:3px; }.graph-evidence p { margin:6px 0 0; line-height:1.5; }
.reading-map-events { margin:var(--space-3) 0; padding-left:var(--space-5); }.reading-map-events li { padding:0 0 10px 6px; border-left:1px solid var(--paper-3); }.reading-map-events strong { color:var(--ink-1); font-size:.8rem; }.reading-map-events p { margin:5px 0; font-size:.76rem; line-height:1.5; }.reading-map-events small { color:var(--ink-4); font-size:.68rem; }.evidence-jump { margin:5px 0 0 6px; border:0; border-bottom:1px solid var(--gold-1); padding:1px 0; background:transparent; color:var(--ink-2); font:inherit; font-size:.68rem; cursor:pointer; }.relationship-map line { cursor:pointer; stroke-width:4px; stroke-opacity:.25; }.relationship-map line:hover { stroke-opacity:.8; }
.shelf-groups-card li { display:grid; grid-template-columns:1fr auto; gap:5px 10px; align-items:center; }.shelf-groups-card li small { grid-column:1 / -1; }.shelf-groups-card select { max-width:150px; padding:5px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-0); color:var(--ink-2); font:inherit; font-size:.7rem; }
.privacy-layout { display:grid; grid-template-columns:1fr 1fr; gap:var(--space-4); align-items:start; }.preference-form { display:flex; flex-direction:column; gap:var(--space-3); }.preference-form label { display:flex; flex-direction:column; gap:6px; font-size:.85rem; color:var(--ink-2); }.preference-form input,.preference-form select { padding:10px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-0); font:inherit; }.switch-line { flex-direction:row!important; align-items:center; }.privacy-danger p { color:var(--ink-2); line-height:1.65; }.privacy-danger .btn { margin-top:var(--space-4); }
@media(max-width:700px){.agent-hero,.agent-workbench,.model-layout,.insight-query,.privacy-layout{display:flex;flex-direction:column}.credit-card{width:100%}.agent-workbench{min-height:unset}.session-list{max-height:180px}.insight-grid{grid-template-columns:1fr}.center-input{flex-direction:column}.chat-model-select{flex:auto}.insight-query>*{width:100%}}
</style>
