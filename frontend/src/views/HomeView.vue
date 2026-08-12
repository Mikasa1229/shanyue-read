<template>
  <main class="page">
    <div class="container">
      <!-- Hero banner -->
      <section class="hero">
        <div class="hero-content">
          <h1 class="hero-title">
            <span class="hero-line1">善阅坊</span>
            <span class="hero-line2">与书相遇，与人共鸣</span>
          </h1>
          <p class="hero-desc">精选小说阅读与书评分享社区，在文字中找到共鸣</p>
          <div class="hero-actions">
            <router-link to="/register" class="btn btn-gold" v-if="!userStore.isLoggedIn">
              开始阅读
            </router-link>
            <router-link to="/bookshelf" class="btn btn-ghost" v-if="userStore.isLoggedIn">
              我的书架
            </router-link>
          </div>
        </div>
        <div class="hero-deco" aria-hidden="true">
          <div class="deco-circle c1"></div>
          <div class="deco-circle c2"></div>
          <div class="deco-chars">
            <span>字</span><span>里</span><span>行</span><span>间</span>
          </div>
        </div>
      </section>

      <!-- 搜索 + 书源结果 -->
      <section class="section">
        <div class="section-header">
          <h2 class="section-title">发现好书</h2>
          <div class="toolbar">
            <div class="search-wrap">
              <input
                v-model="keyword"
                class="search-input"
                placeholder="搜索书名、作者，聚合所有书源…"
                @keydown.enter="doSearch"
              />
              <button class="search-btn" :disabled="loading" @click="doSearch">
                {{ loading ? '搜索中…' : '搜索' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 搜索进度条 -->
        <div v-if="loading" class="search-progress-wrap">
          <div class="search-progress-bar">
            <div class="search-progress-fill" :style="{ width: searchProgress + '%' }"></div>
          </div>
          <p class="search-progress-text">正在并发搜索所有书源… {{ searchProgress }}%</p>
        </div>

        <!-- 搜索结果 -->
        <div v-else-if="searched && results.length === 0" class="empty-state">
          <div class="empty-icon">🔍</div>
          <p>没有找到「{{ lastKeyword }}」相关书籍</p>
        </div>

        <div v-else-if="searched">
          <!-- 章节列表面板（覆盖搜索结果） -->
          <div v-if="chapterBook.title" class="chapter-panel">
            <div class="chapter-header">
              <button class="back-btn" @click="clearChapters">← 返回搜索结果</button>
              <span class="chapter-book-title">《{{ chapterBook.title }}》目录</span>
            </div>
            <div v-if="chapterBook.loading" class="spinner-wrap"><div class="spinner"></div></div>
            <div v-else-if="chapterBook.list.length === 0" class="empty-state"><p>未获取到章节，可能该书源不支持此书目录</p></div>
            <ul v-else class="chapter-list">
              <li v-for="(ch, i) in chapterBook.list" :key="i" class="chapter-item" @click="openChapter(ch)">
                <span class="chapter-num">{{ i + 1 }}</span>
                <span class="chapter-name">{{ ch.chapterName }}</span>
              </li>
            </ul>
          </div>

          <!-- 搜索结果 -->
          <template v-else>
            <!-- 结果统计 + 分页信息 -->
            <div class="result-stat">
              共找到 <strong>{{ results.length }}</strong> 本书
              <span v-if="totalPages > 1">，第 {{ currentPage }} / {{ totalPages }} 页</span>
            </div>

            <!-- 书籍网格 -->
            <div class="book-grid">
              <div v-for="book in pagedResults" :key="bookResultKey(book)" class="book-card">
                <div class="book-cover-wrap" @click="goToDetail(book)">
                  <img
                    v-if="book.coverUrl"
                    :src="book.coverUrl"
                    class="book-cover"
                    :alt="book.name"
                    @error="(e) => e.target.style.display='none'"
                  />
                  <div v-else class="book-cover-placeholder">
                    <span>{{ book.name?.charAt(0) ?? '书' }}</span>
                  </div>
                </div>
                <div class="book-meta">
                  <div class="book-name-row"><div class="book-name">{{ book.name || '未知书名' }}</div><span v-if="isKnowledgeReady(book)" class="knowledge-badge">AI 知识库</span></div>
                  <div class="book-author">{{ book.author || '未知作者' }}</div>
                  <div v-if="book.sourceCount > 1" class="source-count">{{ book.sourceCount }} 个可用书源</div>
                  <div v-if="book.intro" class="book-intro">{{ book.intro }}</div>
                  <div class="book-actions mt-4">
                    <button class="btn btn-sm btn-ghost" @click="goToDetail(book)">查看详情</button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 分页控件 -->
            <div v-if="totalPages > 1" class="pagination">
              <button class="page-btn" :disabled="currentPage === 1" @click="currentPage--">‹ 上一页</button>
              <button
                v-for="p in pageNums"
                :key="p"
                class="page-btn"
                :class="{ 'page-btn-active': p === currentPage, 'page-btn-ellipsis': p === '…' }"
                :disabled="p === '…'"
                @click="p !== '…' && (currentPage = p)"
              >{{ p }}</button>
              <button class="page-btn" :disabled="currentPage === totalPages" @click="currentPage++">下一页 ›</button>
            </div>
          </template>
        </div>

        <!-- 未搜索状态：平台精选提示 -->
        <div v-else class="featured-placeholder">
          <div class="featured-icon">📖</div>
          <p class="featured-tip">暂无平台内容，可通过书源搜索发现好书</p>
          <router-link to="/book-sources" class="btn btn-gold btn-sm mt-4">管理书源</router-link>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { apiAggregateCanonicalSearch, apiGetChapters } from '@/api/bookSource'
import { apiGetBookKnowledgeStatuses } from '@/api/agent'
import { useToast } from '@/composables/useToast'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const { show } = useToast()

const keyword = ref('')
const lastKeyword = ref('')
const results = ref([])
const loading = ref(false)
const searched = ref(false)
const searchProgress = ref(0)

// ─── 分页 ─────────────────────────────────────────────────────────
const currentPage = ref(1)
const pageSize = 10

const totalPages = computed(() => Math.ceil(results.value.length / pageSize))

const pagedResults = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return results.value.slice(start, start + pageSize)
})

function normalizeBookField(value) {
  return String(value || '').replace(/\s+/g, ' ').trim().toLocaleLowerCase()
}

function bookResultKey(book) {
  return String(book.canonicalBookId || `${normalizeBookField(book.name)}|${normalizeBookField(book.author)}`)
}

// 生成显示页码（带省略号）
const pageNums = computed(() => {
  const total = totalPages.value
  const cur = currentPage.value
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages = []
  pages.push(1)
  if (cur > 3) pages.push('…')
  for (let p = Math.max(2, cur - 1); p <= Math.min(total - 1, cur + 1); p++) pages.push(p)
  if (cur < total - 2) pages.push('…')
  pages.push(total)
  return pages
})

let progressTimer = null

function startProgress() {
  searchProgress.value = 0
  clearInterval(progressTimer)
  progressTimer = setInterval(() => {
    if (searchProgress.value < 90) searchProgress.value += 3
    else clearInterval(progressTimer)
  }, 200)
}

function finishProgress() {
  clearInterval(progressTimer)
  searchProgress.value = 100
  setTimeout(() => { searchProgress.value = 0 }, 400)
}

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) { show('请输入搜索关键词'); return }
  loading.value = true
  searched.value = false
  currentPage.value = 1
  lastKeyword.value = q
  startProgress()
  try {
    const res = await apiAggregateCanonicalSearch(q)
    const searchResults = (res ?? []).map(book => ({
      ...book,
      sourceId: book.preferredSource?.sourceId,
      sourceName: book.preferredSource?.sourceName,
      bookUrl: book.preferredSource?.bookUrl,
      lastChapter: book.lastChapter || book.preferredSource?.lastChapter
    }))
    results.value = searchResults
    // Book-source results are already usable. Knowledge status is optional metadata from
    // another service, so it must never hold the visible search response hostage.
    searched.value = true
    const ids = [...new Set(searchResults.map(book => book.canonicalBookId).filter(Boolean))]
    if (ids.length) {
      void apiGetBookKnowledgeStatuses(ids)
        .then(statuses => {
          // Ignore an older asynchronous response after the user starts another search.
          if (lastKeyword.value !== q) return
          results.value = searchResults.map(book => ({ ...book, knowledgeStatus: statuses?.[book.canonicalBookId]?.status }))
        })
        .catch(() => { /* Search remains available if the optional Agent service is offline. */ })
    }
  } catch (e) {
    show(e.message)
    results.value = []
    searched.value = true
  } finally {
    finishProgress()
    loading.value = false
  }
}

function isKnowledgeReady (book) { return book.knowledgeStatus === 'READY' }

// ─── 章节列表 ──────────────────────────────────────────────────
const chapterBook = ref({ title: '', bookUrl: '', sourceId: '', sourceName: '', author: '', coverUrl: '', intro: '', canonicalBookId: '', list: [], loading: false })

function goToDetail(book) {
  if (!book?.bookUrl || !book?.sourceId) {
    show('缺少书籍详情参数')
    return
  }
  router.push({
    path: '/source-book-detail',
    query: {
      sourceId: book.sourceId,
      sourceName: book.sourceName,
      name: book.name,
      author: book.author,
      coverUrl: book.coverUrl,
      intro: book.intro,
      kind: book.kind,
      lastChapter: book.lastChapter,
      bookUrl: book.bookUrl,
      canonicalBookId: book.canonicalBookId
    }
  })
}

async function goToChapters(book) {
  if (!book.bookUrl) { show('该书源未返回书籍 URL'); return }
  if (!book.sourceId) { show('无法确定书源'); return }
  chapterBook.value = {
    title: book.name,
    bookUrl: book.bookUrl,
    sourceId: book.sourceId,
    sourceName: book.sourceName,
    author: book.author,
    coverUrl: book.coverUrl,
    intro: book.intro,
    canonicalBookId: book.canonicalBookId,
    list: [],
    loading: true
  }
  try {
    const res = await apiGetChapters(book.sourceId, book.bookUrl)
    chapterBook.value.list = res ?? []
  } catch (e) {
    show(e.message)
    chapterBook.value.list = []
  } finally {
    chapterBook.value.loading = false
  }
}

function clearChapters() {
  chapterBook.value = { title: '', bookUrl: '', sourceId: '', sourceName: '', author: '', coverUrl: '', intro: '', canonicalBookId: '', list: [], loading: false }
}

function openChapter(ch) {
  if (!ch.chapterUrl) { show('该章节无 URL'); return }
  router.push({
    path: '/reader',
    query: {
      sourceId:     chapterBook.value.sourceId,
      sourceName:   chapterBook.value.sourceName,
      bookUrl:      chapterBook.value.bookUrl,
      bookName:     chapterBook.value.title,
      author:       chapterBook.value.author,
      coverUrl:     chapterBook.value.coverUrl,
      intro:        chapterBook.value.intro,
      canonicalBookId: chapterBook.value.canonicalBookId,
      chapterUrl:   ch.chapterUrl,
      chapterIndex: ch.index ?? 0
    }
  })
}

onMounted(() => {
  // 从书架"目录"按钮跳转过来
  if (route.query.openBook) {
    try {
      const book = JSON.parse(route.query.openBook)
      goToDetail({
        sourceId: book.sourceId,
        sourceName: book.sourceName,
        name: book.name,
        author: book.author,
        coverUrl: book.coverUrl,
        intro: book.intro,
        kind: book.kind,
        lastChapter: book.lastChapter,
        bookUrl: book.bookUrl
      })
    } catch { /* 忽略 */ }
  }
})
</script>

<style scoped>
/* Hero */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-12) 0 var(--space-10);
  position: relative;
  overflow: hidden;
}

.hero-content { max-width: 480px; z-index: 1; }

.hero-title {
  display: flex;
  flex-direction: column;
  margin-bottom: var(--space-4);
}

.hero-line1 {
  font-family: var(--font-serif);
  font-size: clamp(2.5rem, 5vw, 4rem);
  font-weight: 700;
  color: var(--gold-0);
  letter-spacing: 0.05em;
  line-height: 1;
}

.hero-line2 {
  font-family: var(--font-serif);
  font-size: clamp(1.25rem, 2.5vw, 1.75rem);
  font-weight: 400;
  color: var(--ink-2);
  letter-spacing: 0.08em;
  margin-top: var(--space-2);
}

.hero-desc {
  font-size: 1rem;
  color: var(--ink-3);
  margin-bottom: var(--space-6);
  line-height: 1.8;
}

.hero-actions { display: flex; gap: var(--space-4); }

.hero-deco {
  position: relative;
  width: 280px;
  height: 280px;
  flex-shrink: 0;
}

.deco-circle {
  position: absolute;
  border-radius: var(--radius-full);
  opacity: 0.35;
}

.c1 {
  width: 220px;
  height: 220px;
  border: 2px solid var(--gold-2);
  top: 20px;
  left: 20px;
}

.c2 {
  width: 140px;
  height: 140px;
  background: var(--gold-3);
  bottom: 30px;
  right: 20px;
}

.deco-chars {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  gap: var(--space-2);
}

.deco-chars span {
  font-family: var(--font-serif);
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--ink-0);
  opacity: 0.08;
}

/* Section */
.section { padding-bottom: var(--space-12); }

.section-header {
  margin-bottom: var(--space-6);
}

.section-title {
  font-family: var(--font-serif);
  font-size: 1.375rem;
  color: var(--ink-0);
  position: relative;
  padding-left: var(--space-4);
  margin-bottom: var(--space-4);
}

.section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 1.2em;
  background: var(--gold-0);
  border-radius: var(--radius-full);
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-4);
}

/* Search */
.search-wrap {
  display: flex;
  align-items: center;
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-full);
  overflow: hidden;
  background: var(--paper-0);
  transition: border-color var(--transition-fast);
  flex: 1;
  max-width: 480px;
}

.search-wrap:focus-within {
  border-color: var(--gold-1);
}

.search-input {
  border: none;
  outline: none;
  padding: var(--space-2) var(--space-4);
  font-size: 0.875rem;
  color: var(--ink-1);
  background: transparent;
  flex: 1;
}

.search-input::placeholder { color: var(--ink-4); }

.search-btn {
  padding: var(--space-2) var(--space-5);
  background: var(--ink-0);
  color: var(--paper-0);
  border: none;
  font-size: 0.8125rem;
  cursor: pointer;
  transition: background var(--transition-fast);
  white-space: nowrap;
}

.search-btn:hover:not(:disabled) { background: var(--ink-1); }
.search-btn:disabled { opacity: 0.6; cursor: not-allowed; }

/* 搜索进度 */
.search-progress-wrap {
  padding: var(--space-8) 0;
  text-align: center;
}
.search-progress-bar {
  height: 6px;
  background: var(--paper-3);
  border-radius: var(--radius-full);
  overflow: hidden;
  margin-bottom: var(--space-3);
}
.search-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--gold-1), var(--gold-0));
  border-radius: var(--radius-full);
  transition: width 0.2s ease;
}
.search-progress-text {
  font-size: 0.875rem;
  color: var(--ink-4);
}

/* 书籍网格 */
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--space-5);
}
.book-card {
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: box-shadow var(--transition-fast);
}
.book-card:hover { box-shadow: var(--shadow-sm); }
.book-cover-wrap {
  aspect-ratio: 3/4;
  overflow: hidden;
  background: var(--paper-2);
  cursor: pointer;
}
.book-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s;
}
.book-cover-wrap:hover .book-cover { transform: scale(1.03); }
.book-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.book-cover-placeholder span {
  font-family: var(--font-serif);
  font-size: 3rem;
  color: var(--gold-1);
  opacity: 0.5;
}
.book-meta {
  padding: var(--space-3) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.book-name {
  font-weight: 600;
  font-size: 0.9375rem;
  color: var(--ink-0);
  line-height: 1.3;
}
.book-name-row { display:flex; align-items:center; gap:7px; min-width:0; }
.knowledge-badge { flex:0 0 auto; padding:2px 6px; border:1px solid #8bb894; border-radius:999px; background:#edf8ef; color:#39734a; font-size:.62rem; font-weight:700; letter-spacing:.04em; }
.book-author {
  font-size: 0.8125rem;
  color: var(--ink-3);
}
.source-count { display:inline-flex; width:max-content; margin-top:5px; border-radius:999px; padding:3px 7px; color:#456c57; background:#e8f0dd; font-size:.68rem; font-weight:800; }
.book-intro {
  font-size: 0.8rem;
  color: var(--ink-4);
  line-height: 1.5;
  display: -webkit-box;
  line-clamp: 3;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.book-actions {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}
.btn-shelf-on {
  background: var(--paper-2);
  color: var(--ink-3);
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-full);
  padding: var(--space-1) var(--space-3);
  font-size: 0.8rem;
  cursor: default;
}
.mt-4 { margin-top: var(--space-4); }

/* 结果统计 */
.result-stat {
  font-size: 0.875rem;
  color: var(--ink-3);
  margin-bottom: var(--space-4);
}
.result-stat strong {
  color: var(--ink-0);
  font-weight: 600;
}

/* 分页 */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-8);
  flex-wrap: wrap;
}
.page-btn {
  min-width: 36px;
  height: 36px;
  padding: 0 var(--space-2);
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-md);
  background: var(--paper-0);
  color: var(--ink-1);
  font-size: 0.875rem;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.page-btn:hover:not(:disabled):not(.page-btn-ellipsis) {
  border-color: var(--gold-1);
  color: var(--gold-0);
}
.page-btn-active {
  background: var(--gold-0) !important;
  border-color: var(--gold-0) !important;
  color: #fff !important;
}
.page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.page-btn-ellipsis { border-color: transparent; background: transparent; cursor: default; }

/* 章节面板 */
.chapter-panel {
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-lg);
  overflow: hidden;
  margin-top: var(--space-4);
}
.chapter-header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--paper-3);
  background: var(--paper-1);
}
.back-btn {
  border: none;
  background: transparent;
  color: var(--gold-0);
  font-size: 0.875rem;
  cursor: pointer;
  padding: 0;
}
.back-btn:hover { text-decoration: underline; }
.chapter-book-title {
  font-family: var(--font-serif);
  font-size: 1rem;
  font-weight: 600;
  color: var(--ink-0);
}
.chapter-list { list-style: none; padding: 0; margin: 0; max-height: 60vh; overflow-y: auto; }
.chapter-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  border-bottom: 1px solid var(--paper-2);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.chapter-item:hover { background: var(--paper-1); }
.chapter-num  { font-size: 0.75rem; color: var(--ink-4); min-width: 32px; }
.chapter-name { font-size: 0.9rem; color: var(--ink-1); }
.spinner-wrap { display: flex; justify-content: center; padding: var(--space-8) 0; }
.spinner { width: 32px; height: 32px; border: 3px solid var(--paper-3); border-top-color: var(--gold-0); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* 平台精选占位 */
.featured-placeholder {
  text-align: center;
  padding: var(--space-16) 0;
  color: var(--ink-4);
}
.featured-icon { font-size: 3rem; margin-bottom: var(--space-4); }
.featured-tip { font-size: 0.9rem; color: var(--ink-3); }

/* 空状态 */
.empty-state {
  text-align: center;
  padding: var(--space-16) 0;
  color: var(--ink-4);
}
.empty-icon {
  font-size: 3rem;
  margin-bottom: var(--space-4);
}

@media (max-width: 768px) {
  .hero { flex-direction: column; text-align: center; gap: var(--space-8); }
  .hero-deco { width: 160px; height: 160px; }
  .deco-chars span { font-size: 1.5rem; }
  .c1 { width: 130px; height: 130px; }
  .c2 { width: 80px; height: 80px; }
  .hero-actions { justify-content: center; }
  .toolbar { flex-direction: column; align-items: stretch; }
  .book-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
