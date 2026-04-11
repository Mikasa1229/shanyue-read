<template>
  <div class="comment-section">
    <h3 class="section-title">
      <span>书评</span>
      <span class="count-badge">{{ pagination.total }}</span>
    </h3>

    <!-- Submit area -->
    <div v-if="userStore.isLoggedIn" class="comment-composer">
      <div class="composer-avatar">
        <span>{{ userStore.userInfo?.nickname?.charAt(0) ?? '读' }}</span>
      </div>
      <div class="composer-body">
        <textarea
          v-model="newContent"
          class="form-input composer-input"
          :placeholder="replyTo ? `回复 @${replyTo.nickname}：` : '写下你的感想…'"
          maxlength="1000"
          rows="3"
          @keydown.ctrl.enter="submitComment"
        ></textarea>
        <div v-if="!replyTo" class="composer-score-row">
          <span class="composer-score-label">评分</span>
          <div class="composer-stars">
            <button
              v-for="star in 5"
              :key="star"
              class="composer-star"
              :class="{ active: star <= newScore }"
              @click="newScore = star"
            >★</button>
          </div>
          <span class="composer-score-val">{{ newScore }} 分</span>
        </div>
        <div class="composer-footer">
          <span v-if="replyTo" class="reply-hint">
            回复 <strong>@{{ replyTo.nickname }}</strong>
            <button class="cancel-reply" @click="cancelReply">×</button>
          </span>
          <span class="char-count">{{ newContent.length }}/1000</span>
          <button
            class="btn btn-primary btn-sm"
            :disabled="!newContent.trim() || submitting"
            @click="submitComment"
          >
            {{ submitting ? '发送中…' : '发送' }}
          </button>
        </div>
      </div>
    </div>
    <div v-else class="login-hint">
      <router-link to="/login">登录</router-link> 后参与书评讨论
    </div>

    <div class="divider"></div>

    <!-- Comment list -->
    <div v-if="loading" class="comments-loading">
      <div v-for="i in 3" :key="i" class="skeleton-comment">
        <div class="skeleton" style="width:36px;height:36px;border-radius:50%"></div>
        <div style="flex:1;display:flex;flex-direction:column;gap:8px">
          <div class="skeleton" style="width:100px;height:14px"></div>
          <div class="skeleton" style="width:100%;height:14px"></div>
          <div class="skeleton" style="width:70%;height:14px"></div>
        </div>
      </div>
    </div>

    <template v-else>
      <div v-if="comments.length === 0" class="empty-comments">
        还没有书评，来写第一条吧
      </div>

      <div v-for="comment in comments" :key="comment.id" class="comment-item">
        <div class="comment-avatar">
          <img v-if="comment.userAvatar" :src="comment.userAvatar" :alt="comment.userNickname" />
          <span v-else>{{ comment.userNickname?.charAt(0) }}</span>
        </div>
        <div class="comment-body">
          <div class="comment-meta">
            <span class="comment-name">{{ comment.userNickname }}</span>
            <span v-if="comment.score" class="comment-score">{{ comment.score.toFixed(1) }} 分</span>
            <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
          </div>
          <p class="comment-content">{{ comment.content }}</p>
          <div class="comment-actions">
            <button class="action-text" @click="startReply(comment)">回复</button>
            <button
              v-if="userStore.userInfo?.id === comment.userId"
              class="action-text delete"
              @click="deleteComment(comment.id)"
            >删除</button>
          </div>

          <!-- Replies -->
          <div v-if="comment.replies?.length" class="replies">
            <div
              v-for="reply in comment.replies"
              :key="reply.id"
              class="reply-item"
            >
              <div class="comment-avatar avatar-sm">
                <img v-if="reply.userAvatar" :src="reply.userAvatar" />
                <span v-else>{{ reply.userNickname?.charAt(0) }}</span>
              </div>
              <div class="comment-body">
                <div class="comment-meta">
                  <span class="comment-name">{{ reply.userNickname }}</span>
                  <span class="comment-time">{{ formatTime(reply.createdAt) }}</span>
                </div>
                <p class="comment-content">{{ reply.content }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="pagination.pages > 1" class="pagination">
        <button
          v-for="p in pagination.pages"
          :key="p"
          class="page-btn"
          :class="{ active: p === pagination.current }"
          @click="loadPage(p)"
        >{{ p }}</button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { apiCreateComment, apiDeleteComment, apiGetComments } from '@/api/comment'
import { apiRecordLevelAction } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'

const props = defineProps({
  novelId: { type: [Number, String], required: true }
})

const userStore = useUserStore()
const { show } = useToast()

const comments = ref([])
const loading = ref(false)
const submitting = ref(false)
const newContent = ref('')
const newScore = ref(4)
const replyTo = ref(null)
const pagination = ref({ current: 1, pages: 1, total: 0 })

async function loadPage(page = 1) {
  loading.value = true
  try {
    const res = await apiGetComments(props.novelId, page)
    comments.value = res.records ?? []
    pagination.value = {
      current: res.current ?? page,
      pages: res.pages ?? 1,
      total: res.total ?? 0
    }
  } catch (e) {
    show(e.message)
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  if (!newContent.value.trim()) return
  submitting.value = true
  try {
    await apiCreateComment({
      novelId: Number(props.novelId),
      content: newContent.value.trim(),
      score: replyTo.value ? undefined : newScore.value,
      parentId: replyTo.value?.id ?? null
    })
    apiRecordLevelAction('COMMENT').catch(() => {})
    if (!replyTo.value) apiRecordLevelAction('RATE').catch(() => {})
    show('发送成功')
    newContent.value = ''
    newScore.value = 4
    replyTo.value = null
    await loadPage(1)
  } catch (e) {
    show(e.message)
  } finally {
    submitting.value = false
  }
}

async function deleteComment(id) {
  try {
    await apiDeleteComment(id)
    show('已删除')
    await loadPage(pagination.value.current)
  } catch (e) {
    show(e.message)
  }
}

function startReply(comment) {
  replyTo.value = { id: comment.id, nickname: comment.userNickname }
  document.querySelector('.composer-input')?.focus()
}

function cancelReply() {
  replyTo.value = null
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
.section-title {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
  font-family: var(--font-serif);
  font-size: 1.125rem;
}

.count-badge {
  font-family: var(--font-sans);
  font-size: 0.8125rem;
  font-weight: 400;
  color: var(--ink-4);
}

/* Composer */
.comment-composer {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
}

.composer-avatar {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
  background: var(--gold-3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-serif);
  font-size: 1rem;
  color: var(--gold-0);
}

.composer-body { flex: 1; }

.composer-input {
  width: 100%;
  font-size: 0.9375rem;
  resize: none;
}

.composer-score-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: var(--space-2);
}

.composer-score-label {
  font-size: 0.8125rem;
  color: var(--ink-3);
}

.composer-stars {
  display: inline-flex;
  gap: 2px;
}

.composer-star {
  border: none;
  background: transparent;
  color: var(--paper-3);
  font-size: 1rem;
  cursor: pointer;
  padding: 0;
}

.composer-star.active {
  color: var(--gold-0);
}

.composer-score-val {
  font-size: 0.75rem;
  color: var(--gold-0);
}

.composer-footer {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-2);
  justify-content: flex-end;
}

.char-count {
  font-size: 0.75rem;
  color: var(--ink-4);
  margin-right: auto;
}

.reply-hint {
  font-size: 0.8125rem;
  color: var(--ink-3);
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.cancel-reply {
  border: none;
  background: none;
  color: var(--ink-4);
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
  padding: 0 2px;
}

.login-hint {
  font-size: 0.9375rem;
  color: var(--ink-4);
  text-align: center;
  padding: var(--space-5) 0;
}

/* Loading skeletons */
.skeleton-comment {
  display: flex;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

/* Empty */
.empty-comments {
  text-align: center;
  color: var(--ink-4);
  font-size: 0.9375rem;
  padding: var(--space-10) 0;
}

/* Comment item */
.comment-item {
  display: flex;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.comment-avatar {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
  overflow: hidden;
  background: var(--paper-2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-serif);
  color: var(--ink-3);
}

.avatar-sm {
  width: 28px;
  height: 28px;
  font-size: 0.75rem;
}

.comment-avatar img { width: 100%; height: 100%; object-fit: cover; }

.comment-body { flex: 1; min-width: 0; }

.comment-meta {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.comment-name {
  font-size: 0.9375rem;
  font-weight: 500;
  color: var(--ink-1);
}

.comment-time {
  font-size: 0.75rem;
  color: var(--ink-4);
}

.comment-score {
  font-size: 0.75rem;
  color: var(--gold-0);
  background: var(--gold-3);
  border-radius: var(--radius-full);
  padding: 2px 8px;
}

.comment-content {
  font-size: 0.9375rem;
  color: var(--ink-2);
  line-height: 1.7;
  word-break: break-all;
}

.comment-actions {
  display: flex;
  gap: var(--space-4);
  margin-top: var(--space-2);
}

.action-text {
  border: none;
  background: none;
  font-size: 0.8125rem;
  color: var(--ink-4);
  cursor: pointer;
  padding: 0;
  transition: color var(--transition-fast);
}

.action-text:hover { color: var(--ink-2); }
.action-text.delete:hover { color: var(--vermilion); }

/* Replies */
.replies {
  margin-top: var(--space-4);
  padding: var(--space-4);
  background: var(--paper-1);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--paper-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.reply-item {
  display: flex;
  gap: var(--space-3);
}

/* Pagination */
.pagination {
  display: flex;
  justify-content: center;
  gap: var(--space-2);
  margin-top: var(--space-8);
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
</style>
