<template>
  <main class="page">
    <div class="container">
      <div class="square-header">
        <h1 class="square-title">书友动态</h1>
        <p class="square-subtitle">看看书友们最近在聊什么</p>
        <button v-if="userStore.isLoggedIn" class="btn btn-gold write-btn" @click="reviewOpen = true">写书评</button>
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
              <span class="feed-action">
                {{ item.bookTitle || item.novelId ? '推荐了' : '写了书评' }}
              </span>
              <span class="feed-time">{{ formatTime(item.createdAt) }}</span>
            </div>
            <!-- 书名标签（醒目展示，位于正文上方） -->
            <div v-if="item.bookTitle || item.novelId" class="feed-book-badge">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="badge-icon">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
              </svg>
              <router-link v-if="item.novelId" :to="`/novel/${item.novelId}`" class="badge-title badge-link">
                {{ item.novelId ? '查看原著' : '' }}
              </router-link>
              <span v-else class="badge-title">《{{ item.bookTitle }}》</span>
            </div>
            <p class="feed-content">{{ item.content }}</p>
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

    <!-- 写书评模态框 -->
    <Teleport to="body">
      <div v-if="reviewOpen" class="review-overlay" @click.self="reviewOpen = false">
        <div class="review-modal">
          <div class="review-header">
            <span>写书评</span>
            <button class="review-close" @click="reviewOpen = false">✕</button>
          </div>
          <div class="review-book-row">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="review-book-icon">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
            <input v-model="reviewBookTitle" class="review-input review-book-input" placeholder="推荐的书名（不填则匿名书评）" />
          </div>
          <textarea v-model="reviewContent" class="review-textarea" placeholder="写下你的书评…" rows="5"></textarea>
          <button class="btn btn-gold review-submit" :disabled="reviewSubmitting" @click="submitReview">
            {{ reviewSubmitting ? '提交中…' : '发布书评' }}
          </button>
        </div>
      </div>
    </Teleport>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { apiGetRecentComments, apiCreateComment } from '@/api/comment'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'

const { show } = useToast()
const userStore = useUserStore()

// ─── 写书评模态框 ──────────────────────────────────────────────
const reviewOpen = ref(false)
const reviewBookTitle = ref('')
const reviewContent = ref('')
const reviewSubmitting = ref(false)

async function submitReview() {
  if (!reviewContent.value.trim()) { show('请填写书评内容'); return }
  reviewSubmitting.value = true
  try {
    await apiCreateComment({
      bookTitle: reviewBookTitle.value.trim() || undefined,
      content: reviewContent.value.trim()
    })
    reviewOpen.value = false
    reviewBookTitle.value = ''
    reviewContent.value = ''
    await loadPage(1)
  } catch (e) {
    show(e.message)
  } finally {
    reviewSubmitting.value = false
  }
}

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

.write-btn {
  margin-top: var(--space-4);
  padding: var(--space-2) var(--space-6);
  font-size: 0.9375rem;
}

/* 书名标签 */
.feed-book-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: var(--gold-3);
  border: 1px solid var(--gold-2);
  border-radius: var(--radius-full);
  padding: 3px 10px;
  margin-bottom: var(--space-3);
  max-width: 100%;
  overflow: hidden;
}
.badge-icon { width: 13px; height: 13px; flex-shrink: 0; color: var(--gold-0); }
.badge-title {
  font-size: 0.8125rem;
  color: var(--gold-0);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.badge-link { text-decoration: none; }
.badge-link:hover { text-decoration: underline; }

/* 写书评模态框 */
.review-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.45); z-index: 500;
  display: flex; align-items: center; justify-content: center; padding: 20px;
}
.review-modal {
  background: var(--paper-0); border-radius: var(--radius-xl);
  padding: 24px; width: 100%; max-width: 480px;
  display: flex; flex-direction: column; gap: 16px;
  box-shadow: var(--shadow-lg);
}
.review-header {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 1rem; font-weight: 600; color: var(--ink-0);
}
.review-close {
  border: none; background: transparent; font-size: 1rem;
  cursor: pointer; color: var(--ink-3);
}
.review-input, .review-textarea {
  width: 100%; border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-md); padding: 10px 12px;
  font-size: 0.9375rem; color: var(--ink-1);
  background: var(--paper-1); resize: vertical;
  font-family: inherit; box-sizing: border-box;
}
.review-input:focus, .review-textarea:focus {
  outline: none; border-color: var(--gold-1);
}
.review-submit { align-self: flex-end; padding: var(--space-2) var(--space-8); }
.review-book-row {
  display: flex; align-items: center; gap: 8px;
  background: var(--gold-3); border: 1.5px solid var(--gold-2);
  border-radius: var(--radius-md); padding: 6px 12px;
}
.review-book-icon { width: 16px; height: 16px; color: var(--gold-0); flex-shrink: 0; }
.review-book-input {
  border: none; background: transparent; padding: 0; border-radius: 0;
  font-weight: 500;
}
.review-book-input:focus { outline: none; border: none; }
</style>
