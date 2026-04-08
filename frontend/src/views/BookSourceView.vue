<template>
  <main class="page">
    <div class="container">

      <!-- 正文阅读全屏遮罩 -->
      <div v-if="reader.open" class="reader-overlay" @click.self="closeReader">
        <div class="reader-panel">
          <div class="reader-header">
            <button class="reader-back" @click="closeReader">← 返回目录</button>
            <span class="reader-title">{{ reader.chapterName }}</span>
          </div>
          <div v-if="reader.loading" class="reader-loading">加载中…</div>
          <div v-else-if="reader.error" class="reader-error">{{ reader.error }}</div>
          <div v-else class="reader-body" v-html="reader.html"></div>
        </div>
      </div>

      <!-- 页面标题 -->
      <div class="page-header">
        <h1 class="page-title">书源中心</h1>
        <p class="page-desc">基于 legado 书源格式，从第三方网站搜索与阅读小说</p>
      </div>

      <!-- 主标签页 -->
      <div class="tab-bar">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-btn"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >{{ tab.label }}</button>
      </div>

      <!-- ═══ 书源列表 ═══ -->
      <section v-if="activeTab === 'sources'">
        <div class="toolbar-row">
          <span class="count-badge">共 {{ sourceTotal }} 个书源</span>
          <button class="btn btn-gold btn-sm" @click="importModal.open = true">+ 导入书源</button>
        </div>

        <div v-if="sourcesLoading" class="spinner-wrap"><div class="spinner"></div></div>
        <div v-else-if="sources.length === 0" class="empty-state">
          <div class="empty-icon">📖</div>
          <p>暂无书源，点击「导入书源」开始</p>
        </div>
        <div v-else class="source-list">
          <div v-for="s in sources" :key="s.id" class="source-card" :class="{ disabled: !s.enabled }">
            <div class="source-info">
              <span class="source-name">{{ s.sourceName }}</span>
              <span class="source-url">{{ s.sourceUrl }}</span>
              <span v-if="testResults[s.id]" class="test-result" :class="testResults[s.id].accessible ? 'ok' : 'fail'">
                {{ testResults[s.id].accessible ? '✓' : '✗' }}
                {{ testResults[s.id].accessible ? `${testResults[s.id].responseMs}ms` : (testResults[s.id].error || `HTTP ${testResults[s.id].statusCode}`) }}
              </span>
            </div>
            <div class="source-actions">
              <span class="status-dot" :class="s.enabled ? 'on' : 'off'">
                {{ s.enabled ? '启用' : '禁用' }}
              </span>
              <button class="icon-btn" :title="testingId === s.id ? '测试中…' : '测试可访问性'" @click="doTestSource(s)" :disabled="testingId === s.id">
                {{ testingId === s.id ? '⏳' : '🔍' }}
              </button>
              <button class="icon-btn" :title="s.enabled ? '禁用' : '启用'" @click="toggleSource(s)">
                {{ s.enabled ? '🔕' : '🔔' }}
              </button>
              <button class="icon-btn danger" title="删除" @click="deleteSource(s)">🗑</button>
            </div>
          </div>
        </div>

        <div v-if="sourceTotalPages > 1" class="pagination mt-6">
          <button
            v-for="p in sourceTotalPages"
            :key="p"
            class="page-btn"
            :class="{ active: p === sourcePage }"
            @click="loadSources(p)"
          >{{ p }}</button>
        </div>
      </section>

      <!-- ═══ 搜索书籍 ═══ -->
      <section v-if="activeTab === 'search'">
        <!-- 搜索栏 -->
        <div class="search-bar">
          <div class="search-wrap">
            <input
              v-model="search.keyword"
              class="search-input"
              placeholder="输入书名或作者，聚合搜索所有书源…"
              @keydown.enter="doSearch"
            />
            <button class="search-btn" :disabled="search.loading" @click="doSearch">
              {{ search.loading ? '搜索中…' : '聚合搜索' }}
            </button>
          </div>
        </div>

        <!-- 章节面板 -->
        <div v-if="chapters.bookTitle" class="chapter-panel">
          <div class="chapter-header">
            <button class="back-btn" @click="clearChapters">← 返回搜索结果</button>
            <span class="chapter-book-title">《{{ chapters.bookTitle }}》目录</span>
          </div>
          <div v-if="chapters.loading" class="spinner-wrap"><div class="spinner"></div></div>
          <div v-else-if="chapters.list.length === 0" class="empty-state">
            <p>未获取到章节，可能该书源不支持此书目录</p>
          </div>
          <ul v-else class="chapter-list">
            <li
              v-for="(ch, idx) in chapters.list"
              :key="idx"
              class="chapter-item"
              @click="openChapter(ch)"
            >
              <span class="chapter-num">{{ idx + 1 }}</span>
              <span class="chapter-name">{{ ch.chapterName }}</span>
            </li>
          </ul>
        </div>

        <!-- 搜索结果 -->
        <template v-else>
          <!-- 聚合搜索进度条 -->
          <div v-if="search.loading" class="search-progress-wrap">
            <div class="search-progress-bar">
              <div class="search-progress-fill" :style="{ width: searchProgress + '%' }"></div>
            </div>
            <p class="search-progress-text">正在并发搜索所有书源… {{ searchProgress }}%</p>
          </div>
          <div v-else-if="search.results.length === 0 && search.searched" class="empty-state">
            <div class="empty-icon">🔍</div>
            <p>没有找到相关书籍</p>
          </div>
          <div v-else-if="!search.searched" class="search-hint">
            <p>输入关键词，将并发搜索所有已启用的书源</p>
          </div>
          <div v-else class="book-grid">
            <div v-for="(book, idx) in search.results" :key="idx" class="book-card">
              <div class="book-cover-wrap">
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
                <div class="book-name">{{ book.name || '未知书名' }}</div>
                <div class="book-author">{{ book.author || '未知作者' }}</div>
                <div v-if="book.intro" class="book-intro">{{ book.intro }}</div>
                <button class="btn btn-sm btn-ghost mt-4" @click="loadChapters(book)">查看目录</button>
              </div>
            </div>
          </div>
        </template>
      </section>

    </div>
  </main>

  <!-- ═══ 导入书源弹窗 ═══ -->
  <Teleport to="body">
    <div v-if="importModal.open" class="modal-overlay" @click.self="importModal.open = false">
      <div class="modal">
        <div class="modal-header">
          <h3>导入书源</h3>
          <button class="modal-close" @click="importModal.open = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="import-tabs">
            <button
              class="import-tab"
              :class="{ active: importModal.mode === 'url' }"
              @click="importModal.mode = 'url'"
            >远端 URL</button>
            <button
              class="import-tab"
              :class="{ active: importModal.mode === 'json' }"
              @click="importModal.mode = 'json'"
            >粘贴 JSON</button>
          </div>
          <div v-if="importModal.mode === 'url'" class="import-url">
            <label class="form-label">书源文件 URL</label>
            <input
              v-model="importModal.url"
              class="form-input"
              placeholder="https://example.com/sources.json"
            />
          </div>
          <div v-else class="import-json">
            <label class="form-label">JSON 内容（粘贴书源数组）</label>
            <textarea
              v-model="importModal.json"
              class="form-textarea"
              rows="8"
              placeholder='[{"bookSourceName":"...","bookSourceUrl":"..."}]'
            ></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost btn-sm" @click="importModal.open = false">取消</button>
          <button class="btn btn-gold btn-sm" :disabled="importModal.loading" @click="doImport">
            {{ importModal.loading ? '导入中…' : '确认导入' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'
import {
  apiListSources, apiToggleSource, apiDeleteSource,
  apiImportByUrl, apiImportByJson,
  apiAggregateSearch, apiGetChapters, apiGetContent,
  apiTestSource
} from '@/api/bookSource'
import { apiRecordReading } from '@/api/reading'

const { show } = useToast()
const userStore = useUserStore()

// ─── 标签页 ────────────────────────────────────────────────────
const tabs = [
  { key: 'sources', label: '书源管理' },
  { key: 'search',  label: '搜索书籍' }
]
const activeTab = ref('sources')

// ─── 书源列表 ──────────────────────────────────────────────────
const sources = ref([])
const sourcesLoading = ref(false)
const sourcePage = ref(1)
const sourceTotal = ref(0)
const sourceTotalPages = ref(1)

async function loadSources(page = 1) {
  sourcesLoading.value = true
  sourcePage.value = page
  try {
    const res = await apiListSources(page, 20)
    sources.value = res?.records ?? []
    sourceTotal.value = res?.total ?? 0
    sourceTotalPages.value = res?.pages ?? 1
  } catch (e) {
    show(e.message)
  } finally {
    sourcesLoading.value = false
  }
}

const enabledSources = computed(() => sources.value.filter(s => s.enabled))

async function toggleSource(s) {
  try {
    await apiToggleSource(s.id)
    s.enabled = !s.enabled
  } catch (e) {
    show(e.message)
  }
}

// ─── 书源测试 ──────────────────────────────────────────────────
const testResults = ref({})
const testingId = ref(null)

async function doTestSource(s) {
  testingId.value = s.id
  try {
    const res = await apiTestSource(s.id)
    testResults.value = { ...testResults.value, [s.id]: res }
  } catch (e) {
    testResults.value = { ...testResults.value, [s.id]: { accessible: false, statusCode: 0, responseMs: 0, error: e.message } }
  } finally {
    testingId.value = null
  }
}

async function deleteSource(s) {
  if (!confirm(`确认删除书源「${s.sourceName}」？`)) return
  try {
    await apiDeleteSource(s.id)
    await loadSources(sourcePage.value)
    show('已删除')
  } catch (e) {
    show(e.message)
  }
}

// ─── 导入弹窗 ──────────────────────────────────────────────────
const importModal = ref({ open: false, mode: 'url', url: '', json: '', loading: false })

async function doImport() {
  importModal.value.loading = true
  try {
    let res
    if (importModal.value.mode === 'url') {
      if (!importModal.value.url.trim()) { show('请输入 URL'); return }
      res = await apiImportByUrl(importModal.value.url.trim())
    } else {
      if (!importModal.value.json.trim()) { show('请粘贴 JSON'); return }
      res = await apiImportByJson(importModal.value.json.trim())
    }
    show(`成功导入 ${res?.imported ?? 0} 个书源`)
    importModal.value.open = false
    importModal.value.url = ''
    importModal.value.json = ''
    await loadSources(1)
  } catch (e) {
    show(e.message)
  } finally {
    importModal.value.loading = false
  }
}

// ─── 搜索 ──────────────────────────────────────────────────────
const search = ref({ keyword: '', results: [], loading: false, searched: false })
const searchProgress = ref(0)
let progressTimer = null

function startProgress() {
  searchProgress.value = 0
  clearInterval(progressTimer)
  // 6 秒内线性推进到 90%（每 200ms +3%），最后 10% 等结果回来
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
  if (!search.value.keyword.trim()) { show('请输入搜索关键词'); return }
  search.value.loading = true
  search.value.searched = false
  clearChapters()
  startProgress()
  try {
    const res = await apiAggregateSearch(search.value.keyword.trim())
    search.value.results = res ?? []
    search.value.searched = true
  } catch (e) {
    show(e.message)
    search.value.results = []
    search.value.searched = true
  } finally {
    finishProgress()
    search.value.loading = false
  }
}

// ─── 章节列表 ──────────────────────────────────────────────────
const chapters = ref({ bookTitle: '', bookUrl: '', sourceId: '', list: [], loading: false })

async function loadChapters(book) {
  if (!book.bookUrl) { show('该书源未返回书籍 URL'); return }
  if (!book.sourceId) { show('无法确定书源，无法获取目录'); return }
  chapters.value = { bookTitle: book.name, bookUrl: book.bookUrl, sourceId: book.sourceId, list: [], loading: true }
  try {
    const res = await apiGetChapters(book.sourceId, book.bookUrl)
    chapters.value.list = res ?? []
  } catch (e) {
    show(e.message)
    chapters.value.list = []
  } finally {
    chapters.value.loading = false
  }
}

function clearChapters() {
  chapters.value = { bookTitle: '', bookUrl: '', list: [], loading: false }
}

// ─── 正文阅读 ──────────────────────────────────────────────────
const reader = ref({ open: false, chapterName: '', html: '', loading: false, error: '' })
let readerOpenTime = 0

async function openChapter(ch) {
  readerOpenTime = Date.now()
  if (!ch.chapterUrl) { show('该章节无 URL'); return }
  reader.value = { open: true, chapterName: ch.chapterName, html: '', loading: true, error: '' }
  try {
    const res = await apiGetContent(chapters.value.sourceId, ch.chapterUrl)
    const raw = res?.content ?? ''
    if (!raw.trim()) {
      reader.value.error = '正文内容为空（该书源可能采用 JS 动态加载，服务端无法提取）'
    } else {
      reader.value.html = raw.replace(/\n/g, '<br/>')
    }
  } catch (e) {
    reader.value.error = e.message
  } finally {
    reader.value.loading = false
  }
}

function closeReader() {
  reader.value.open = false
  if (readerOpenTime > 0 && userStore.isLoggedIn) {
    const seconds = Math.floor((Date.now() - readerOpenTime) / 1000)
    readerOpenTime = 0
    if (seconds >= 5) {
      apiRecordReading(seconds).catch(() => {})
    }
  } else {
    readerOpenTime = 0
  }
}

onMounted(() => loadSources(1))
</script>

<style scoped>
.page-header {
  padding: var(--space-8) 0 var(--space-4);
}
.page-title {
  font-family: var(--font-serif);
  font-size: 1.75rem;
  color: var(--ink-0);
  margin-bottom: var(--space-2);
}
.page-desc {
  font-size: 0.9rem;
  color: var(--ink-3);
}

/* 标签页 */
.tab-bar {
  display: flex;
  gap: var(--space-2);
  border-bottom: 2px solid var(--paper-3);
  margin-bottom: var(--space-6);
}
.tab-btn {
  padding: var(--space-2) var(--space-5);
  border: none;
  background: transparent;
  font-size: 0.9375rem;
  color: var(--ink-3);
  cursor: pointer;
  position: relative;
  bottom: -2px;
  border-bottom: 2px solid transparent;
  transition: all var(--transition-fast);
}
.tab-btn.active {
  color: var(--ink-0);
  border-bottom-color: var(--gold-0);
  font-weight: 600;
}

/* 工具栏行 */
.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
}
.count-badge {
  font-size: 0.875rem;
  color: var(--ink-3);
}

/* 书源卡片 */
.source-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.source-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4) var(--space-5);
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-lg);
  transition: opacity var(--transition-fast);
}
.source-card.disabled {
  opacity: 0.5;
}
.source-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.source-name {
  font-weight: 600;
  font-size: 0.9375rem;
  color: var(--ink-0);
}
.source-url {
  font-size: 0.8125rem;
  color: var(--ink-4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 400px;
}
.source-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-shrink: 0;
}
.test-result {
  font-size: 0.75rem;
  padding: 2px 6px;
  border-radius: var(--radius-full);
  white-space: nowrap;
}
.test-result.ok   { background: #d1fae5; color: #065f46; }
.test-result.fail { background: #fee2e2; color: #991b1b; }
.status-dot {
  font-size: 0.75rem;
  padding: 2px 8px;
  border-radius: var(--radius-full);
}
.status-dot.on  { background: #d1fae5; color: #065f46; }
.status-dot.off { background: var(--paper-2); color: var(--ink-4); }
.icon-btn {
  border: none;
  background: transparent;
  font-size: 1rem;
  cursor: pointer;
  padding: 4px;
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}
.icon-btn:hover { background: var(--paper-2); }
.icon-btn.danger:hover { background: #fee2e2; }

/* 搜索栏 */
.search-bar {
  display: flex;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
  align-items: center;
}
.source-select {
  padding: var(--space-2) var(--space-4);
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-full);
  background: var(--paper-0);
  font-size: 0.875rem;
  color: var(--ink-1);
  cursor: pointer;
  outline: none;
  min-width: 160px;
}
.source-select:focus { border-color: var(--gold-1); }
.search-wrap {
  display: flex;
  align-items: center;
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-full);
  overflow: hidden;
  background: var(--paper-0);
  transition: border-color var(--transition-fast);
  flex: 1;
  min-width: 200px;
}
.search-wrap:focus-within { border-color: var(--gold-1); }
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

/* 搜索结果书籍网格 */
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
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
}
.book-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
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
.book-author {
  font-size: 0.8125rem;
  color: var(--ink-3);
}
.book-intro {
  font-size: 0.8rem;
  color: var(--ink-4);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.mt-4 { margin-top: var(--space-4); }

/* 章节面板 */
.chapter-panel {
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-lg);
  overflow: hidden;
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
.chapter-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 60vh;
  overflow-y: auto;
}
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
.chapter-num {
  font-size: 0.75rem;
  color: var(--ink-4);
  min-width: 32px;
}
.chapter-name {
  font-size: 0.9rem;
  color: var(--ink-1);
}

/* 正文阅读 */
.reader-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  z-index: 200;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  overflow-y: auto;
  padding: var(--space-8) var(--space-4);
}
.reader-panel {
  background: var(--paper-0);
  border-radius: var(--radius-xl);
  width: 100%;
  max-width: 720px;
  min-height: 60vh;
  display: flex;
  flex-direction: column;
}
.reader-header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-6);
  border-bottom: 1px solid var(--paper-3);
  background: var(--paper-1);
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}
.reader-back {
  border: none;
  background: transparent;
  color: var(--gold-0);
  cursor: pointer;
  font-size: 0.875rem;
  padding: 0;
  white-space: nowrap;
}
.reader-back:hover { text-decoration: underline; }
.reader-title {
  font-family: var(--font-serif);
  font-size: 1rem;
  font-weight: 600;
  color: var(--ink-0);
}
.reader-loading,
.reader-error {
  padding: var(--space-10) var(--space-6);
  text-align: center;
  color: var(--ink-3);
  font-size: 0.9rem;
}
.reader-error { color: #ef4444; }
.reader-body {
  padding: var(--space-6) var(--space-8);
  font-size: 1.0625rem;
  line-height: 2;
  color: var(--ink-1);
  font-family: var(--font-serif);
}

/* 搜索提示 */
.search-hint {
  text-align: center;
  padding: var(--space-16) 0;
  color: var(--ink-4);
  font-size: 0.9rem;
}

/* 通用 */
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

.spinner-wrap {
  display: flex;
  justify-content: center;
  padding: var(--space-12) 0;
}
.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--paper-3);
  border-top-color: var(--gold-0);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  text-align: center;
  padding: var(--space-16) 0;
  color: var(--ink-4);
}
.empty-icon { font-size: 3rem; margin-bottom: var(--space-4); }

.pagination {
  display: flex;
  justify-content: center;
  gap: var(--space-2);
}
.mt-6 { margin-top: var(--space-6); }
.page-btn {
  width: 36px;
  height: 36px;
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-md);
  background: transparent;
  font-size: 0.875rem;
  color: var(--ink-3);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.page-btn:hover { background: var(--paper-2); }
.page-btn.active { background: var(--ink-0); border-color: var(--ink-0); color: var(--paper-0); }

/* 导入弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.45);
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
}
.modal {
  background: var(--paper-0);
  border-radius: var(--radius-xl);
  width: 100%;
  max-width: 520px;
  box-shadow: var(--shadow-lg);
}
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-5) var(--space-6);
  border-bottom: 1px solid var(--paper-3);
}
.modal-header h3 {
  font-family: var(--font-serif);
  font-size: 1.125rem;
  color: var(--ink-0);
}
.modal-close {
  border: none;
  background: transparent;
  font-size: 1rem;
  cursor: pointer;
  color: var(--ink-3);
}
.modal-body { padding: var(--space-5) var(--space-6); }
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--paper-3);
}

.import-tabs {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}
.import-tab {
  padding: var(--space-2) var(--space-4);
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-full);
  background: transparent;
  font-size: 0.875rem;
  color: var(--ink-3);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.import-tab.active {
  background: var(--ink-0);
  border-color: var(--ink-0);
  color: var(--paper-0);
}

.form-label {
  display: block;
  font-size: 0.875rem;
  color: var(--ink-2);
  margin-bottom: var(--space-2);
}
.form-input {
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-md);
  font-size: 0.875rem;
  color: var(--ink-1);
  background: var(--paper-0);
  outline: none;
  box-sizing: border-box;
  transition: border-color var(--transition-fast);
}
.form-input:focus { border-color: var(--gold-1); }
.form-textarea {
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-md);
  font-size: 0.8125rem;
  color: var(--ink-1);
  background: var(--paper-0);
  outline: none;
  resize: vertical;
  box-sizing: border-box;
  font-family: 'Courier New', monospace;
  transition: border-color var(--transition-fast);
}
.form-textarea:focus { border-color: var(--gold-1); }

@media (max-width: 600px) {
  .search-bar { flex-direction: column; align-items: stretch; }
  .source-url { max-width: 180px; }
  .book-grid { grid-template-columns: repeat(2, 1fr); }
  .reader-body { padding: var(--space-4); }
}
</style>
