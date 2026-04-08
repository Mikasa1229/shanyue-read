<template>
  <main class="page">
    <div class="container">
      <div class="square-header">
        <h1 class="square-title">书友动态</h1>
        <p class="square-subtitle">看看书友们最近在聊什么</p>
      </div>

      <!-- 加载骨架 -->
      <div v-if="loading" class="feed-list">
        <div v-for="i in 5" :key="i" class="feed-card skeleton-card">
          <div class="skeleton avatar-skeleton"></div>
          <div class="skeleton-body">
            <div class="skeleton" style="width: 120px; height: 14px; margin-bottom: 8px;"></div>
            <div class="skeleton" style="width: 100%; height: 14px; margin-bottom: 6px;"></div>
            <div class="skeleton" style="width: 75%; height: 14px;"></div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else-if="comments.length === 0" class="empty-state">
        <div class="empty-icon">💬</div>
        <p>还没有书评，去写第一条吧</p>
        <router-link to="/" class="btn btn-gold mt-4">发现好书</router-link>
      </div>

      <!-- 动态列表 -->
      <div v-else class="feed-list">
        <div v-for="item in comments" :key="item.id" class="feed-card">
          <div class="feed-avatar">
            <img v-if="item.userAvatar" :src="item.userAvatar" :alt="item.userNickname" />
            <span v-else>{{ item.userNickname?.charAt(0) ?? '读' }}</span>
          </div>
          <div class="feed-body">
            <div class="feed-meta">
              <span class="feed-user">{{ item.userNickname }}</span>
              <span class="feed-action">评论了小说</span>
              <span class="feed-time">{{ formatTime(item.createdAt) }}</span>
            </div>
            <p class="feed-content">{{ item.content }}</p>
            <div class="feed-footer">
              <router-link :to="`/novel/${item.novelId}`" class="feed-novel-link">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="link-icon">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                  <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                </svg>
                查看原著
              </router-link>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="pagination">
        <button
          class="page-btn"
          :disabled="currentPage === 1"
          @click="loadPage(currentPage - 1)"
        >上一页</button>
        <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button
          class="page-btn"
          :disabled="currentPage === totalPages"
          @click="loadPage(currentPage + 1)"
        >下一页</button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { apiGetRecentComments } from '@/api/comment'
import { useToast } from '@/composables/useToast'

const { show } = useToast()

const comments = ref([])
const loading = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)

async function loadPage(page = 1) {
  loading.value = true
  try {
    const res = await apiGetRecentComments(page, 20)
    comments.value = res?.records ?? []
    currentPage.value = res?.current ?? page
    totalPages.value = res?.pages ?? 1
  } catch (e) {
    show(e.message)
  } finally {
    loading.value = false
  }
}

function formatTime(str) {
  if (!str) return ''
  const d = new Date(str)
  const now = new Date()
  const diff = (now - d) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)}天前`
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => loadPage(1))
</script>

<style scoped>
.square-header {
  text-align: center;
  padding: var(--space-10) 0 var(--space-8);
}

.square-title {
  font-family: var(--font-serif);
  font-size: 2rem;
  color: var(--ink-0);
  margin-bottom: var(--space-2);
}

.square-subtitle {
  font-size: 0.9375rem;
  color: var(--ink-4);
}

/* Feed list */
.feed-list {
  max-width: 680px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.feed-card {
  display: flex;
  gap: var(--space-4);
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-xl);
  padding: var(--space-5) var(--space-6);
  box-shadow: var(--shadow-sm);
  transition: box-shadow var(--transition-fast);
}

.feed-card:hover {
  box-shadow: var(--shadow-md);
}

/* Avatar */
.feed-avatar {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
  overflow: hidden;
  background: var(--gold-3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-serif);
  font-size: 1.1rem;
  color: var(--gold-0);
}

.feed-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Body */
.feed-body {
  flex: 1;
  min-width: 0;
}

.feed-meta {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  flex-wrap: wrap;
}

.feed-user {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--ink-1);
}

.feed-action {
  font-size: 0.875rem;
  color: var(--ink-4);
}

.feed-time {
  font-size: 0.8125rem;
  color: var(--ink-4);
  margin-left: auto;
}

.feed-content {
  font-size: 0.9375rem;
  color: var(--ink-2);
  line-height: 1.7;
  word-break: break-all;
  margin-bottom: var(--space-3);
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.feed-footer {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.feed-novel-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: 0.8125rem;
  color: var(--ink-4);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.feed-novel-link:hover {
  color: var(--gold-0);
}

.link-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

/* Skeleton */
.skeleton-card {
  align-items: flex-start;
}

.avatar-skeleton {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.skeleton-body {
  flex: 1;
}

/* Empty */
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
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  margin-top: var(--space-8);
  padding-bottom: var(--space-8);
}

.page-btn {
  padding: var(--space-2) var(--space-5);
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-md);
  background: var(--paper-0);
  font-size: 0.875rem;
  color: var(--ink-2);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.page-btn:hover:not(:disabled) {
  background: var(--paper-2);
  border-color: var(--ink-3);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 0.875rem;
  color: var(--ink-4);
}
</style>
