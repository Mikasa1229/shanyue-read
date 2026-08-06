<template>
  <main class="page">
    <div class="container">
      <div class="page-header">
        <h1 class="page-title">我的书架</h1>
        <p class="page-desc">{{ total }} 本书</p>
      </div>

      <div v-if="loading" class="spinner-wrap"><div class="spinner"></div></div>

      <div v-else-if="books.length === 0" class="empty-state">
        <div class="empty-icon">📚</div>
        <p>书架还是空的</p>
        <router-link to="/book-sources" class="btn btn-gold btn-sm mt-4">去书源搜索好书</router-link>
      </div>

      <div v-else class="shelf-grid">
        <div v-for="book in books" :key="book.id" class="shelf-card">
          <div class="cover-wrap" @click="goRead(book)">
            <img v-if="book.coverUrl" :src="book.coverUrl" class="cover-img" :alt="book.bookName"
                 @error="e => e.target.style.display='none'" />
            <div v-else class="cover-placeholder">
              <span>{{ book.bookName?.charAt(0) ?? '书' }}</span>
            </div>
            <div v-if="book.lastChapterName" class="reading-badge">续读</div>
          </div>
          <div class="card-meta">
            <div class="shelf-name-row"><div class="book-name" :title="book.bookName">{{ book.bookName }}</div><span v-if="book.knowledgeStatus === 'READY'" class="knowledge-badge">AI 知识库</span></div>
            <div class="book-author">{{ book.author || '未知作者' }}</div>
            <div v-if="book.lastChapterName" class="last-chapter" :title="book.lastChapterName">
              {{ book.lastChapterName }}
            </div>
            <!-- 阅读进度条 -->
            <div v-if="book.totalChapters > 0" class="progress-wrap">
              <div class="progress-bar">
                <div class="progress-fill"
                     :style="{ width: Math.round((book.lastChapterIndex + 1) / book.totalChapters * 100) + '%' }">
                </div>
              </div>
              <span class="progress-text">
                第 {{ (book.lastChapterIndex ?? 0) + 1 }} / {{ book.totalChapters }} 章
              </span>
            </div>
            <div class="card-actions">
              <button class="btn btn-sm btn-gold" @click="goRead(book)">
                {{ book.lastChapterUrl ? '继续阅读' : '开始阅读' }}
              </button>
              <button class="btn btn-sm btn-ghost" @click="goChapters(book)">目录</button>
              <button class="icon-btn danger" title="移出书架" @click="removeBook(book)">🗑</button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="totalPages > 1" class="pagination mt-6">
        <button v-for="p in totalPages" :key="p" class="page-btn" :class="{ active: p === currentPage }"
                @click="loadPage(p)">{{ p }}</button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { apiGetMyShelf, apiRemoveFromShelf } from '@/api/bookshelf'
import { apiGetBookKnowledgeStatuses } from '@/api/agent'

const router = useRouter()
const { show } = useToast()

const books = ref([])
const loading = ref(false)
const currentPage = ref(1)
const total = ref(0)
const totalPages = ref(1)

async function loadPage(page = 1) {
  loading.value = true
  currentPage.value = page
  try {
    const res = await apiGetMyShelf(page, 20)
    books.value  = res?.records ?? []
    const ids = [...new Set(books.value.map(book => book.canonicalBookId).filter(Boolean))]
    if (ids.length) {
      try {
        const statuses = await apiGetBookKnowledgeStatuses(ids)
        books.value = books.value.map(book => ({ ...book, knowledgeStatus: statuses?.[book.canonicalBookId]?.status }))
      } catch (_) { /* Agent badges are optional and never block the bookshelf. */ }
    }
    total.value  = res?.total   ?? 0
    totalPages.value = res?.pages ?? 1
  } catch (e) {
    show(e.message)
  } finally {
    loading.value = false
  }
}

function goRead(book) {
  router.push({
    path: '/reader',
    query: {
      sourceId:    book.sourceId,
      bookUrl:     book.bookUrl,
      bookName:    book.bookName,
      chapterUrl:  book.lastChapterUrl || undefined,
      canonicalBookId: book.canonicalBookId || undefined,
      chapterIndex: Number.isInteger(book.lastChapterIndex) ? book.lastChapterIndex : 0
    }
  })
}

function goChapters(book) {
  router.push({
    path: '/',
    query: { openBook: JSON.stringify({ sourceId: book.sourceId, bookUrl: book.bookUrl, name: book.bookName }) }
  })
}

async function removeBook(book) {
  if (!confirm(`确认将「${book.bookName}」移出书架？`)) return
  try {
    await apiRemoveFromShelf(book.bookUrl)
    show('已移出书架')
    await loadPage(currentPage.value)
  } catch (e) {
    show(e.message)
  }
}

onMounted(() => loadPage(1))
</script>

<style scoped>
.page-header { padding: var(--space-8) 0 var(--space-6); }
.page-title  { font-family: var(--font-serif); font-size: 1.75rem; color: var(--ink-0); margin-bottom: var(--space-2); }
.page-desc   { font-size: 0.875rem; color: var(--ink-3); }

.shelf-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--space-5);
}

.shelf-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.shelf-name-row { display:flex; align-items:center; gap:6px; min-width:0; }
.shelf-name-row .book-name { min-width:0; }
.knowledge-badge { flex:0 0 auto; padding:2px 6px; border:1px solid #8bb894; border-radius:999px; background:#edf8ef; color:#39734a; font-size:.62rem; font-weight:700; white-space:nowrap; }

.cover-wrap {
  position: relative;
  aspect-ratio: 3/4;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--paper-2);
  cursor: pointer;
  transition: transform 0.2s;
}
.cover-wrap:hover { transform: translateY(-3px); box-shadow: var(--shadow-sm); }
.cover-img { width: 100%; height: 100%; object-fit: cover; }
.cover-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-serif); font-size: 2.5rem;
  color: var(--gold-1); opacity: 0.5;
}
.reading-badge {
  position: absolute; bottom: 6px; right: 6px;
  background: var(--gold-0); color: #fff;
  font-size: 0.6875rem; padding: 2px 6px;
  border-radius: var(--radius-full);
}

.card-meta { display: flex; flex-direction: column; gap: 3px; }
.book-name  { font-weight: 600; font-size: 0.875rem; color: var(--ink-0); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.book-author { font-size: 0.75rem; color: var(--ink-3); }
.last-chapter { font-size: 0.7rem; color: var(--ink-4); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.progress-wrap { display: flex; align-items: center; gap: 5px; margin-top: 3px; }
.progress-bar  { flex: 1; height: 4px; background: var(--paper-3); border-radius: 99px; overflow: hidden; }
.progress-fill { height: 100%; background: var(--gold-1); border-radius: 99px; transition: width 0.3s; min-width: 4px; }
.progress-text { font-size: 0.65rem; color: var(--ink-4); white-space: nowrap; flex-shrink: 0; }

.card-actions { display: flex; align-items: center; gap: var(--space-2); margin-top: var(--space-2); flex-wrap: wrap; }

.spinner-wrap { display: flex; justify-content: center; padding: var(--space-16) 0; }
.spinner {
  width: 32px; height: 32px;
  border: 3px solid var(--paper-3); border-top-color: var(--gold-0);
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.empty-state { text-align: center; padding: var(--space-16) 0; color: var(--ink-4); }
.empty-icon  { font-size: 3rem; margin-bottom: var(--space-4); }

.pagination { display: flex; justify-content: center; gap: var(--space-2); }
.mt-4 { margin-top: var(--space-4); }
.mt-6 { margin-top: var(--space-6); }
.page-btn {
  width: 36px; height: 36px; border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-md); background: transparent;
  font-size: 0.875rem; color: var(--ink-3); cursor: pointer;
  transition: all var(--transition-fast);
}
.page-btn:hover { background: var(--paper-2); }
.page-btn.active { background: var(--ink-0); border-color: var(--ink-0); color: var(--paper-0); }

.icon-btn { border: none; background: transparent; font-size: 1rem; cursor: pointer; padding: 4px; border-radius: var(--radius-sm); }
.icon-btn.danger:hover { background: #fee2e2; }

@media (max-width: 480px) {
  .shelf-grid { grid-template-columns: repeat(3, 1fr); gap: var(--space-3); }
}
</style>
