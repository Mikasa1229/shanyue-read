<template>
  <main class="page">
    <div class="container">
      <section class="detail-card">
        <div class="cover-wrap">
          <img v-if="book.coverUrl" :src="book.coverUrl" :alt="book.name" @error="book.coverUrl = ''" />
          <div v-else class="cover-fallback">{{ book.name?.charAt(0) ?? '书' }}</div>
        </div>

        <div class="detail-main">
          <div class="tag-row">
            <span v-if="book.kind" class="tag">{{ book.kind }}</span>
            <span class="tag tag-gold">{{ book.sourceName || '书源作品' }}</span>
          </div>
          <h1 class="book-title">{{ book.name || '未知书名' }}</h1>
          <p class="book-author">{{ book.author || '未知作者' }}</p>
          <p class="book-intro">{{ book.intro || '暂无简介' }}</p>
          <p v-if="book.lastChapter" class="book-last">最新章节：{{ book.lastChapter }}</p>

          <div class="action-row">
            <button class="btn btn-primary" :disabled="addingShelf || onShelf" @click="addToShelf">
              {{ onShelf ? '已在书架' : (addingShelf ? '加入中…' : '加入书架') }}
            </button>
            <button class="btn" :class="favorited ? 'btn-shelf-on' : 'btn-gold'" :disabled="addingFav || favorited" @click="addFavorite">
              {{ favorited ? '已收藏' : (addingFav ? '收藏中…' : '加入收藏') }}
            </button>
            <button class="btn btn-gold" :disabled="loadingChapters" @click="openCatalog">
              {{ loadingChapters ? '加载目录中…' : '查看目录' }}
            </button>
            <button class="btn btn-ghost" @click="goHome">返回发现页</button>
          </div>

          <div v-if="chapters.length" class="chapter-panel">
            <div class="chapter-head">
              <span>目录（共 {{ chapters.length }} 章）</span>
            </div>
            <div class="chapter-list">
              <button
                v-for="(ch, idx) in chapters"
                :key="ch.chapterUrl || idx"
                class="chapter-item"
                @click="openChapter(ch, idx)"
              >
                <span class="chapter-index">{{ idx + 1 }}</span>
                <span class="chapter-name">{{ ch.chapterName || `第 ${idx + 1} 章` }}</span>
              </button>
            </div>
            <div class="chapter-more">
              <button class="btn btn-ghost btn-sm" :disabled="loadingMoreChapters || chapterPage <= 1" @click="changeChapterPage(chapterPage - 1)">上一页</button>
              <span class="chapter-page-text">第 {{ chapterPage }} 页</span>
              <button class="btn btn-ghost btn-sm" :disabled="loadingMoreChapters || !hasMoreChapters" @click="changeChapterPage(chapterPage + 1)">下一页</button>
              <input v-model.number="jumpPage" class="chapter-jump-input" type="number" min="1" placeholder="页码" />
              <button class="btn btn-ghost btn-sm" :disabled="loadingMoreChapters" @click="jumpToPage">跳转</button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiGetBookDetail, apiGetChaptersPage } from '@/api/bookSource'
import { apiAddFavorite, apiCheckFavorited } from '@/api/favorite'
import { apiAddToShelf, apiCheckOnShelf } from '@/api/bookshelf'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const { show } = useToast()
const userStore = useUserStore()

const book = reactive({
  sourceId: route.query.sourceId,
  sourceName: route.query.sourceName || '',
  name: route.query.name || route.query.bookName || route.query.bookTitle || '',
  author: route.query.author || '',
  coverUrl: route.query.coverUrl || route.query.bookCoverUrl || '',
  intro: route.query.intro || '',
  kind: route.query.kind || '',
  lastChapter: route.query.lastChapter || '',
  bookUrl: route.query.bookUrl || '',
  canonicalBookId: route.query.canonicalBookId || ''
})

const loadingChapters = ref(false)
const loadingDetail = ref(false)
const chapters = ref([])
const chapterSize = 50
const chapterPage = ref(1)
const jumpPage = ref(null)
const hasMoreChapters = ref(false)
const loadingMoreChapters = ref(false)
const favorited = ref(false)
const onShelf = ref(false)
const addingFav = ref(false)
const addingShelf = ref(false)

async function hydrateDetail() {
  if (!book.sourceId || !book.bookUrl) return
  loadingDetail.value = true
  try {
    const res = await apiGetBookDetail(book.sourceId, book.bookUrl)
    if (!res) return
    book.sourceName = book.sourceName || res.sourceName || ''
    book.name = res.name || book.name || ''
    book.author = res.author || book.author || ''
    book.coverUrl = res.coverUrl || book.coverUrl || ''
    book.intro = res.intro || book.intro || ''
    book.kind = res.kind || book.kind || ''
    book.lastChapter = res.lastChapter || book.lastChapter || ''
    book.canonicalBookId = res.canonicalBookId || book.canonicalBookId || ''
  } catch (_) {
    // 兜底失败不阻断页面
  } finally {
    loadingDetail.value = false
  }
}

async function openCatalog() {
  if (!book.sourceId || !book.bookUrl) {
    show('缺少书源信息，无法获取目录')
    return
  }
  loadingChapters.value = true
  try {
    await loadChapterPage(1)
  } catch (e) {
    show(e.message)
  } finally {
    loadingChapters.value = false
  }
}

async function loadChapterPage(pageNo) {
  const safePage = Math.max(1, pageNo)
  const offset = (safePage - 1) * chapterSize
  const page = await apiGetChaptersPage(book.sourceId, book.bookUrl, offset, chapterSize)
  const records = page?.records ?? []
  if (!records.length) {
    show('该书暂无可读章节')
    if (safePage > 1) return
  }
  chapters.value = records
  chapterPage.value = safePage
  hasMoreChapters.value = !!page?.hasMore
}

async function changeChapterPage(pageNo) {
  if (loadingMoreChapters.value) return
  loadingMoreChapters.value = true
  try {
    await loadChapterPage(pageNo)
  } catch (e) {
    show(e.message)
  } finally {
    loadingMoreChapters.value = false
  }
}

function jumpToPage() {
  const p = Number(jumpPage.value)
  if (!p || p < 1) {
    show('请输入有效页码')
    return
  }
  changeChapterPage(p)
}

function openChapter(chapter, idx) {
  router.push({
    path: '/reader',
    query: {
      sourceId: book.sourceId,
      sourceName: book.sourceName,
      bookUrl: book.bookUrl,
      bookName: book.name,
      author: book.author,
      coverUrl: book.coverUrl,
      intro: book.intro,
      canonicalBookId: book.canonicalBookId,
      chapterUrl: chapter.chapterUrl,
      chapterIndex: idx ?? 0
    }
  })
}

async function addToShelf() {
  if (!userStore.isLoggedIn) {
    show('请先登录')
    return
  }
  if (onShelf.value || addingShelf.value) return
  addingShelf.value = true
  try {
    await apiAddToShelf({
      sourceId: book.sourceId,
      sourceName: book.sourceName,
      bookName: book.name,
      author: book.author,
      coverUrl: book.coverUrl,
      bookUrl: book.bookUrl,
      canonicalBookId: book.canonicalBookId || undefined
    })
    onShelf.value = true
    show('已加入书架')
  } catch (e) {
    show(e.message)
  } finally {
    addingShelf.value = false
  }
}

async function addFavorite() {
  if (!userStore.isLoggedIn) {
    show('请先登录')
    return
  }
  if (favorited.value || addingFav.value) return
  addingFav.value = true
  try {
    await apiAddFavorite({
      sourceId: book.sourceId,
      sourceName: book.sourceName,
      bookName: book.name,
      author: book.author,
      coverUrl: book.coverUrl,
      bookUrl: book.bookUrl
    })
    favorited.value = true
    show('已加入收藏')
  } catch (e) {
    show(e.message)
  } finally {
    addingFav.value = false
  }
}

async function loadActionStatus() {
  if (!userStore.isLoggedIn || !book.bookUrl) return
  try {
    const [fav, shelf] = await Promise.all([
      apiCheckFavorited(book.bookUrl),
      apiCheckOnShelf(book.bookUrl)
    ])
    favorited.value = !!fav?.favorited
    onShelf.value = !!shelf?.onShelf
  } catch {
    // 忽略状态查询失败
  }
}

function goHome() {
  router.push('/')
}

onMounted(() => {
  hydrateDetail()
  loadActionStatus()
})
</script>

<style scoped>
.detail-card {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: var(--space-8);
  padding: var(--space-10);
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
}
.cover-wrap {
  aspect-ratio: 3 / 4;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--paper-2);
}
.cover-wrap img { width: 100%; height: 100%; object-fit: cover; }
.cover-fallback {
  width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  font-family: var(--font-serif); font-size: 4rem; color: var(--gold-1); opacity: 0.55;
}
.tag-row { display: flex; gap: var(--space-2); margin-bottom: var(--space-3); }
.book-title {
  font-family: var(--font-serif);
  font-size: 2rem;
  color: var(--ink-0);
  margin-bottom: var(--space-2);
}
.book-author { color: var(--ink-3); margin-bottom: var(--space-4); }
.book-intro {
  color: var(--ink-2);
  line-height: 1.85;
  white-space: pre-line;
  margin-bottom: var(--space-4);
}
.book-last { color: var(--ink-3); font-size: 0.875rem; margin-bottom: var(--space-6); }
.action-row { display: flex; gap: var(--space-3); }
.btn-shelf-on {
  background: var(--paper-2);
  color: var(--ink-3);
  border: 1px solid var(--paper-3);
}

.chapter-panel {
  margin-top: var(--space-6);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-lg);
  background: var(--paper-1);
}

.chapter-head {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--paper-3);
  color: var(--ink-2);
  font-size: 0.875rem;
}

.chapter-list {
  max-height: 380px;
  overflow: auto;
}

.chapter-more {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  border-top: 1px solid var(--paper-3);
}

.chapter-page-text {
  font-size: 0.8125rem;
  color: var(--ink-3);
}

.chapter-jump-input {
  width: 70px;
  height: 30px;
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-sm);
  padding: 0 8px;
  background: var(--paper-0);
  color: var(--ink-2);
}

.chapter-item {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  text-align: left;
  padding: var(--space-3) var(--space-4);
  color: var(--ink-2);
  cursor: pointer;
}

.chapter-item:hover {
  background: var(--paper-0);
}

.chapter-index {
  min-width: 28px;
  font-size: 0.75rem;
  color: var(--ink-4);
}

.chapter-name {
  font-size: 0.875rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .detail-card {
    grid-template-columns: 1fr;
    padding: var(--space-6);
    gap: var(--space-5);
  }
  .cover-wrap { max-width: 180px; }
}
</style>
