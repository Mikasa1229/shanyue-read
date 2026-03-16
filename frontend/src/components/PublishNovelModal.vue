<template>
  <Teleport to="body">
    <div class="modal-mask" @click.self="$emit('close')">
      <div class="modal-body">
        <h3 class="modal-title">发布新小说</h3>
        <form @submit.prevent="handleSubmit">
          <div class="form-group">
            <label class="form-label">书名 *</label>
            <input v-model="form.title" class="form-input" placeholder="小说标题" required />
          </div>
          <div class="form-group">
            <label class="form-label">作者名 *</label>
            <input v-model="form.authorName" class="form-input" placeholder="作者笔名" required />
          </div>
          <div class="form-group">
            <label class="form-label">分类 *</label>
            <select v-model="form.category" class="form-input" required>
              <option value="">请选择分类</option>
              <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">封面图片URL</label>
            <input v-model="form.coverUrl" class="form-input" placeholder="https://..." />
          </div>
          <div class="form-group">
            <label class="form-label">简介</label>
            <textarea v-model="form.summary" class="form-input" rows="4" placeholder="用一段话介绍这部作品…" maxlength="2000"></textarea>
          </div>
          <div class="form-group">
            <label class="form-label">连载状态 *</label>
            <div class="radio-group">
              <label class="radio-label">
                <input type="radio" v-model="form.status" :value="1" />
                连载中
              </label>
              <label class="radio-label">
                <input type="radio" v-model="form.status" :value="2" />
                已完结
              </label>
            </div>
          </div>

          <div v-if="error" class="form-error">{{ error }}</div>

          <div class="modal-footer">
            <button type="button" class="btn btn-ghost" @click="$emit('close')">取消</button>
            <button type="submit" class="btn btn-gold" :disabled="loading">
              {{ loading ? '发布中…' : '立即发布' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { apiCreateNovel } from '@/api/novel'
import { useToast } from '@/composables/useToast'

const emit = defineEmits(['close', 'published'])
const { show } = useToast()

const categories = ['玄幻', '言情', '武侠', '都市', '历史', '科幻', '悬疑', '其他']

const form = reactive({
  title: '',
  authorName: '',
  category: '',
  coverUrl: '',
  summary: '',
  status: 1
})

const loading = ref(false)
const error = ref('')

async function handleSubmit() {
  error.value = ''
  loading.value = true
  try {
    const novel = await apiCreateNovel(form)
    show('发布成功！')
    emit('published', novel)
    emit('close')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
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
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
}

.modal-title {
  font-family: var(--font-serif);
  font-size: 1.25rem;
  color: var(--ink-0);
  margin-bottom: var(--space-6);
}

.radio-group {
  display: flex;
  gap: var(--space-6);
}

.radio-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 0.9375rem;
  color: var(--ink-2);
  cursor: pointer;
}

.radio-label input { accent-color: var(--gold-0); }

.form-error {
  background: var(--vermilion-light);
  color: var(--vermilion);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  font-size: 0.875rem;
  margin-bottom: var(--space-4);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-6);
}
</style>
