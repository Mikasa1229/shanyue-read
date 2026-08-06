<template>
  <div class="agent-float" :style="floatStyle">
    <transition name="agent-panel">
      <section v-if="open" class="agent-popover" :style="mobilePanelStyle" aria-label="小说智能助手">
        <button class="agent-mobile-handle" type="button" aria-label="拖动调整助手窗口高度" @pointerdown="startResize" />
        <header class="agent-head">
          <div>
            <span class="agent-kicker">阅见智能</span>
            <h2>阅见助手</h2>
          </div>
          <button class="agent-close" type="button" aria-label="关闭助手" @click="open = false">×</button>
        </header>

        <div v-if="!userStore.isLoggedIn" class="agent-guest">
          <span class="agent-kicker">阅读陪伴</span>
          <h3>先看看阅见助手能做什么</h3>
          <p>无剧透回忆、人物关系、伏笔线索、相似书 DNA 和书架阅读计划，都会严格依据你的已读进度。</p>
          <ul><li>阅读时不离开当前页面</li><li>章节引用可回到原文</li><li>登录后获得 3 次平台试用额度</li></ul>
          <button class="agent-guest-login" type="button" @click="goToLogin">登录后开始对话</button>
        </div>
        <div v-else ref="messageList" class="agent-messages">
          <div v-if="!messages.length" class="agent-welcome">
            <strong>今天想读点什么？</strong>
            <p>我可以从你的书架出发，也可以陪你梳理正在读的故事。</p>
            <div class="agent-prompts">
              <button v-for="prompt in prompts" :key="prompt" type="button" @click="ask(prompt)">{{ prompt }}</button>
            </div>
          </div>
          <article v-for="(message, index) in messages" :key="`${message.id || index}-${message.role}`" :class="['agent-message', message.role === 'USER' ? 'is-user' : 'is-agent']">
            <span>{{ message.content }}</span>
            <div v-if="citationItems(message).length" class="agent-citations">
              <button v-for="citation in citationItems(message)" :key="`${citation.canonicalBookId}-${citation.chapterIndex}-${citation.excerpt}`" type="button" @click="openCitation(citation)">
                {{ citationLabel(citation) }}
              </button>
            </div>
          </article>
          <p v-if="status" class="agent-status">{{ status }}</p>
          <section v-if="previewLoading || recommendations.length || graphPreview.nodes?.length || plotCapsule || previewError" class="agent-preview" aria-label="阅读助手快捷洞察">
            <p v-if="previewLoading" class="agent-status">正在加载阅读助手快捷洞察…</p>
            <template v-else>
              <p v-if="previewError" class="agent-preview-note">{{ previewError }}</p>
              <details v-if="plotCapsule" class="agent-capsule">
                <summary>无剧透回忆胶囊</summary>
                <template v-if="plotCapsule.timeline?.length"><p v-for="item in plotCapsule.timeline.slice(0, 5)" :key="item">{{ item }}</p></template>
                <p v-else>已读到第 {{ (plotCapsule.readingBoundary || 0) + 1 }} 章；这部分尚未建立可引用的剧情索引。</p>
                <small>{{ plotCapsule.safetyNote }}</small>
              </details>
              <div v-if="recommendations.length" class="agent-recommendations">
                <small>为你推荐</small>
                <button v-for="item in recommendations.slice(0, 3)" :key="`${item.canonicalBookId || ''}-${item.title}`" class="agent-recommendation-card" type="button" :disabled="!item.canonicalBookId" @click="openRecommendation(item)">
                  <strong>{{ item.title }}</strong><span>{{ item.reason }}</span>
                </button>
              </div>
              <div v-if="graphPreview.nodes?.length" class="agent-graph-preview">
                <small>当前人物关系（已读范围）</small>
                <div class="agent-graph-nodes"><span v-for="node in graphPreview.nodes.slice(0, 4)" :key="node.id">{{ node.name }}</span></div>
                <p>{{ graphPreview.edges?.length || 0 }} 条已验证关联；完整图谱在 Agent 中心查看。</p>
              </div>
            </template>
          </section>
        </div>

        <form class="agent-input" @submit.prevent="send">
          <div class="agent-quick-tools">
            <button type="button" class="agent-tool" :class="{ active: showShelfPicker }" @click="showShelfPicker = !showShelfPicker">＋ 引用书架作品</button>
            <span v-if="referencedBook" class="agent-reference">已引用《{{ referencedBook.bookName }}》</span>
          </div>
          <div v-if="showShelfPicker" class="agent-shelf-picker">
            <select v-model="selectedShelfBookId" @change="applyShelfReference"><option value="">选择书架中的作品</option><option v-for="book in usableShelfBooks" :key="book.canonicalBookId" :value="String(book.canonicalBookId)">{{ book.bookName }} · 已读第 {{ (book.lastChapterIndex ?? 0) + 1 }} 章</option></select>
            <small v-if="!usableShelfBooks.length">书架里还没有可引用的作品。</small>
          </div>
          <textarea v-model="draft" rows="2" maxlength="4000" placeholder="问我找书、剧情或书架…" @keydown.enter.exact.prevent="send" />
          <button class="agent-send" type="submit" :disabled="sending || !draft.trim()">{{ sending ? '思考中' : '发送' }}</button>
        </form>
        <footer class="agent-foot">
          <router-link :to="agentCenterLink" @click="open = false">进入完整 Agent 中心</router-link>
        </footer>
      </section>
    </transition>
    <button class="agent-trigger" type="button" :aria-expanded="open" aria-label="打开阅读助手" @pointerdown="startDrag" @click="handleTriggerClick">
      <svg class="agent-trigger-icon" viewBox="0 0 48 48" aria-hidden="true">
        <path class="agent-trigger-book" d="M10 12.5c5.5-2.5 10.5-2.2 14 1.2v23.8c-3.5-3.4-8.5-3.7-14-1.2z" />
        <path class="agent-trigger-book" d="M38 12.5c-5.5-2.5-10.5-2.2-14 1.2v23.8c3.5-3.4 8.5-3.7 14-1.2z" />
        <path class="agent-trigger-spark" d="M24 7v8M20 11h8M31.5 22l2.5 2.5M34 22l-2.5 2.5" />
      </svg>
      <small>阅见</small>
    </button>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { apiCreateAgentSession, apiGetAgentGraph, apiGetAgentMessages, apiGetAgentReaderLink, apiGetPlotCapsule, apiGetQuickRecommendations, streamAgentMessage } from '@/api/agent'
import { apiGetMyShelf } from '@/api/bookshelf'

const router = useRouter()
const userStore = useUserStore()
const open = ref(false)
const sessionId = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const status = ref('')
const messageList = ref(null)
const mobilePanelHeight = ref(null)
const floatPosition = ref(loadFloatPosition())
const dragging = ref(false)
const movedDuringDrag = ref(false)
let dragState = null
const recommendations = ref([])
const graphPreview = ref({ nodes: [], edges: [] })
const plotCapsule = ref(null)
const previewLoading = ref(false)
const previewError = ref('')
const shelfBooks = ref([])
const showShelfPicker = ref(false)
const selectedShelfBookId = ref('')
const prompts = ['按我的书架推荐', '帮我回忆正在读的剧情', '找一本和当前书类似的作品']
const usableShelfBooks = computed(() => shelfBooks.value.filter(book => book?.canonicalBookId && book.bookName))
const referencedBook = computed(() => usableShelfBooks.value.find(book => String(book.canonicalBookId) === String(selectedShelfBookId.value)) || null)

const currentContext = computed(() => ({
  path: router.currentRoute.value.fullPath,
  title: router.currentRoute.value.query.bookName || document.title,
  bookUrl: router.currentRoute.value.query.bookUrl,
  canonicalBookId: router.currentRoute.value.query.canonicalBookId,
  currentChapter: router.currentRoute.value.query.chapterIndex
}))
const agentCenterLink = computed(() => ({
  name: 'Agent',
  query: {
    ...(sessionId.value ? { sessionId: String(sessionId.value) } : {}),
    ...(selectedShelfBookId.value || currentContext.value.canonicalBookId ? { canonicalBookId: String(selectedShelfBookId.value || currentContext.value.canonicalBookId) } : {}),
    ...(selectedShelfBookId.value && referencedBook.value ? { chapterIndex: String(referencedBook.value.lastChapterIndex ?? 0) } : (currentContext.value.currentChapter !== undefined && currentContext.value.currentChapter !== null ? { chapterIndex: String(currentContext.value.currentChapter) } : {}))
  }
}))
const mobilePanelStyle = computed(() => mobilePanelHeight.value ? { '--agent-mobile-height': `${mobilePanelHeight.value}px` } : {})
const floatStyle = computed(() => ({ left: `${floatPosition.value.x}px`, top: `${floatPosition.value.y}px`, right: 'auto', bottom: 'auto' }))

function loadFloatPosition() {
  try {
    const saved = JSON.parse(localStorage.getItem('reader-agent-float-position') || 'null')
    if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) return saved
  } catch (_) { /* Ignore malformed local UI state. */ }
  return { x: Math.max(16, window.innerWidth - 96), y: Math.max(16, window.innerHeight - 104) }
}

function clampFloatPosition(x, y) {
  const size = 76
  return { x: Math.min(Math.max(8, x), Math.max(8, window.innerWidth - size)), y: Math.min(Math.max(8, y), Math.max(8, window.innerHeight - size)) }
}

function startDrag(event) {
  if (event.button !== undefined && event.button !== 0) return
  const start = { x: event.clientX, y: event.clientY, left: floatPosition.value.x, top: floatPosition.value.y }
  movedDuringDrag.value = false
  dragState = start
  event.currentTarget.setPointerCapture?.(event.pointerId)
  const move = (moveEvent) => {
    const dx = moveEvent.clientX - start.x
    const dy = moveEvent.clientY - start.y
    if (Math.abs(dx) + Math.abs(dy) > 4) movedDuringDrag.value = true
    floatPosition.value = clampFloatPosition(start.left + dx, start.top + dy)
  }
  const finish = () => {
    if (movedDuringDrag.value) localStorage.setItem('reader-agent-float-position', JSON.stringify(floatPosition.value))
    dragging.value = false
    dragState = null
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', finish)
    window.removeEventListener('pointercancel', finish)
  }
  dragging.value = true
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', finish, { once: true })
  window.addEventListener('pointercancel', finish, { once: true })
}

function handleTriggerClick() {
  if (movedDuringDrag.value) return
  toggle()
}

async function toggle() {
  open.value = !open.value
  if (open.value && userStore.isLoggedIn) {
    await ensureSession()
    try { shelfBooks.value = (await apiGetMyShelf(1, 50))?.records || [] } catch (_) { shelfBooks.value = [] }
    await loadPreview()
  }
}

function applyShelfReference() {
  showShelfPicker.value = false
  if (referencedBook.value) {
    status.value = `本次对话将引用《${referencedBook.value.bookName}》已读到第 ${(referencedBook.value.lastChapterIndex ?? 0) + 1} 章`
  }
}

function goToLogin() {
  open.value = false
  router.push({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath } })
}

function startResize(event) {
  if (window.innerWidth > 600) return
  const startY = event.clientY
  const initialHeight = mobilePanelHeight.value || Math.min(window.innerHeight * 0.72, 620)
  event.currentTarget.setPointerCapture?.(event.pointerId)
  const resize = (moveEvent) => {
    const minHeight = 360
    const maxHeight = Math.max(minHeight, window.innerHeight - 96)
    mobilePanelHeight.value = Math.round(Math.min(maxHeight, Math.max(minHeight, initialHeight + startY - moveEvent.clientY)))
  }
  const finish = () => {
    window.removeEventListener('pointermove', resize)
    window.removeEventListener('pointerup', finish)
    window.removeEventListener('pointercancel', finish)
  }
  window.addEventListener('pointermove', resize)
  window.addEventListener('pointerup', finish, { once: true })
  window.addEventListener('pointercancel', finish, { once: true })
}

async function ensureSession() {
  if (sessionId.value) return
  const session = await apiCreateAgentSession({ context: JSON.stringify(currentContext.value) })
  sessionId.value = session.id
  messages.value = await apiGetAgentMessages(session.id)
}

async function loadPreview() {
  previewLoading.value = true
  previewError.value = ''
  try {
    const requests = [apiGetQuickRecommendations()]
    const bookId = Number(currentContext.value.canonicalBookId)
    if (Number.isFinite(bookId) && bookId > 0) {
      const chapter = Math.max(0, Number(currentContext.value.currentChapter) || 0)
      requests.push(apiGetAgentGraph(bookId, chapter), apiGetPlotCapsule(bookId, chapter))
    }
    const results = await Promise.allSettled(requests)
    const [recommendationResult, graphResult, capsuleResult] = results
    recommendations.value = recommendationResult?.status === 'fulfilled' && Array.isArray(recommendationResult.value)
      ? recommendationResult.value : []
    graphPreview.value = graphResult?.status === 'fulfilled' ? graphResult.value : { nodes: [], edges: [] }
    plotCapsule.value = capsuleResult?.status === 'fulfilled' ? capsuleResult.value : null
    const unavailable = results.filter(result => result.status === 'rejected').length
    if (unavailable) {
      previewError.value = unavailable === results.length
        ? '快捷推荐和关系预览暂时不可用；对话和阅读不会受到影响。'
        : `${unavailable} 项快捷洞察暂时不可用，已展示其余阅读安全结果。`
    }
  } catch (_) {
    // Defensive fallback for unexpected client-side failures before requests are assembled.
    recommendations.value = []
    graphPreview.value = { nodes: [], edges: [] }
    plotCapsule.value = null
    previewError.value = '快捷推荐和关系预览暂时不可用；对话和阅读不会受到影响。'
  } finally {
    previewLoading.value = false
  }
}

async function openRecommendation(item) {
  if (!item?.canonicalBookId) return
  try {
    const detail = await apiGetAgentReaderLink(item.canonicalBookId)
    if (!detail?.sourceId || !detail?.sourceBookUrl) {
      previewError.value = '这本推荐作品暂时没有可用书源；你可以在 Agent 中心继续查看。'
      return
    }
    open.value = false
    router.push({ name: 'Reader', query: { sourceId: detail.sourceId, bookUrl: detail.sourceBookUrl, bookName: detail.title || item.title, author: detail.author, coverUrl: detail.coverUrl, intro: detail.summary, canonicalBookId: item.canonicalBookId, chapterIndex: 0 } })
  } catch (_) {
    previewError.value = '暂时无法打开推荐作品；请稍后重试。'
  }
}

async function ask(prompt) {
  draft.value = prompt
  await send()
}

async function send() {
  if (!draft.value.trim() || sending.value) return
  await ensureSession()
  const content = draft.value.trim()
  draft.value = ''
  messages.value.push({ role: 'USER', content })
  sending.value = true
  status.value = '正在整理你的阅读线索…'
  let answer = ''
  messages.value.push({ role: 'ASSISTANT', content: '', citations: [] })
  try {
    const selected = referencedBook.value
    await streamAgentMessage(sessionId.value, {
      content,
      mode: 'PLATFORM',
      canonicalBookId: selected?.canonicalBookId || currentContext.value.canonicalBookId || undefined,
      currentChapter: selected ? Number(selected.lastChapterIndex ?? 0) : Number(currentContext.value.currentChapter) || 0,
      currentBookTitle: selected?.bookName || currentContext.value.title
    }, {
      onDelta(delta) {
        answer += delta
        messages.value[messages.value.length - 1].content = answer
      },
      onStatus(data) { status.value = data?.status === 'thinking' ? '正在思考…' : '正在检索…' },
      onRecommendations(data) { recommendations.value = Array.isArray(data) ? data.slice(0, 3) : recommendations.value },
      onGraph(data) { graphPreview.value = data || graphPreview.value },
      onDone(reply) { messages.value[messages.value.length - 1].citations = reply?.citations || [] },
      onError() { status.value = '请求暂时失败，请稍后重试。' }
    })
  } catch (error) {
    messages.value[messages.value.length - 1].content = error.message
  } finally {
    sending.value = false
    status.value = ''
    await nextTick()
    messageList.value?.scrollTo({ top: messageList.value.scrollHeight, behavior: 'smooth' })
  }
}

function citationItems(message) {
  return Array.isArray(message.citations) ? message.citations : []
}

function citationLabel(citation) {
  return Number.isInteger(citation?.chapterIndex) ? `来源：第 ${citation.chapterIndex + 1} 章` : '来源：已索引内容'
}

async function openCitation(citation) {
  if (!citation?.canonicalBookId || !Number.isInteger(citation.chapterIndex)) return
  try {
    const detail = await apiGetAgentReaderLink(citation.canonicalBookId)
    if (!detail?.sourceId || !detail?.sourceBookUrl) {
      status.value = '该引用暂时没有可用书源。'
      return
    }
    open.value = false
    router.push({ name: 'Reader', query: { sourceId: detail.sourceId, bookUrl: detail.sourceBookUrl, bookName: detail.title, author: detail.author, coverUrl: detail.coverUrl, intro: detail.summary, canonicalBookId: citation.canonicalBookId, chapterIndex: citation.chapterIndex } })
  } catch (_) { status.value = '暂时无法打开引用章节。' }
}
</script>

<style scoped>
.agent-float { position: fixed; z-index: 9000; touch-action: none; }
.agent-trigger { width: 68px; height: 68px; display: grid; place-items: center; gap: 0; padding: 8px; border: 1px solid rgba(255,255,255,.4); border-radius: 22px; color: var(--paper-0); background: linear-gradient(145deg, #c69742 0%, #7d5b2c 48%, #26231e 100%); box-shadow: 0 14px 34px rgba(26,24,20,.28), inset 0 1px 0 rgba(255,255,255,.32); cursor: grab; position: relative; font-family: var(--font-serif); transition: transform var(--transition-base), box-shadow var(--transition-base); }
.agent-trigger:active { cursor: grabbing; }
.agent-trigger::before { content: ''; position: absolute; inset: 5px; border: 1px solid rgba(255,255,255,.22); border-radius: 17px; pointer-events: none; }
.agent-trigger:hover { transform: translateY(-4px) rotate(-2deg); box-shadow: 0 18px 40px rgba(26,24,20,.34), inset 0 1px 0 rgba(255,255,255,.38); }
.agent-trigger-icon { width: 34px; height: 34px; overflow: visible; }
.agent-trigger-book { fill: none; stroke: var(--paper-0); stroke-width: 2.1; stroke-linejoin: round; }
.agent-trigger-spark { fill: none; stroke: var(--gold-2); stroke-width: 2; stroke-linecap: round; }
.agent-trigger small { position: relative; font-size: .62rem; letter-spacing: .12em; color: var(--gold-2); }
.agent-popover { position: absolute; right: 0; bottom: 74px; width: min(400px, calc(100vw - 32px)); height: min(620px, calc(100vh - 110px)); display: flex; flex-direction: column; overflow: hidden; border: 1px solid var(--paper-3); border-radius: var(--radius-xl); background: rgba(255,254,249,.98); box-shadow: var(--shadow-lg); }
.agent-mobile-handle { display: none; }
.agent-head { display: flex; justify-content: space-between; align-items: flex-start; padding: var(--space-5) var(--space-5) var(--space-3); border-bottom: 1px solid var(--paper-3); }
.agent-kicker { color: var(--gold-0); font-size: .65rem; font-weight: 700; letter-spacing: .14em; }
.agent-head h2 { margin-top: 2px; font-size: 1.25rem; }
.agent-close { border: 0; background: transparent; color: var(--ink-3); font-size: 1.6rem; cursor: pointer; line-height: 1; }
.agent-messages { flex: 1; overflow-y: auto; padding: var(--space-4); display: flex; flex-direction: column; gap: var(--space-3); }
.agent-guest { flex:1; padding:var(--space-6) var(--space-5); color:var(--ink-2); background:linear-gradient(145deg,var(--paper-0),var(--gold-3)); }.agent-guest h3 { margin:8px 0; color:var(--ink-0); font-family:var(--font-serif); font-size:1.35rem; }.agent-guest p { line-height:1.7; font-size:.86rem; }.agent-guest ul { margin:18px 0; padding-left:18px; font-size:.8rem; line-height:1.85; }.agent-guest-login { border:0; border-radius:var(--radius-md); padding:10px 14px; color:var(--paper-0); background:var(--ink-0); font:inherit; cursor:pointer; }
.agent-welcome { padding: var(--space-3); color: var(--ink-2); }
.agent-welcome p { margin: var(--space-2) 0 var(--space-4); font-size: .875rem; }
.agent-prompts { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.agent-prompts button { border: 1px solid var(--paper-3); border-radius: var(--radius-full); padding: 6px 10px; background: var(--paper-1); color: var(--ink-2); cursor: pointer; font-size: .75rem; }
.agent-message { max-width: 88%; padding: 10px 12px; border-radius: var(--radius-md); white-space: pre-wrap; font-size: .875rem; line-height: 1.6; }
.agent-citations { display: flex; flex-direction: column; gap: 2px; margin-top: 8px; padding-top: 7px; border-top: 1px solid rgba(138,121,101,.28); white-space: normal; }.agent-citations button { border:0; padding:0; background:transparent; color:var(--sage-0); text-align:left; font:inherit; font-size:.68rem; cursor:pointer; }.agent-citations button:hover { text-decoration:underline; }
.is-user { align-self: flex-end; color: var(--paper-0); background: var(--ink-0); border-bottom-right-radius: 2px; }
.is-agent { align-self: flex-start; background: var(--paper-2); color: var(--ink-1); border-bottom-left-radius: 2px; }
.agent-status { color: var(--ink-4); font-size: .75rem; }
.agent-preview { margin-top:auto; border-top:1px solid var(--paper-3); padding-top:var(--space-3); }.agent-preview-note { margin:0; color:var(--ink-4); font-size:.72rem; line-height:1.55; }.agent-recommendations,.agent-graph-preview { margin:0 0 var(--space-2); }.agent-preview small { display:block; color:var(--sage-0); font-size:.66rem; font-weight:700; letter-spacing:.08em; }.agent-capsule { margin:0 0 var(--space-2); border:1px solid var(--paper-3); border-radius:var(--radius-sm); padding:7px 8px; background:var(--paper-1); }.agent-capsule summary { color:var(--ink-1); font-size:.75rem; font-weight:700; cursor:pointer; }.agent-capsule p { margin:6px 0; color:var(--ink-3); font-size:.7rem; line-height:1.5; }.agent-capsule small { font-weight:400; letter-spacing:0; }.agent-recommendation-card { display:block; width:100%; margin-top:6px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); padding:7px 8px; background:var(--paper-1); text-align:left; font:inherit; cursor:pointer; }.agent-recommendation-card:hover:not(:disabled) { border-color:var(--gold-1); }.agent-recommendation-card:disabled { cursor:default; }.agent-recommendation-card strong,.agent-recommendation-card span { display:block; }.agent-recommendation-card strong { color:var(--ink-1); font-size:.76rem; }.agent-recommendation-card span,.agent-graph-preview p { margin:3px 0 0; color:var(--ink-4); font-size:.68rem; line-height:1.45; }.agent-graph-nodes { display:flex; flex-wrap:wrap; gap:4px; margin-top:5px; }.agent-graph-nodes span { border-radius:99px; padding:3px 6px; background:var(--gold-3); color:var(--ink-1); font-size:.68rem; }
.agent-input { padding: var(--space-3); display: flex; flex-wrap:wrap; gap: var(--space-2); border-top: 1px solid var(--paper-3); }.agent-quick-tools { display:flex; flex:0 0 100%; align-items:center; gap:6px; }.agent-tool { border:1px solid var(--paper-3); border-radius:99px; padding:4px 8px; background:var(--paper-0); color:var(--ink-3); cursor:pointer; font:inherit; font-size:.68rem; }.agent-tool.active { border-color:var(--gold-1); background:var(--gold-3); color:var(--gold-0); }.agent-reference { color:var(--sage-0); font-size:.68rem; }.agent-shelf-picker { display:flex; flex:0 0 100%; align-items:center; gap:6px; padding:6px; border-radius:var(--radius-sm); background:var(--paper-1); }.agent-shelf-picker select { flex:1; min-width:0; padding:5px 7px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-0); color:var(--ink-1); font:inherit; font-size:.7rem; }.agent-shelf-picker small { color:var(--ink-4); font-size:.68rem; }
.agent-input textarea { flex: 1; min-height: 42px; border: 1px solid var(--paper-3); border-radius: var(--radius-md); padding: 8px; resize: none; font: inherit; font-size: .82rem; outline-color: var(--gold-1); }
.agent-send { border: 0; border-radius: var(--radius-md); padding: 0 12px; color: var(--paper-0); background: var(--ink-0); cursor: pointer; font-size: .78rem; }
.agent-send:disabled { opacity: .5; cursor: not-allowed; }
.agent-foot { padding: 0 var(--space-4) var(--space-3); text-align: right; font-size: .76rem; }
.agent-panel-enter-active,.agent-panel-leave-active { transition: opacity var(--transition-base), transform var(--transition-base); }
.agent-panel-enter-from,.agent-panel-leave-to { opacity: 0; transform: translateY(12px) scale(.98); }
@media (max-width: 600px) { .agent-popover { position: fixed; left: 12px; right: auto; bottom: 86px; width: calc(100vw - 24px); height: var(--agent-mobile-height, min(72vh, 620px)); border-radius: 20px 20px var(--radius-xl) var(--radius-xl); } .agent-mobile-handle { display: block; flex: 0 0 24px; border: 0; background: linear-gradient(var(--paper-1), var(--paper-0)); cursor: ns-resize; touch-action: none; } .agent-mobile-handle::after { content: ''; display: block; width: 38px; height: 4px; margin: 10px auto; border-radius: 99px; background: var(--paper-4); } }
</style>
