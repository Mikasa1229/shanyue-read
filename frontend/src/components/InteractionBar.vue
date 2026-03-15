<template>
  <div class="interaction-bar">
    <!-- Like -->
    <button
      class="action-btn"
      :class="{ active: likeActive }"
      :disabled="loading"
      @click="toggle('like')"
      :title="likeActive ? '取消点赞' : '点赞'"
    >
      <svg class="action-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
      </svg>
      <span>{{ likeCount }}</span>
    </button>

    <!-- Favorite -->
    <button
      class="action-btn"
      :class="{ active: favActive, 'fav-active': favActive }"
      :disabled="loading"
      @click="toggle('favorite')"
      :title="favActive ? '取消收藏' : '收藏'"
    >
      <svg class="action-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
      </svg>
      <span>{{ favCount }}</span>
    </button>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { apiToggleInteraction, apiGetInteractionStatus } from '@/api/interaction'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'

const props = defineProps({
  targetId: { type: [Number, String], required: true },
  targetType: { type: Number, default: 1 } // 1=NOVEL, 2=COMMENT
})

const userStore = useUserStore()
const { show } = useToast()

const likeActive = ref(false)
const favActive = ref(false)
const likeCount = ref(0)
const favCount = ref(0)
const loading = ref(false)

onMounted(async () => {
  if (!userStore.isLoggedIn) return
  try {
    const res = await apiGetInteractionStatus({
      targetIds: [Number(props.targetId)],
      targetType: props.targetType,
      action: 1 // LIKE
    })
    const entry = res?.[String(props.targetId)]
    if (entry) {
      likeActive.value = entry.active
      likeCount.value = entry.count
    }

    const res2 = await apiGetInteractionStatus({
      targetIds: [Number(props.targetId)],
      targetType: props.targetType,
      action: 2 // FAVORITE
    })
    const entry2 = res2?.[String(props.targetId)]
    if (entry2) {
      favActive.value = entry2.active
      favCount.value = entry2.count
    }
  } catch (_) {}
})

async function toggle(type) {
  if (!userStore.isLoggedIn) {
    show('请先登录')
    return
  }
  if (loading.value) return
  loading.value = true
  try {
    const action = type === 'like' ? 1 : 2
    const res = await apiToggleInteraction({
      targetId: Number(props.targetId),
      targetType: props.targetType,
      action
    })
    if (type === 'like') {
      likeActive.value = res.active
      likeCount.value = res.count
    } else {
      favActive.value = res.active
      favCount.value = res.count
    }
  } catch (e) {
    show(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.interaction-bar {
  display: inline-flex;
  align-items: center;
  gap: var(--space-4);
}

.action-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border: 1.5px solid var(--paper-3);
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--ink-3);
  font-size: 0.875rem;
  cursor: pointer;
  transition: all var(--transition-fast);
  user-select: none;
}

.action-btn:hover {
  border-color: var(--ink-3);
  color: var(--ink-1);
  background: var(--paper-2);
}

.action-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  transition: transform var(--transition-fast);
}

.action-btn:hover .action-icon {
  transform: scale(1.15);
}

/* Like active state */
.action-btn.active {
  border-color: #e8b4b8;
  color: var(--vermilion);
  background: var(--vermilion-light);
}

.action-btn.active .action-icon {
  fill: var(--vermilion);
  stroke: var(--vermilion);
}

/* Fav active state */
.action-btn.fav-active {
  border-color: var(--gold-2);
  color: var(--gold-0);
  background: var(--gold-3);
}

.action-btn.fav-active .action-icon {
  fill: var(--gold-1);
  stroke: var(--gold-1);
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
