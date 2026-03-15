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
          <!-- Cover -->
          <div class="detail-cover">
            <img v-if="novel.coverUrl" :src="novel.coverUrl" :alt="novel.title" />
            <div v-else class="cover-fallback">
              <span>{{ novel.title?.charAt(0) }}</span>
            </div>
          </div>

          <!-- Meta -->
          <div class="detail-meta">
            <div class="meta-top">
              <span class="tag">{{ novel.category }}</span>
              <span class="tag" v-if="novel.status">{{ novel.status }}</span>
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

            <!-- Actions -->
            <div class="detail-actions">
              <InteractionBar :target-id="novel.id" :target-type="1" />
              <button class="btn btn-primary" @click="handleCheckin">
                {{ checkedToday ? '今日已打卡 ✓' : '打卡阅读' }}
              </button>
            </div>
          </div>
        </section>

        <div class="divider"></div>

        <!-- Tab content -->
        <div class="detail-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="detail-tab"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >{{ tab.label }}</button>
        </div>

        <!-- Comment tab -->
        <section v-if="activeTab === 'comment'" class="tab-content">
          <CommentSection :novel-id="novel.id" />
        </section>

        <!-- Checkin tab -->
        <section v-if="activeTab === 'checkin'" class="tab-content">
          <div class="checkin-wrap">
            <CheckinCalendar :novel-id="novel.id" @checked="checkedToday = true" />
          </div>
        </section>
      </template>

      <div v-else class="empty-state">
        <p>未找到该小说</p>
        <router-link to="/" class="btn btn-ghost mt-4">返回首页</router-link>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { apiCheckin } from '@/api/checkin'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import InteractionBar from '@/components/InteractionBar.vue'
import CommentSection from '@/components/CommentSection.vue'
import CheckinCalendar from '@/components/CheckinCalendar.vue'

const route = useRoute()
const userStore = useUserStore()
const { show } = useToast()

const novel = ref(null)
const loading = ref(true)
const activeTab = ref('comment')
const checkedToday = ref(false)

const tabs = [
  { key: 'comment', label: '书评' },
  { key: 'checkin', label: '打卡记录' }
]

// Mock single novel detail
function getMockNovel(id) {
  const map = {
    1: { id: 1, title: '星辰之上', authorName: '沧月', category: '玄幻', status: '连载中', summary: '一个关于星辰与命运的史诗故事，穿越虚空，寻找古老神明的足迹。少年自荒芜星域崛起，踏遍万千世界，只为触碰那遥远的星光。\n\n这是一段关于执念与自由的传说，是对命运最倔强的抵抗。', viewCount: 128000, likeCount: 12400, favoriteCount: 8600 },
    2: { id: 2, title: '锦绣未央', authorName: '秦简', category: '言情', status: '已完结', summary: '宫廷深处，爱恨纠缠，她以弱女子之身，步步为营，终成一代传奇。', viewCount: 95600, likeCount: 9200, favoriteCount: 6800 },
  }
  return map[id] ?? { id, title: `小说 #${id}`, authorName: '佚名', category: '未知', summary: '暂无简介', viewCount: 0, likeCount: 0, favoriteCount: 0 }
}

async function handleCheckin() {
  if (!userStore.isLoggedIn) { show('请先登录'); return }
  if (checkedToday.value) return
  try {
    await apiCheckin({ novelId: Number(route.params.id) })
    checkedToday.value = true
    show('打卡成功！')
  } catch (e) {
    show(e.message)
  }
}

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  return String(n)
}

onMounted(async () => {
  await new Promise(r => setTimeout(r, 300))
  novel.value = getMockNovel(Number(route.params.id))
  loading.value = false
})
</script>

<style scoped>
/* Loading */
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

/* Header */
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

.detail-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

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

/* Tabs */
.detail-tabs {
  display: flex;
  gap: 0;
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

.detail-tab.active::after {
  width: 100%;
}

.tab-content { padding-bottom: var(--space-12); }

.checkin-wrap { max-width: 380px; }

/* Empty */
.empty-state {
  text-align: center;
  padding: var(--space-16) 0;
  color: var(--ink-4);
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
