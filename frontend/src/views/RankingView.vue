<template>
  <main class="page">
    <div class="container">

      <div class="page-header">
        <h1 class="page-title">阅读排行榜</h1>
        <p class="page-desc">累计阅读时长最多的书友</p>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="spinner-wrap"><div class="spinner"></div></div>

      <!-- 空状态 -->
      <div v-else-if="list.length === 0" class="empty-state">
        <div class="empty-icon">🏆</div>
        <p>暂无排行数据，去阅读吧！</p>
        <router-link to="/book-sources" class="btn btn-gold mt-4">去书源阅读</router-link>
      </div>

      <!-- 排行列表 -->
      <div v-else class="ranking-list">
        <!-- 前三名 podium -->
        <div class="podium" v-if="list.length >= 1">
          <div v-if="list[1]" class="podium-item rank-2">
            <div class="podium-avatar">
              <img v-if="list[1].avatar" :src="list[1].avatar" :alt="list[1].nickname" />
              <span v-else class="avatar-placeholder">{{ list[1].nickname?.charAt(0) ?? '读' }}</span>
            </div>
            <div class="podium-name">{{ list[1].nickname }}</div>
            <div class="podium-time">{{ list[1].readingTime }}</div>
            <div class="podium-base rank-2-base">2</div>
          </div>
          <div class="podium-item rank-1">
            <div class="crown">👑</div>
            <div class="podium-avatar large">
              <img v-if="list[0].avatar" :src="list[0].avatar" :alt="list[0].nickname" />
              <span v-else class="avatar-placeholder">{{ list[0].nickname?.charAt(0) ?? '读' }}</span>
            </div>
            <div class="podium-name">{{ list[0].nickname }}</div>
            <div class="podium-time">{{ list[0].readingTime }}</div>
            <div class="podium-base rank-1-base">1</div>
          </div>
          <div v-if="list[2]" class="podium-item rank-3">
            <div class="podium-avatar">
              <img v-if="list[2].avatar" :src="list[2].avatar" :alt="list[2].nickname" />
              <span v-else class="avatar-placeholder">{{ list[2].nickname?.charAt(0) ?? '读' }}</span>
            </div>
            <div class="podium-name">{{ list[2].nickname }}</div>
            <div class="podium-time">{{ list[2].readingTime }}</div>
            <div class="podium-base rank-3-base">3</div>
          </div>
        </div>

        <!-- 4名以后的列表 -->
        <div v-if="list.length > 3" class="rank-table">
          <div
            v-for="item in list.slice(3)"
            :key="item.userId"
            class="rank-row"
          >
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

    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { apiGetRanking } from '@/api/reading'

const list = ref([])
const loading = ref(true)

async function loadRanking() {
  loading.value = true
  try {
    const res = await apiGetRanking(50)
    list.value = res ?? []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => loadRanking())
</script>

<style scoped>
.page-header {
  padding: var(--space-8) 0 var(--space-6);
  text-align: center;
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

.crown {
  font-size: 1.5rem;
  line-height: 1;
}

.podium-avatar {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-full);
  overflow: hidden;
  border: 3px solid var(--paper-3);
  display: flex;
  align-items: center;
  justify-content: center;
}
.podium-avatar.large {
  width: 72px;
  height: 72px;
  border-color: var(--gold-0);
}
.podium-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-placeholder {
  font-family: var(--font-serif);
  font-size: 1.25rem;
  color: var(--gold-0);
  background: var(--gold-3);
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.podium-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--ink-0);
  max-width: 80px;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.podium-time {
  font-size: 0.75rem;
  color: var(--ink-3);
}

.podium-base {
  width: 56px;
  border-radius: var(--radius-md) var(--radius-md) 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 1.25rem;
  color: white;
}
.rank-1-base { height: 72px; background: linear-gradient(180deg, #f5c842, #d4a017); width: 72px; }
.rank-2-base { height: 52px; background: linear-gradient(180deg, #c0c0c0, #9e9e9e); }
.rank-3-base { height: 40px; background: linear-gradient(180deg, #cd7f32, #a0522d); }

/* ── Rank Table ──────────────────────────────────── */
.rank-table {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  max-width: 600px;
  margin: 0 auto;
}
.rank-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-md);
  transition: box-shadow var(--transition-fast);
}
.rank-row:hover {
  box-shadow: var(--shadow-xs);
}
.rank-num {
  width: 28px;
  text-align: center;
  font-size: 0.875rem;
  font-weight: 700;
  color: var(--ink-3);
}
.rank-avatar {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  overflow: hidden;
  border: 2px solid var(--paper-3);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.rank-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-placeholder.sm {
  font-size: 0.875rem;
}
.rank-name {
  flex: 1;
  font-size: 0.9375rem;
  color: var(--ink-0);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-time {
  font-size: 0.875rem;
  color: var(--gold-0);
  font-weight: 600;
  white-space: nowrap;
}
</style>
