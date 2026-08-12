<template>
  <main class="page">
    <div class="container">
      <!-- Loading -->
      <div v-if="loading" class="detail-loading">
        <div class="skeleton detail-cover-skeleton"></div>
        <div class="detail-info-skeleton">
          <div class="skeleton" style="height:32px;width:200px;margin-bottom:12px"></div>
          <div class="skeleton" style="height:16px;width:100px;margin-bottom:24px"></div>
          <div v-for="i in 4" :key="i" class="skeleton" style="height:14px;width:100%;margin-bottom:8px"></div>
        </div>
      </div>

      <template v-else-if="novel">
        <!-- Novel header -->
        <section class="detail-header">
          <div class="detail-cover">
            <img v-if="novel.coverUrl" :src="novel.coverUrl" :alt="novel.title" />
            <div v-else class="cover-fallback">
              <span>{{ novel.title?.charAt(0) }}</span>
            </div>
          </div>

          <div class="detail-meta">
            <div class="meta-top">
              <span class="tag">{{ novel.category }}</span>
              <span class="tag tag-gold">{{ novel.statusLabel }}</span>
            </div>

            <h1 class="detail-title">{{ novel.title }}</h1>
            <p class="detail-author">{{ novel.authorName }}</p>

            <div class="detail-stats">
              <div class="stat-item">
                <span class="stat-val">{{ formatCount(novel.viewCount) }}</span>
                <span class="stat-label">阅读</span>
              </div>
              <div class="stat-item">
                <span class="stat-val">{{ formatCount(novel.likeCount) }}</span>
                <span class="stat-label">点赞</span>
              </div>
              <div class="stat-item">
                <span class="stat-val">{{ formatCount(novel.favoriteCount) }}</span>
                <span class="stat-label">收藏</span>
              </div>
            </div>

            <p class="detail-summary">{{ novel.summary }}</p>

            <div class="detail-actions">
              <InteractionBar :target-id="novel.id" :target-type="1" />
              <button class="btn btn-primary" :disabled="checkedToday" @click="handleCheckin">
                {{ checkedToday ? '今日已打卡 ✓' : '打卡阅读' }}
              </button>
            </div>
          </div>
        </section>

        <!-- Owner controls -->
        <div v-if="isOwner" class="owner-bar">
          <button class="btn btn-ghost btn-sm" @click="showEdit = true">编辑</button>
          <button class="btn btn-sm delete-btn" @click="confirmDelete">删除</button>
        </div>

        <div class="divider"></div>

        <!-- Tabs -->
        <div class="detail-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="detail-tab"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >{{ tab.label }}</button>
        </div>

        <section v-if="activeTab === 'comment'" class="tab-content">
          <CommentSection :novel-id="novel.id" />
        </section>

        <section v-if="activeTab === 'checkin'" class="tab-content">
          <div class="checkin-wrap">
            <CheckinCalendar :novel-id="novel.id" />
          </div>
        </section>
      </template>

      <div v-else class="empty-state">
        <p>未找到该小说</p>
        <router-link to="/" class="btn btn-ghost mt-4">返回首页</router-link>
      </div>
    </div>

    <!-- Edit modal -->
    <Teleport to="body">
      <div v-if="showEdit" class="modal-mask" @click.self="showEdit = false">
        <div class="modal-body">
          <h3 class="modal-title">编辑小说信息</h3>
          <form @submit.prevent="saveEdit">
            <div class="form-group">
              <label class="form-label">书名</label>
              <input v-model="editForm.title" class="form-input" required />
            </div>
            <div class="form-group">
              <label class="form-label">作者名</label>
              <input v-model="editForm.authorName" class="form-input" required />
            </div>
            <div class="form-group">
              <label class="form-label">分类</label>
              <input v-model="editForm.category" class="form-input" />
            </div>
            <div class="form-group">
              <label class="form-label">封面URL</label>
              <input v-model="editForm.coverUrl" class="form-input" placeholder="https://..." />
            </div>
            <div class="form-group">
              <label class="form-label">简介</label>
              <textarea v-model="editForm.summary" class="form-input" rows="4"></textarea>
            </div>
            <div class="form-group">
              <label class="form-label">状态</label>
              <select v-model="editForm.status" class="form-input">
                <option :value="1">连载中</option>
                <option :value="2">已完结</option>
              </select>
            </div>
            <div v-if="editError" class="auth-error">{{ editError }}</div>
            <div class="modal-footer">
              <button type="button" class="btn btn-ghost" @click="showEdit = false">取消</button>
              <button type="submit" class="btn btn-primary" :disabled="editLoading">
                {{ editLoading ? '保存中…' : '保存' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </main>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiGetNovelDetail, apiUpdateNovel, apiDeleteNovel } from '@/api/novel'
import { apiCheckin } from '@/api/checkin'
import { apiRecordLevelAction } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import InteractionBar from '@/components/InteractionBar.vue'
import CommentSection from '@/components/CommentSection.vue'
import CheckinCalendar from '@/components/CheckinCalendar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { show } = useToast()
const { confirm } = useConfirmDialog()

const novel = ref(null)
const loading = ref(true)
const activeTab = ref('comment')
const checkedToday = ref(false)
const showEdit = ref(false)
const editLoading = ref(false)
const editError = ref('')

const tabs = [
  { key: 'comment', label: '书评' },
  { key: 'checkin', label: '打卡记录' }
]

const isOwner = computed(() =>
  userStore.isLoggedIn && novel.value?.userId === userStore.userInfo?.id
)

const editForm = reactive({
  title: '', authorName: '', category: '', coverUrl: '', summary: '', status: 1
})

async function loadDetail() {
  loading.value = true
  try {
    novel.value = await apiGetNovelDetail(route.params.id)
    if (novel.value) {
      Object.assign(editForm, {
        title: novel.value.title,
        authorName: novel.value.authorName,
        category: novel.value.category,
        coverUrl: novel.value.coverUrl ?? '',
        summary: novel.value.summary ?? '',
        status: novel.value.status ?? 1
      })
    }
  } catch (e) {
    show(e.message)
    novel.value = null
  } finally {
    loading.value = false
  }
}

async function handleCheckin() {
  if (!userStore.isLoggedIn) { show('请先登录'); return }
  try {
    await apiCheckin({ novelId: Number(route.params.id) })
    apiRecordLevelAction('CHECKIN').catch(() => {})
    checkedToday.value = true
    show('打卡成功！')
  } catch (e) {
    show(e.message)
  }
}

async function saveEdit() {
  editError.value = ''
  editLoading.value = true
  try {
    novel.value = await apiUpdateNovel(novel.value.id, editForm)
    showEdit.value = false
    show('已保存')
  } catch (e) {
    editError.value = e.message
  } finally {
    editLoading.value = false
  }
}

async function confirmDelete() {
  if (!await confirm({
    title: '删除小说',
    message: `确定删除《${novel.value.title}》吗？小说详情、内容及互动记录将无法恢复。`,
    confirmText: '删除小说',
    tone: 'danger'
  })) return
  try {
    await apiDeleteNovel(novel.value.id)
    show('已删除')
    router.push('/')
  } catch (e) {
    show(e.message)
  }
}

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  return String(n)
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-loading {
  display: flex;
  gap: var(--space-10);
  padding-top: var(--space-10);
}

.detail-cover-skeleton {
  width: 220px;
  height: 293px;
  border-radius: var(--radius-lg);
  flex-shrink: 0;
}

.detail-info-skeleton { flex: 1; }

.detail-header {
  display: flex;
  gap: var(--space-10);
  padding-top: var(--space-10);
  align-items: flex-start;
}

.detail-cover {
  width: 200px;
  flex-shrink: 0;
  aspect-ratio: 3/4;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
  background: var(--paper-2);
}

.detail-cover img { width: 100%; height: 100%; object-fit: cover; }

.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--paper-2), var(--paper-3));
}

.cover-fallback span {
  font-family: var(--font-serif);
  font-size: 4rem;
  font-weight: 700;
  color: var(--ink-4);
  opacity: 0.5;
}

.detail-meta { flex: 1; }

.meta-top {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.detail-title {
  font-family: var(--font-serif);
  font-size: 2rem;
  font-weight: 700;
  color: var(--ink-0);
  margin-bottom: var(--space-2);
}

.detail-author {
  font-size: 1rem;
  color: var(--ink-3);
  margin-bottom: var(--space-5);
}

.detail-stats {
  display: flex;
  gap: var(--space-8);
  margin-bottom: var(--space-5);
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-val {
  font-family: var(--font-serif);
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--ink-1);
}

.stat-label {
  font-size: 0.75rem;
  color: var(--ink-4);
}

.detail-summary {
  font-size: 0.9375rem;
  color: var(--ink-2);
  line-height: 1.9;
  margin-bottom: var(--space-6);
  white-space: pre-line;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  flex-wrap: wrap;
}

/* Owner bar */
.owner-bar {
  display: flex;
  gap: var(--space-3);
  justify-content: flex-end;
  padding: var(--space-3) 0;
}

.delete-btn {
  background: var(--vermilion-light);
  color: var(--vermilion);
  border: 1px solid #e8b4b8;
}
.delete-btn:hover { background: #f5c6c4; }

/* Tabs */
.detail-tabs {
  display: flex;
  border-bottom: 1px solid var(--paper-3);
  margin-bottom: var(--space-8);
}

.detail-tab {
  padding: var(--space-4) var(--space-6);
  border: none;
  background: transparent;
  font-size: 0.9375rem;
  color: var(--ink-3);
  cursor: pointer;
  position: relative;
  transition: color var(--transition-fast);
}

.detail-tab::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 0;
  height: 2px;
  background: var(--gold-0);
  border-radius: var(--radius-full) var(--radius-full) 0 0;
  transition: width var(--transition-base);
}

.detail-tab:hover { color: var(--ink-1); }

.detail-tab.active {
  color: var(--ink-0);
  font-weight: 500;
}

.detail-tab.active::after { width: 100%; }

.tab-content { padding-bottom: var(--space-12); }
.checkin-wrap { max-width: 380px; }

/* Empty */
.empty-state {
  text-align: center;
  padding: var(--space-16) 0;
  color: var(--ink-4);
}

/* Modal */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(26, 24, 20, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: var(--space-4);
}

.modal-body {
  background: var(--paper-0);
  border-radius: var(--radius-xl);
  padding: var(--space-8);
  width: 100%;
  max-width: 480px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
}

.modal-title {
  font-family: var(--font-serif);
  font-size: 1.125rem;
  margin-bottom: var(--space-6);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-6);
}

.auth-error {
  background: var(--vermilion-light);
  color: var(--vermilion);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  font-size: 0.875rem;
  margin-bottom: var(--space-4);
}

@media (max-width: 640px) {
  .detail-header { flex-direction: column; align-items: center; }
  .detail-cover { width: 160px; }
  .detail-meta { width: 100%; }
  .detail-stats { justify-content: center; }
  .detail-title { font-size: 1.5rem; text-align: center; }
  .detail-author { text-align: center; }
}
</style>
