<template>
  <router-link :to="`/novel/${novel.id}`" class="novel-card">
    <div class="cover-wrap">
      <img
        v-if="novel.coverUrl"
        :src="novel.coverUrl"
        :alt="novel.title"
        class="cover-img"
        loading="lazy"
      />
      <div v-else class="cover-placeholder">
        <span>{{ novel.title?.charAt(0) }}</span>
      </div>
      <div class="cover-overlay">
        <span class="read-hint">阅读</span>
      </div>
    </div>
    <div class="card-body">
      <h3 class="card-title">{{ novel.title }}</h3>
      <p class="card-author">{{ novel.authorName }}</p>
      <p v-if="novel.summary" class="card-summary">{{ novel.summary }}</p>
      <div class="card-meta">
        <span class="tag">{{ novel.category }}</span>
        <span class="meta-stat">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/>
          </svg>
          {{ formatCount(novel.viewCount) }}
        </span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
defineProps({
  novel: { type: Object, required: true }
})

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  return String(n)
}
</script>

<style scoped>
.novel-card {
  display: block;
  text-decoration: none;
  color: inherit;
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: box-shadow var(--transition-base), transform var(--transition-base);
}

.novel-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-3px);
}

/* Cover */
.cover-wrap {
  position: relative;
  aspect-ratio: 3/4;
  overflow: hidden;
  background: var(--paper-2);
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--transition-slow);
}

.novel-card:hover .cover-img {
  transform: scale(1.05);
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--paper-2), var(--paper-3));
}

.cover-placeholder span {
  font-family: var(--font-serif);
  font-size: 3rem;
  font-weight: 700;
  color: var(--ink-4);
  opacity: 0.6;
}

.cover-overlay {
  position: absolute;
  inset: 0;
  background: rgba(26, 24, 20, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity var(--transition-base);
}

.novel-card:hover .cover-overlay {
  opacity: 1;
}

.read-hint {
  color: var(--paper-0);
  font-size: 0.875rem;
  letter-spacing: 0.1em;
  border: 1px solid rgba(255, 255, 255, 0.6);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-full);
}

/* Body */
.card-body {
  padding: var(--space-4);
}

.card-title {
  font-family: var(--font-serif);
  font-size: 1rem;
  font-weight: 600;
  color: var(--ink-0);
  margin-bottom: var(--space-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-author {
  font-size: 0.8125rem;
  color: var(--ink-4);
  margin-bottom: var(--space-2);
}

.card-summary {
  font-size: 0.8125rem;
  color: var(--ink-3);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: var(--space-3);
  line-height: 1.5;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.meta-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
  color: var(--ink-4);
}
</style>
