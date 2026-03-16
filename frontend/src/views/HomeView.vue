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
            <router-link to="/profile" class="btn btn-ghost" v-if="userStore.isLoggedIn">
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

      <!-- Category filter + Search -->
      <section class="section">
        <div class="section-header">
          <h2 class="section-title">发现好书</h2>
          <div class="toolbar">
            <div class="search-wrap">
              <input
                v-model="keyword"
                class="search-input"
                placeholder="搜索书名、作者…"
                @keydown.enter="doSearch"
              />
              <button class="search-btn" @click="doSearch">搜索</button>
            </div>
            <div class="category-tabs">
              <button
                v-for="cat in categories"
                :key="cat.value"
                class="cat-tab"
                :class="{ active: activeCategory === cat.value }"
                @click="selectCategory(cat.value)"
              >{{ cat.label }}</button>
            </div>
          </div>
        </div>

        <!-- Skeleton -->
        <div v-if="loading" class="grid-novels">
          <div v-for="i in 8" :key="i" class="skeleton-card">
            <div class="skeleton" style="aspect-ratio:3/4;border-radius:var(--radius-md)"></div>
            <div style="padding:12px;display:flex;flex-direction:column;gap:8px">
              <div class="skeleton" style="height:16px;width:80%"></div>
              <div class="skeleton" style="height:12px;width:50%"></div>
            </div>
          </div>
        </div>

        <div v-else-if="novels.length === 0" class="empty-state">
          <div class="empty-icon">📚</div>
          <p>暂无内容，敬请期待</p>
        </div>

        <div v-else class="grid-novels">
          <NovelCard v-for="n in novels" :key="n.id" :novel="n" />
        </div>

        <!-- Pagination -->
        <div v-if="totalPages > 1" class="pagination mt-8">
          <button
            v-for="p in totalPages"
            :key="p"
            class="page-btn"
            :class="{ active: p === currentPage }"
            @click="loadPage(p)"
          >{{ p }}</button>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { apiGetNovels } from '@/api/novel'
import { useToast } from '@/composables/useToast'
import NovelCard from '@/components/NovelCard.vue'

const userStore = useUserStore()
const { show } = useToast()

const categories = [
  { label: '全部', value: '' },
  { label: '玄幻', value: '玄幻' },
  { label: '言情', value: '言情' },
  { label: '武侠', value: '武侠' },
  { label: '都市', value: '都市' },
  { label: '历史', value: '历史' },
  { label: '科幻', value: '科幻' }
]

const activeCategory = ref('')
const keyword = ref('')
const searchKeyword = ref('')
const novels = ref([])
const loading = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)

async function loadPage(page = 1) {
  loading.value = true
  currentPage.value = page
  try {
    const res = await apiGetNovels({
      page,
      size: 16,
      category: activeCategory.value || undefined,
      keyword: searchKeyword.value || undefined
    })
    novels.value = res?.records ?? []
    totalPages.value = res?.pages ?? 1
  } catch (e) {
    show(e.message)
    novels.value = []
  } finally {
    loading.value = false
  }
}

function selectCategory(val) {
  activeCategory.value = val
  currentPage.value = 1
  loadPage(1)
}

function doSearch() {
  searchKeyword.value = keyword.value.trim()
  currentPage.value = 1
  loadPage(1)
}

onMounted(() => loadPage(1))
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
  justify-content: space-between;
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
  width: 200px;
}

.search-input::placeholder { color: var(--ink-4); }

.search-btn {
  padding: var(--space-2) var(--space-4);
  background: var(--ink-0);
  color: var(--paper-0);
  border: none;
  font-size: 0.8125rem;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.search-btn:hover { background: var(--ink-1); }

/* Category tabs */
.category-tabs {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.cat-tab {
  padding: var(--space-2) var(--space-4);
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-full);
  background: transparent;
  font-size: 0.875rem;
  color: var(--ink-3);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.cat-tab:hover {
  border-color: var(--ink-3);
  color: var(--ink-1);
}

.cat-tab.active {
  background: var(--ink-0);
  border-color: var(--ink-0);
  color: var(--paper-0);
}

/* Skeleton card */
.skeleton-card {
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

/* Empty state */
.empty-state {
  text-align: center;
  padding: var(--space-16) 0;
  color: var(--ink-4);
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: var(--space-4);
}

/* Pagination */
.pagination {
  display: flex;
  justify-content: center;
  gap: var(--space-2);
}

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
.page-btn.active {
  background: var(--ink-0);
  border-color: var(--ink-0);
  color: var(--paper-0);
}

@media (max-width: 768px) {
  .hero { flex-direction: column; text-align: center; gap: var(--space-8); }
  .hero-deco { width: 160px; height: 160px; }
  .deco-chars span { font-size: 1.5rem; }
  .c1 { width: 130px; height: 130px; }
  .c2 { width: 80px; height: 80px; }
  .hero-actions { justify-content: center; }
  .toolbar { flex-direction: column; align-items: flex-start; }
}
</style>
