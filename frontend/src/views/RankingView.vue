<template>
  <main class="page">
    <div class="container">

      <div class="page-header">
        <h1 class="page-title">排行榜</h1>
        <div class="tab-bar">
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'reading' }"
            @click="switchTab('reading')"
          >阅读排行</button>
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'hot' }"
            @click="switchTab('hot')"
          >热门书籍</button>
        </div>
      </div>

      <!-- ═══════════════ 阅读时长排行 ═══════════════ -->
      <template v-if="activeTab === 'reading'">
        <p class="tab-desc">累计阅读时长最多的书友</p>

        <div v-if="readingLoading" class="spinner-wrap"><div class="spinner"></div></div>

        <div v-else-if="readingList.length === 0" class="empty-state">
          <div class="empty-icon">🏆</div>
          <p>暂无排行数据，去阅读吧！</p>
          <router-link to="/book-sources" class="btn btn-gold mt-4">去书源阅读</router-link>
        </div>

        <div v-else class="ranking-list">
          <!-- 前三名 podium -->
          <div class="podium" v-if="readingList.length >= 1">
            <div v-if="readingList[1]" class="podium-item rank-2">
              <div class="podium-avatar">
                <img v-if="readingList[1].avatar" :src="readingList[1].avatar" :alt="readingList[1].nickname" />
                <span v-else class="avatar-placeholder">{{ readingList[1].nickname?.charAt(0) ?? '读' }}</span>
              </div>
              <div class="podium-name">{{ readingList[1].nickname }}</div>
              <div class="podium-time">{{ readingList[1].readingTime }}</div>
              <div class="podium-base rank-2-base">2</div>
            </div>
            <div class="podium-item rank-1">
              <div class="crown">👑</div>
              <div class="podium-avatar large">
                <img v-if="readingList[0].avatar" :src="readingList[0].avatar" :alt="readingList[0].nickname" />
                <span v-else class="avatar-placeholder">{{ readingList[0].nickname?.charAt(0) ?? '读' }}</span>
              </div>
              <div class="podium-name">{{ readingList[0].nickname }}</div>
              <div class="podium-time">{{ readingList[0].readingTime }}</div>
              <div class="podium-base rank-1-base">1</div>
            </div>
            <div v-if="readingList[2]" class="podium-item rank-3">
              <div class="podium-avatar">
                <img v-if="readingList[2].avatar" :src="readingList[2].avatar" :alt="readingList[2].nickname" />
                <span v-else class="avatar-placeholder">{{ readingList[2].nickname?.charAt(0) ?? '读' }}</span>
              </div>
              <div class="podium-name">{{ readingList[2].nickname }}</div>
              <div class="podium-time">{{ readingList[2].readingTime }}</div>
              <div class="podium-base rank-3-base">3</div>
            </div>
          </div>

          <!-- 4名以后 -->
          <div v-if="readingList.length > 3" class="rank-table">
            <div v-for="item in readingList.slice(3)" :key="item.userId" class="rank-row">
              <span class="rank-num">{{ item.rank }}</span>
              <div class="rank-avatar">
                <img v-if="item.avatar" :src="item.avatar" :alt="item.nickname" />
                <span v-else class="avatar-placeholder sm">{{ item.nickname?.charAt(0) ?? '读' }}</span>
              </div>
              <span class="rank-name">{{ item.nickname }}</span>
              <span class="rank-time">{{ item.readingTime }}</span>
            </div>
          </div>
        </div>
      </template>

      <!-- ═══════════════ 热门书籍排行 ═══════════════ -->
      <template v-else>
        <p class="tab-desc">加入书架人数最多的书籍</p>

        <div v-if="hotLoading" class="spinner-wrap"><div class="spinner"></div></div>

        <div v-else-if="hotList.length === 0" class="empty-state">
          <div class="empty-icon">📚</div>
          <p>暂无数据，去书源搜索并加入书架吧！</p>
          <router-link to="/book-sources" class="btn btn-gold mt-4">去书源搜索</router-link>
        </div>

        <div v-else class="hot-list">
          <div
            v-for="book in hotList"
            :key="book.bookUrl"
            class="hot-row"
            @click="goRead(book)"
          >
            <span class="rank-num" :class="['num-' + Math.min(book.rank, 4)]">{{ book.rank }}</span>
            <div class="hot-cover">
              <img v-if="book.coverUrl" :src="book.coverUrl" :alt="book.bookName" />
              <span v-else class="cover-placeholder">{{ book.bookName?.charAt(0) ?? '书' }}</span>
            </div>
            <div class="hot-meta">
              <div class="hot-name">{{ book.bookName || '未知书名' }}</div>
              <div class="hot-author">{{ book.author || '未知作者' }}</div>
              <div class="hot-source" v-if="book.sourceName">来源：{{ book.sourceName }}</div>
            </div>
            <div class="hot-count">
              <span class="count-num">{{ book.shelfCount }}</span>
              <span class="count-label">人在读</span>
            </div>
          </div>
        </div>
      </template>

    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { apiGetRanking, apiGetHotBooks } from '@/api/reading'

const router = useRouter()

const activeTab = ref('reading')

const readingList = ref([])
const readingLoading = ref(false)
const readingLoaded = ref(false)

const hotList = ref([])
const hotLoading = ref(false)
const hotLoaded = ref(false)

async function loadReadingRanking() {
  if (readingLoaded.value) return
  readingLoading.value = true
  try {
    readingList.value = await apiGetRanking(50) ?? []
    readingLoaded.value = true
  } catch {
    readingList.value = []
  } finally {
    readingLoading.value = false
  }
}

async function loadHotBooks() {
  if (hotLoaded.value) return
  hotLoading.value = true
  try {
    hotList.value = await apiGetHotBooks(30) ?? []
    hotLoaded.value = true
  } catch {
    hotList.value = []
  } finally {
    hotLoading.value = false
  }
}

function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'reading') loadReadingRanking()
  else loadHotBooks()
}

function goRead(book) {
  router.push({ path: '/', query: { openBook: JSON.stringify({
    sourceId: book.sourceId,
    bookUrl: book.bookUrl,
    bookName: book.bookName,
    author: book.author,
    coverUrl: book.coverUrl,
  }) } })
}

onMounted(() => loadReadingRanking())
</script>

<style scoped>
.page-header {
  padding: var(--space-8) 0 var(--space-4);
  text-align: center;
}
.page-title {
  font-family: var(--font-serif);
  font-size: 1.75rem;
  color: var(--ink-0);
  margin-bottom: var(--space-4);
}

/* ── Tabs ────────────────────────────────────────── */
.tab-bar {
  display: inline-flex;
  background: var(--paper-2);
  border-radius: var(--radius-full);
  padding: 3px;
  gap: 2px;
}
.tab-btn {
  padding: var(--space-2) var(--space-6);
  border: none;
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--ink-2);
  font-size: 0.9375rem;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.tab-btn.active {
  background: var(--paper-0);
  color: var(--gold-0);
  font-weight: 600;
  box-shadow: var(--shadow-xs);
}
.tab-desc {
  text-align: center;
  font-size: 0.875rem;
  color: var(--ink-3);
  margin-bottom: var(--space-6);
}

/* ── Podium ───────────────────────────────────────── */
.podium {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}
.podium-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
}
.crown { font-size: 1.5rem; line-height: 1; }
.podium-avatar {
  width: 56px; height: 56px;
  border-radius: var(--radius-full);
  overflow: hidden;
  border: 3px solid var(--paper-3);
  display: flex; align-items: center; justify-content: center;
}
.podium-avatar.large { width: 72px; height: 72px; border-color: var(--gold-0); }
.podium-avatar img { width: 100%; height: 100%; object-fit: cover; }
.avatar-placeholder {
  font-family: var(--font-serif);
  font-size: 1.25rem;
  color: var(--gold-0);
  background: var(--gold-3);
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
}
.podium-name {
  font-size: 0.875rem; font-weight: 600; color: var(--ink-0);
  max-width: 80px; text-align: center;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.podium-time { font-size: 0.75rem; color: var(--ink-3); }
.podium-base {
  width: 56px; border-radius: var(--radius-md) var(--radius-md) 0 0;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 1.25rem; color: white;
}
.rank-1-base { height: 72px; background: linear-gradient(180deg, #f5c842, #d4a017); width: 72px; }
.rank-2-base { height: 52px; background: linear-gradient(180deg, #c0c0c0, #9e9e9e); }
.rank-3-base { height: 40px; background: linear-gradient(180deg, #cd7f32, #a0522d); }

/* ── Rank Table ──────────────────────────────────── */
.rank-table {
  display: flex; flex-direction: column; gap: var(--space-2);
  max-width: 600px; margin: 0 auto;
}
.rank-row {
  display: flex; align-items: center; gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-md);
  transition: box-shadow var(--transition-fast);
}
.rank-row:hover { box-shadow: var(--shadow-xs); }
.rank-num {
  width: 28px; text-align: center;
  font-size: 0.875rem; font-weight: 700; color: var(--ink-3);
}
.rank-avatar {
  width: 36px; height: 36px;
  border-radius: var(--radius-full); overflow: hidden;
  border: 2px solid var(--paper-3);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.rank-avatar img { width: 100%; height: 100%; object-fit: cover; }
.avatar-placeholder.sm { font-size: 0.875rem; }
.rank-name {
  flex: 1; font-size: 0.9375rem; color: var(--ink-0);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.rank-time { font-size: 0.875rem; color: var(--gold-0); font-weight: 600; white-space: nowrap; }

/* ── Hot Books List ──────────────────────────────── */
.hot-list {
  display: flex; flex-direction: column; gap: var(--space-2);
  max-width: 680px; margin: 0 auto;
}
.hot-row {
  display: flex; align-items: center; gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: box-shadow var(--transition-fast);
}
.hot-row:hover { box-shadow: var(--shadow-xs); }

.num-1 { color: #d4a017; font-size: 1rem; }
.num-2 { color: #9e9e9e; }
.num-3 { color: #a0522d; }

.hot-cover {
  width: 48px; height: 64px; border-radius: var(--radius-sm);
  overflow: hidden; flex-shrink: 0;
  background: var(--paper-3);
  display: flex; align-items: center; justify-content: center;
}
.hot-cover img { width: 100%; height: 100%; object-fit: cover; }
.cover-placeholder {
  font-family: var(--font-serif); font-size: 1.25rem; color: var(--gold-0);
}

.hot-meta {
  flex: 1; min-width: 0;
  display: flex; flex-direction: column; gap: 2px;
}
.hot-name {
  font-size: 0.9375rem; font-weight: 600; color: var(--ink-0);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.hot-author { font-size: 0.8125rem; color: var(--ink-3); }
.hot-source { font-size: 0.75rem; color: var(--ink-4); }

.hot-count {
  display: flex; flex-direction: column; align-items: center;
  flex-shrink: 0; min-width: 48px;
}
.count-num { font-size: 1.125rem; font-weight: 700; color: var(--gold-0); line-height: 1.2; }
.count-label { font-size: 0.75rem; color: var(--ink-3); }
</style>
