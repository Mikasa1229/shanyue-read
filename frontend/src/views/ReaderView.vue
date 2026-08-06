<template>
  <div class="reader-page" :class="bgClass" :style="{ fontSize: fontSize + 'px', fontFamily: fontFamily, '--reader-font-size': fontSize + 'px' }">

    <!-- 顶部栏 -->
    <div class="reader-topbar" :class="{ visible: uiVisible || atTop }">
      <button class="topbar-btn" @click="goBack">←</button>
      <div class="topbar-center">
        <div class="topbar-bookname">{{ bookName }}</div>
        <div class="topbar-chapter">{{ currentChapterName }}</div>
      </div>
      <div class="topbar-actions">
        <button v-if="userStore.isLoggedIn" class="topbar-btn topbar-collect" :class="{ 'collected': isFavorited }" @click="addToShelf">
          {{ isFavorited ? '♥' : '♡' }}
        </button>
        <button class="topbar-btn" title="写书评" @click="shareOpen = true">书评</button>
        <button class="topbar-btn" :class="{ 'topbar-bookmarked': isCurrentBookmarked }" title="书签" @click="toggleBookmark">🔖</button>
        <button class="topbar-btn" title="目录" @click="tocOpen = true">☰</button>
        <button class="topbar-btn" title="设置" @click="settingsOpen = true">⚙</button>
      </div>
    </div>

    <!-- 正文区域 -->
    <div class="reader-content" ref="contentEl" :style="{ maxWidth: pageWidth }" @click="uiVisible = !uiVisible">
      <div v-if="initialLoading" class="reader-loading">
        <div class="spinner"></div>
        <p>加载中…</p>
      </div>
      <template v-else>
        <div ref="topTrigger" class="top-trigger"></div>
        <div v-if="loadingPrev" class="chunk-loading">正在加载上一章…</div>
          <div v-for="chunk in loadedChunks" :key="chunk.chapterUrl" class="chapter-chunk" :ref="el => setChapterChunkRef(el, chunk.chapterUrl)">
          <div class="chapter-divider">{{ chunk.chapterName }}</div>
          <div v-if="chunk.loading" class="chunk-loading">加载中…</div>
          <div v-else-if="chunk.error" class="chunk-error">{{ chunk.error }}</div>
          <div v-else class="chapter-text" v-html="chunk.html"></div>
        </div>
        <!-- 底部触发器 -->
        <div ref="bottomTrigger" class="bottom-trigger"></div>
        <div v-if="loadingNext" class="chunk-loading">正在加载下一章…</div>
        <div v-if="noMoreChapters" class="no-more">— 全书完 —</div>
      </template>
    </div>

    <!-- 底部导航栏 -->
    <div class="reader-bottombar" :class="{ visible: uiVisible || atTop }">
      <button class="nav-btn" :disabled="currentIndex <= 0" @click="jumpToChapter(currentIndex - 1)">上一章</button>
      <div class="bottom-center">
        <span class="progress-text">{{ currentIndex + 1 }} / {{ chapters.length }}</span>
        <button class="bookmark-btn" :class="{ active: isCurrentBookmarked }" @click="toggleBookmark">
          {{ isCurrentBookmarked ? '已书签' : '加书签' }}
        </button>
        <button class="bookmark-btn" @click="bookmarkOpen = true">书签列表</button>
      </div>
      <button class="nav-btn" :disabled="currentIndex >= chapters.length - 1" @click="jumpToChapter(currentIndex + 1)">下一章</button>
    </div>

    <!-- 设置面板 -->
    <Teleport to="body">
      <div v-if="settingsOpen" class="settings-overlay" @click.self="settingsOpen = false">
        <div class="settings-panel" :style="bgStyle">
          <div class="settings-header">阅读设置</div>

          <div class="settings-row">
            <span class="settings-label">字号</span>
            <div class="settings-options">
              <button v-for="s in fontSizes" :key="s.val"
                      class="settings-btn" :class="{ active: fontSize === s.val }"
                      @click="setFontSize(s.val)">{{ s.label }}</button>
            </div>
          </div>

          <div class="settings-row">
            <span class="settings-label">字体</span>
            <div class="settings-options">
              <button v-for="f in fontFamilies" :key="f.val"
                      class="settings-btn" :class="{ active: fontFamily === f.val }"
                      :style="{ fontFamily: f.val }"
                      @click="setFontFamily(f.val)">{{ f.label }}</button>
            </div>
          </div>

          <div class="settings-row">
            <span class="settings-label">背景</span>
            <div class="settings-options">
              <button v-for="b in bgOptions" :key="b.key"
                      class="bg-btn" :class="{ active: bgKey === b.key }"
                      :style="{ background: b.bg, color: b.color, border: bgKey === b.key ? '2px solid #c8a96e' : '2px solid transparent' }"
                      @click="setBg(b.key)">{{ b.label }}</button>
            </div>
          </div>

          <div class="settings-row">
            <span class="settings-label">宽度</span>
            <div class="settings-options">
              <button v-for="w in widthOptions" :key="w.val"
                      class="settings-btn" :class="{ active: pageWidth === w.val }"
                      @click="setPageWidth(w.val)">{{ w.label }}</button>
            </div>
          </div>

          <div class="settings-row">
            <span class="settings-label">预载</span>
            <div class="settings-options">
              <button v-for="p in preloadOptions" :key="p.val"
                      class="settings-btn" :class="{ active: preloadCount === p.val }"
                      @click="setPreloadCount(p.val)">{{ p.label }}</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 目录侧边栏 -->
    <Teleport to="body">
      <div v-if="tocOpen" class="toc-overlay" @click.self="tocOpen = false">
        <div class="toc-panel">
          <div class="toc-header">
            <span>目录</span>
            <button class="toc-close" @click="tocOpen = false">✕</button>
          </div>
          <div class="toc-list" ref="tocListEl">
            <div v-for="(ch, idx) in chapters" :key="ch.chapterUrl"
                 class="toc-item" :class="{ active: idx === currentIndex }"
                 @click="jumpToChapter(idx); tocOpen = false">
              <span class="toc-num">{{ idx + 1 }}</span>
              <span class="toc-name">{{ ch.chapterName }}</span>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 书评弹窗 -->
    <Teleport to="body">
      <div v-if="shareOpen" class="settings-overlay" @click.self="shareOpen = false">
        <div class="settings-panel" :style="bgStyle">
          <div class="settings-header">写书评</div>
          <!-- 书名（固定显示，不可编辑） -->
          <div class="share-book-badge">
            <span class="share-book-icon">📖</span>
            <span class="share-book-name">《{{ bookName }}》</span>
          </div>
          <div class="share-score-row">
            <span class="share-score-label">我的评分</span>
            <div class="share-stars">
              <button
                v-for="star in 5"
                :key="star"
                class="share-star"
                :class="{ active: star <= shareScore }"
                @click="shareScore = star"
              >★</button>
            </div>
            <span class="share-score-val">{{ shareScore }} 分</span>
          </div>
          <div class="settings-row" style="flex-direction: column; align-items: stretch; gap: 12px;">
            <textarea v-model="shareContent" class="share-textarea" :placeholder="`写下你对《${bookName}》的感想，推荐给书友…`" rows="5"></textarea>
            <button class="settings-btn" style="align-self: flex-end; padding: 8px 24px;"
                    :disabled="shareSubmitting" @click="submitShare">
              {{ shareSubmitting ? '发布中…' : '发布书评' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 书签面板 -->
    <Teleport to="body">
      <div v-if="bookmarkOpen" class="toc-overlay" @click.self="bookmarkOpen = false">
        <div class="toc-panel">
          <div class="toc-header">
            <span>我的书签</span>
            <button class="toc-close" @click="bookmarkOpen = false">✕</button>
          </div>
          <div v-if="bookmarks.length === 0" class="bookmark-empty">暂无书签，点击 🔖 添加</div>
          <div v-else class="toc-list">
            <div v-for="(bm, i) in bookmarks" :key="i" class="toc-item bookmark-item"
                 @click="jumpToChapter(bm.chapterIndex); bookmarkOpen = false">
              <div class="bookmark-info">
                <span class="toc-name">{{ bm.chapterName }}</span>
                <span class="bookmark-time">{{ formatBmTime(bm.savedAt) }}</span>
              </div>
              <button class="bookmark-del" @click.stop="deleteBookmark(i)">✕</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { apiGetChapters, apiGetContent } from '@/api/bookSource'
import { apiUpdateReadingProgress } from '@/api/bookshelf'
import { apiReadingHeartbeat, apiStartReadingSession } from '@/api/reading'
import { apiAddFavorite, apiCheckFavorited } from '@/api/favorite'
import { apiCreateComment } from '@/api/comment'
import { useToast } from '@/composables/useToast'

const route  = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { show } = useToast()

// ─── query 参数 ──────────────────────────────────────────────
const sourceId    = computed(() => route.query.sourceId)
const sourceName  = computed(() => route.query.sourceName || '')
const bookUrl     = computed(() => route.query.bookUrl)
const bookName    = computed(() => route.query.bookName || '未知书名')
const bookAuthor  = computed(() => route.query.author || '')
const bookCoverUrl = computed(() => route.query.coverUrl || '')
const bookIntro   = computed(() => route.query.intro || '')
const initChapterUrl   = computed(() => route.query.chapterUrl)
const initChapterIndex = computed(() => parseInt(route.query.chapterIndex) || 0)

// ─── 章节数据 ─────────────────────────────────────────────────
const chapters     = ref([])
const initialLoading = ref(true)
const loadedChunks = ref([])   // { chapterName, chapterUrl, html, loading, error }
const currentIndex = ref(0)
const loadingNext  = ref(false)
const noMoreChapters = ref(false)
const isFavorited = ref(false)
let readerOpenTime = 0
let readingSessionToken = ''
let readingHeartbeatTimer = 0
let lastReportedChapter = -1

const currentChapterName = computed(() => chapters.value[currentIndex.value]?.chapterName ?? '')

// ─── 设置 ─────────────────────────────────────────────────────
const fontSizes   = [{ val: 14, label: '小' }, { val: 18, label: '中' }, { val: 22, label: '大' }, { val: 26, label: '特大' }]
const fontFamilies = [
  { val: "'Noto Serif SC', 宋体, serif", label: '宋体' },
  { val: "'Noto Sans SC', 黑体, sans-serif", label: '黑体' },
  { val: "楷体, KaiTi, serif", label: '楷体' }
]
const bgOptions = [
  { key: 'white',  bg: '#ffffff', color: '#1a1a1a', label: '白' },
  { key: 'rice',   bg: '#f5f0e8', color: '#3a3228', label: '米' },
  { key: 'green',  bg: '#e8f0e8', color: '#2a3a2a', label: '绿' },
  { key: 'dark',   bg: '#1a1a1a', color: '#c8c8c8', label: '夜' }
]

const fontSize   = ref(parseInt(localStorage.getItem('reader_fontSize') ?? '18'))
const fontFamily = ref(localStorage.getItem('reader_fontFamily') ?? "'Noto Serif SC', 宋体, serif")
const bgKey      = ref(localStorage.getItem('reader_bgKey') ?? 'rice')

const bgClass = computed(() => `bg-${bgKey.value}`)
const bgStyle = computed(() => {
  const opt = bgOptions.find(b => b.key === bgKey.value) ?? bgOptions[1]
  return { background: opt.bg, color: opt.color }
})

// ─── 页面宽度 ─────────────────────────────────────────────────
const widthOptions = [
  { val: '500px', label: '窄' },
  { val: '680px', label: '中' },
  { val: '900px', label: '宽' },
  { val: '100%',  label: '全' }
]
const pageWidth = ref(localStorage.getItem('reader_pageWidth') ?? '680px')
function setPageWidth(v) { pageWidth.value = v; localStorage.setItem('reader_pageWidth', v) }

// ─── 预加载设置 ───────────────────────────────────────────────
const preloadOptions = [
  { val: 0, label: '关' },
  { val: 1, label: '1章' },
  { val: 2, label: '2章' },
  { val: 3, label: '3章' },
  { val: 5, label: '5章' },
  { val: 8, label: '8章' },
  { val: 10, label: '10章' }
]
const preloadCount = ref(Math.min(10, Math.max(0, parseInt(localStorage.getItem('reader_preloadCount') ?? '1') || 1)))
function setPreloadCount(v) {
  const safe = Math.min(10, Math.max(0, v))
  preloadCount.value = safe
  localStorage.setItem('reader_preloadCount', safe)
}

function setFontSize(v)   { fontSize.value = v;   localStorage.setItem('reader_fontSize', v) }
function setFontFamily(v) { fontFamily.value = v; localStorage.setItem('reader_fontFamily', v) }
function setBg(v)         { bgKey.value = v;      localStorage.setItem('reader_bgKey', v) }

// ─── UI 显示控制 ──────────────────────────────────────────────
const uiVisible = ref(true)
const atTop     = ref(true)
const settingsOpen = ref(false)
const tocOpen      = ref(false)
const bookmarkOpen = ref(false)

// ─── 书签 ─────────────────────────────────────────────────────
const bookmarkKey = computed(() => bookUrl.value ? `reader_bookmarks_${bookUrl.value}` : null)

const bookmarks = ref([])

function loadBookmarks() {
  if (!bookmarkKey.value) return
  try {
    bookmarks.value = JSON.parse(localStorage.getItem(bookmarkKey.value) || '[]')
  } catch { bookmarks.value = [] }
}

function saveBookmarks() {
  if (!bookmarkKey.value) return
  localStorage.setItem(bookmarkKey.value, JSON.stringify(bookmarks.value))
}

const isCurrentBookmarked = computed(() =>
  bookmarks.value.some(b => b.chapterIndex === currentIndex.value)
)

function toggleBookmark() {
  const idx = currentIndex.value
  const ch  = chapters.value[idx]
  if (!ch) return
  const existing = bookmarks.value.findIndex(b => b.chapterIndex === idx)
  if (existing >= 0) {
    bookmarks.value.splice(existing, 1)
    saveBookmarks()
    show('已移除书签')
  } else {
    bookmarks.value.unshift({ chapterIndex: idx, chapterName: ch.chapterName, savedAt: Date.now() })
    if (bookmarks.value.length > 30) bookmarks.value.pop()
    saveBookmarks()
    bookmarkOpen.value = true  // 添加后打开书签面板
    show('已添加书签')
  }
}

function deleteBookmark(i) {
  bookmarks.value.splice(i, 1)
  saveBookmarks()
}

function formatBmTime(ts) {
  const d = new Date(ts)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

// ─── DOM refs ─────────────────────────────────────────────────
const bottomTrigger = ref(null)
const topTrigger    = ref(null)
const tocListEl     = ref(null)
let observer    = null
let topObserver = null
const chapterChunkRefs = new Map()
let chapterSyncRaf = 0

function setChapterChunkRef(el, chapterUrl) {
  if (!chapterUrl) return
  if (el) {
    chapterChunkRefs.set(chapterUrl, el)
  } else {
    chapterChunkRefs.delete(chapterUrl)
  }
}

// ─── 收藏 ────────────────────────────────────────────────────
async function addToShelf() {
  if (isFavorited.value) return
  try {
    await apiAddFavorite({
      sourceId: sourceId.value,
      sourceName: sourceName.value,
      bookName: bookName.value,
      author: bookAuthor.value,
      coverUrl: bookCoverUrl.value,
      bookUrl: bookUrl.value,
    })
    isFavorited.value = true
  } catch (e) {
    // 忽略
  }
}

// ─── 广场分享 ─────────────────────────────────────────────────
const shareOpen      = ref(false)
const shareContent   = ref('')
const shareScore     = ref(4)
const shareSubmitting = ref(false)

async function submitShare() {
  if (!shareContent.value.trim()) return
  shareSubmitting.value = true
  try {
    await apiCreateComment({
      bookTitle: bookName.value,
      sourceId: sourceId.value ? Number(sourceId.value) : undefined,
      bookUrl: bookUrl.value,
      bookAuthor: bookAuthor.value,
      bookCoverUrl: bookCoverUrl.value,
      bookIntro: bookIntro.value,
      score: shareScore.value,
      content: shareContent.value.trim()
    })
    shareOpen.value = false
    shareContent.value = ''
    shareScore.value = 4
  } catch (e) {
    // 忽略
  } finally {
    shareSubmitting.value = false
  }
}

function reportReadDuration() {
  readerOpenTime = 0
}

async function beginVerifiedReadingSession() {
  if (!userStore.isLoggedIn || !bookUrl.value) return
  try {
    const session = await apiStartReadingSession(bookUrl.value)
    readingSessionToken = session.sessionToken
    readingHeartbeatTimer = window.setInterval(() => {
      if (!readingSessionToken) return
      apiReadingHeartbeat(readingSessionToken, document.visibilityState === 'visible').catch(() => {})
    }, 45_000)
  } catch (_) {
    // Legacy duration tracking remains available if the session service is temporarily unavailable.
  }
}

// ─── 段落格式化 ───────────────────────────────────────────────
function formatContent(raw) {
  return (raw || '')
    .split('\n')
    .map(l => l.trim())
    .filter(l => l.length > 0)
    .map(l => `<p>${l}</p>`)
    .join('')
}

// ─── 加载章节内容 ─────────────────────────────────────────────
// isPreload=true 时不再触发下一级预加载，防止级联
async function loadChapterContent(idx, isPreload = false) {
  if (idx < 0 || idx >= chapters.value.length) return
  const ch = chapters.value[idx]
  const existing = loadedChunks.value.find(c => c.chapterUrl === ch.chapterUrl)
  if (existing) return

  const chunk = { chapterIndex: idx, chapterName: ch.chapterName, chapterUrl: ch.chapterUrl, html: '', loading: true, error: '' }
  loadedChunks.value.push(chunk)

  try {
    const res = await apiGetContent(sourceId.value, ch.chapterUrl, bookUrl.value, idx)
    const raw = res?.content ?? ''
    chunk.html = formatContent(raw) || '<p>（正文内容为空）</p>'
    chunk.loading = false
    if (!isPreload) {
      currentIndex.value = idx
      // 上报阅读进度（仅主动加载才上报）
      reportChapterProgress(idx)
    }
    if (!isPreload) {
      ensurePreloadWindow(idx)
    }
  } catch (e) {
    chunk.loading = false
    chunk.error = e.message
  }
}

function reportChapterProgress(idx) {
  const chapter = chapters.value[idx]
  if (!userStore.isLoggedIn || !chapter || idx === lastReportedChapter) return
  lastReportedChapter = idx
  apiUpdateReadingProgress({
    bookUrl: bookUrl.value,
    chapterName: chapter.chapterName,
    chapterUrl: chapter.chapterUrl,
    chapterIndex: idx,
    totalChapters: chapters.value.length
  }).catch(() => { lastReportedChapter = -1 })
  // Keep every Agent entry point bound to the chapter actually on screen.
  if (String(route.query.chapterIndex ?? '') !== String(idx)) {
    router.replace({ query: { ...route.query, chapterIndex: String(idx) } })
  }
}

function ensurePreloadWindow(centerIdx) {
  if (preloadCount.value <= 0) return
  const limit = Math.min(chapters.value.length - 1, centerIdx + preloadCount.value)
  for (let idx = centerIdx + 1; idx <= limit; idx++) {
    loadChapterContent(idx, true)
  }
}

// ─── 加载上一章 ───────────────────────────────────────────────
const loadingPrev = ref(false)

async function loadPrevChapter() {
  const firstChunk = loadedChunks.value[0]
  if (!firstChunk) return
  const firstIdx = chapters.value.findIndex(c => c.chapterUrl === firstChunk.chapterUrl)
  const prevIdx = firstIdx - 1
  if (prevIdx < 0) return

  loadingPrev.value = true
  const prevCh = chapters.value[prevIdx]
  const chunk = { chapterIndex: prevIdx, chapterName: prevCh.chapterName, chapterUrl: prevCh.chapterUrl, html: '', loading: true, error: '' }
  loadedChunks.value.unshift(chunk)

  try {
    const res = await apiGetContent(sourceId.value, prevCh.chapterUrl, bookUrl.value, prevIdx)
    const raw = res?.content ?? ''
    chunk.html = formatContent(raw) || '<p>（正文内容为空）</p>'
    chunk.loading = false
    currentIndex.value = prevIdx
    reportChapterProgress(prevIdx)
    ensurePreloadWindow(prevIdx)
  } catch (e) {
    chunk.error = e.message
    chunk.loading = false
  } finally {
    loadingPrev.value = false
  }
}

function setupTopObserver() {
  if (!topTrigger.value) return
  topObserver = new IntersectionObserver(async (entries) => {
    if (!entries[0].isIntersecting) return
    if (loadingPrev.value) return
    const firstChunk = loadedChunks.value[0]
    if (!firstChunk) return
    const firstIdx = chapters.value.findIndex(c => c.chapterUrl === firstChunk.chapterUrl)
    if (firstIdx <= 0) return
    await loadPrevChapter()
  }, { threshold: 0.1 })
  topObserver.observe(topTrigger.value)
}

// ─── 无缝加载下一章 ───────────────────────────────────────────
function setupBottomObserver() {
  if (!bottomTrigger.value) return
  observer = new IntersectionObserver(async (entries) => {
    if (!entries[0].isIntersecting) return
    if (loadingNext.value || noMoreChapters.value) return
    const lastChunk = loadedChunks.value[loadedChunks.value.length - 1]
    const lastIdx = lastChunk?.chapterIndex ?? currentIndex.value
    const nextIdx = lastIdx + 1
    if (nextIdx >= chapters.value.length) { noMoreChapters.value = true; return }
    loadingNext.value = true
    await loadChapterContent(nextIdx)
    loadingNext.value = false
  }, { threshold: 0.1 })
  observer.observe(bottomTrigger.value)
}

// ─── 跳转到指定章节 ───────────────────────────────────────────
async function jumpToChapter(idx) {
  if (idx < 0 || idx >= chapters.value.length) return
  loadedChunks.value = []
  noMoreChapters.value = false
  currentIndex.value = idx
  await loadChapterContent(idx)
  await nextTick()
  syncCurrentChapterFromView(true)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function syncCurrentChapterFromView(force = false) {
  if (initialLoading.value || !loadedChunks.value.length) return
  const anchor = 96
  let activeIdx = currentIndex.value
  for (const chunk of loadedChunks.value) {
    const el = chapterChunkRefs.get(chunk.chapterUrl)
    if (!el) continue
    const rect = el.getBoundingClientRect()
    if (rect.top <= anchor) {
      activeIdx = chunk.chapterIndex
    } else {
      break
    }
  }
  if (force || activeIdx !== currentIndex.value) {
    currentIndex.value = activeIdx
    reportChapterProgress(activeIdx)
    ensurePreloadWindow(activeIdx)
  }
}

function scheduleChapterSync() {
  if (chapterSyncRaf) return
  chapterSyncRaf = window.requestAnimationFrame(() => {
    chapterSyncRaf = 0
    syncCurrentChapterFromView()
  })
}

// ─── 滚动监听 ─────────────────────────────────────────────────
function onScroll() {
  atTop.value = window.scrollY < 60
  scheduleChapterSync()
}

// ─── 返回 ─────────────────────────────────────────────────────
function goBack() {
  reportReadDuration()
  router.back()
}

// ─── 初始化 ───────────────────────────────────────────────────
onMounted(async () => {
  readerOpenTime = Date.now()
  window.addEventListener('scroll', onScroll)
  beginVerifiedReadingSession()
  loadBookmarks()

  // 检查是否已收藏
  if (userStore.isLoggedIn && bookUrl.value) {
    apiCheckFavorited(bookUrl.value).then(res => {
      isFavorited.value = res?.favorited ?? false
    }).catch(() => {})
  }

  // 获取章节列表
  try {
    const list = await apiGetChapters(sourceId.value, bookUrl.value)
    chapters.value = list ?? []
  } catch (e) {
    initialLoading.value = false
    return
  }

  // 确定起始章节
  let startIdx = initChapterIndex.value
  if (initChapterUrl.value) {
    const found = chapters.value.findIndex(c => c.chapterUrl === initChapterUrl.value)
    if (found >= 0) startIdx = found
  }
  startIdx = Math.max(0, Math.min(startIdx, chapters.value.length - 1))
  currentIndex.value = startIdx
  initialLoading.value = false

  await loadChapterContent(startIdx)
  await nextTick()
  syncCurrentChapterFromView(true)
  setupBottomObserver()
  setupTopObserver()

  // 目录滚动到当前章节
  watch(tocOpen, (val) => {
    if (val) nextTick(() => {
      const el = tocListEl.value?.children[currentIndex.value]
      el?.scrollIntoView({ block: 'center' })
    })
  })
})

onUnmounted(() => {
  observer?.disconnect()
  topObserver?.disconnect()
  if (chapterSyncRaf) window.cancelAnimationFrame(chapterSyncRaf)
  if (readingHeartbeatTimer) window.clearInterval(readingHeartbeatTimer)
  if (readingSessionToken) apiReadingHeartbeat(readingSessionToken, document.visibilityState === 'visible').catch(() => {})
  window.removeEventListener('scroll', onScroll)
  reportReadDuration()
})
</script>

<style scoped>
/* ── 基础布局 ── */
.reader-page {
  min-height: 100vh;
  padding: 56px 0 56px;
  transition: background 0.3s, color 0.3s;
}
.bg-white { --reader-bg: #ffffff; --reader-ink: #1a1a1a; background: var(--reader-bg); color: var(--reader-ink); }
.bg-rice  { --reader-bg: #f5f0e8; --reader-ink: #3a3228; background: var(--reader-bg); color: var(--reader-ink); }
.bg-green { --reader-bg: #e8f0e8; --reader-ink: #2a3a2a; background: var(--reader-bg); color: var(--reader-ink); }
.bg-dark  { --reader-bg: #1a1a1a; --reader-ink: #f5f5f5; background: var(--reader-bg); color: var(--reader-ink); }

/* ── 顶部栏 ── */
.reader-topbar {
  position: fixed; top: 0; left: 0; right: 0; height: 56px;
  display: flex; align-items: center; gap: 8px;
  padding: 0 12px;
  background: var(--reader-bg);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(128,128,128,0.15);
  z-index: 100;
  opacity: 0; pointer-events: none;
  transition: opacity 0.25s;
}
.reader-topbar.visible { opacity: 1; pointer-events: auto; }
.topbar-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  width: min(62vw, 460px);
  min-width: 0;
  text-align: center;
  pointer-events: none;
}
.topbar-bookname { font-size: 0.875rem; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.topbar-chapter  { font-size: 0.75rem; opacity: 0.6; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.topbar-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
  position: relative;
  z-index: 1;
}
.topbar-btn {
  border: none; background: transparent; font-size: 1.25rem;
  cursor: pointer; color: inherit; padding: 6px 8px;
  border-radius: 6px; flex-shrink: 0;
}
.topbar-btn:hover { background: rgba(128,128,128,0.15); }
.topbar-collect { font-size: 1.4rem; line-height: 1; }
.topbar-collect.collected { color: #e05a7a; }
.topbar-bookmarked { color: #c8a96e; }

/* ── 正文区域 ── */
.reader-content {
  margin: 0 auto;
  padding: 20px 24px 40px;
  cursor: pointer;
  user-select: text;
  transition: max-width 0.3s;
}
.top-trigger { height: 1px; }
.chapter-divider {
  text-align: center; font-size: calc(var(--reader-font-size) * 0.95); font-weight: 600;
  margin: 36px 0 20px;
  padding: 12px 0;
  border-top: 1px solid rgba(128,128,128,0.2);
  border-bottom: 1px solid rgba(128,128,128,0.2);
  opacity: 0.75;
}
.chapter-chunk:first-child .chapter-divider {
  margin-top: 0;
  border-top: none;
}
.chapter-text :deep(p) {
  font-size: var(--reader-font-size);
  line-height: 1.9;
  margin: 0 0 0.75em;
  text-indent: 2em;
  word-break: break-all;
}
.chunk-loading, .chunk-error {
  text-align: center; padding: 20px; font-size: 0.875rem; opacity: 0.6;
}
.chunk-error { color: #ef4444; }
.bottom-trigger { height: 1px; }
.no-more {
  text-align: center; padding: 32px; font-size: 0.875rem;
  opacity: 0.5; letter-spacing: 4px;
}
.reader-loading { display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 80px 0; opacity: 0.6; }
.spinner {
  width: 32px; height: 32px;
  border: 3px solid rgba(128,128,128,0.3); border-top-color: currentColor;
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ── 底部导航栏 ── */
.reader-bottombar {
  position: fixed; bottom: 0; left: 0; right: 0; height: 56px;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 20px;
  background: var(--reader-bg);
  backdrop-filter: blur(8px);
  border-top: 1px solid rgba(128,128,128,0.15);
  z-index: 100;
  opacity: 0; pointer-events: none;
  transition: opacity 0.25s;
}
.reader-bottombar.visible { opacity: 1; pointer-events: auto; }
.nav-btn {
  border: 1px solid rgba(128,128,128,0.3); background: transparent;
  color: inherit; padding: 6px 18px; border-radius: 20px;
  cursor: pointer; font-size: 0.875rem;
  transition: background 0.15s;
}
.nav-btn:hover:not(:disabled) { background: rgba(128,128,128,0.12); }
.nav-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.progress-text { font-size: 0.8125rem; opacity: 0.6; }

.bottom-center {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bookmark-btn {
  border: 1px solid rgba(128,128,128,0.28);
  background: transparent;
  color: inherit;
  padding: 5px 10px;
  border-radius: 16px;
  font-size: 0.75rem;
  cursor: pointer;
}

.bookmark-btn.active {
  border-color: #c8a96e;
  color: #c8a96e;
}

/* ── 设置面板 ── */
.settings-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.3); z-index: 300;
  display: flex; align-items: flex-end;
}
.settings-panel {
  width: 100%;
  padding: 20px 24px 36px;
  border-radius: 16px 16px 0 0;
  animation: slideUp 0.2s ease;
}
.share-textarea {
  width: 100%; border: 1.5px solid rgba(128,128,128,0.3);
  border-radius: 8px; padding: 10px 12px;
  font-size: 0.9375rem; color: inherit;
  background: transparent; resize: vertical;
  font-family: inherit; box-sizing: border-box;
}
.share-textarea:focus { outline: none; border-color: #c8a96e; }
@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
.settings-header { font-size: 1rem; font-weight: 600; margin-bottom: 20px; text-align: center; }
.settings-row {
  display: flex; align-items: center; gap: 16px;
  margin-bottom: 20px;
}
.settings-label { font-size: 0.875rem; opacity: 0.7; min-width: 36px; }
.settings-options { display: flex; gap: 8px; flex-wrap: wrap; }
.settings-btn {
  padding: 6px 16px; border: 1.5px solid rgba(128,128,128,0.3);
  border-radius: 20px; background: transparent; color: inherit;
  font-size: 0.875rem; cursor: pointer; transition: all 0.15s;
}
.settings-btn.active { border-color: #c8a96e; background: rgba(200,169,110,0.15); }
.bg-btn {
  width: 44px; height: 44px; border-radius: 50%;
  font-size: 0.75rem; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: transform 0.15s;
}
.bg-btn.active { transform: scale(1.15); }

/* ── 目录侧边栏 ── */
.toc-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.45); z-index: 300;
  display: flex; justify-content: flex-end;
}
.toc-panel {
  width: min(320px, 80vw); background: var(--paper-0);
  height: 100%; display: flex; flex-direction: column;
  animation: slideRight 0.2s ease;
}
@keyframes slideRight { from { transform: translateX(100%); } to { transform: translateX(0); } }
.toc-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; border-bottom: 1px solid var(--paper-3);
  font-weight: 600; color: var(--ink-0);
}
.toc-close { border: none; background: transparent; font-size: 1rem; cursor: pointer; color: var(--ink-3); }
.toc-list  { flex: 1; overflow-y: auto; }
.toc-item  {
  display: flex; align-items: baseline; gap: 10px;
  padding: 10px 20px; cursor: pointer; color: var(--ink-1);
  border-bottom: 1px solid var(--paper-2);
  transition: background 0.12s;
}
.toc-item:hover  { background: var(--paper-1); }
.toc-item.active { background: var(--paper-2); color: var(--ink-0); font-weight: 600; }
.toc-num  { font-size: 0.75rem; color: var(--ink-4); min-width: 28px; }
.toc-name { font-size: 0.875rem; line-height: 1.4; }

/* ── 书评弹窗书名 ── */
.share-book-badge {
  display: flex; align-items: center; gap: 8px;
  background: rgba(200,169,110,0.12);
  border: 1.5px solid rgba(200,169,110,0.35);
  border-radius: 8px; padding: 8px 14px;
  margin-bottom: 12px;
}
.share-book-icon { font-size: 1rem; }
.share-book-name { font-weight: 600; font-size: 0.9375rem; color: #c8a96e; }

.share-score-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.share-score-label { font-size: 0.875rem; opacity: 0.75; }
.share-stars { display: inline-flex; gap: 2px; }
.share-star {
  border: none;
  background: transparent;
  color: rgba(128,128,128,0.45);
  font-size: 1.1rem;
  cursor: pointer;
  padding: 0 2px;
}
.share-star.active { color: #c8a96e; }
.share-score-val { font-size: 0.8125rem; color: #c8a96e; }

/* ── 书签面板 ── */
.bookmark-empty {
  text-align: center; padding: 40px 20px;
  font-size: 0.875rem; opacity: 0.5;
}
.bookmark-item { justify-content: space-between; }
.bookmark-info { display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0; }
.bookmark-time { font-size: 0.7rem; color: var(--ink-4); }
.bookmark-del {
  border: none; background: transparent; color: var(--ink-4);
  font-size: 0.75rem; cursor: pointer; padding: 4px;
  border-radius: 4px; flex-shrink: 0;
}
.bookmark-del:hover { background: rgba(239,68,68,0.1); color: #ef4444; }

@media (max-width: 600px) {
  .reader-content { padding: 16px 16px 40px; }
}
</style>
