<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-brand">
        <span class="brand-char">善</span>
        <span class="brand-text">阅坊</span>
      </div>
      <p class="auth-tagline">加入我们，开始你的阅读之旅</p>

      <form @submit.prevent="handleRegister" class="auth-form">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <input
            v-model="form.username"
            type="text"
            class="form-input"
            placeholder="字母、数字或下划线，3-20位"
            autocomplete="username"
            required
          />
          <span class="form-hint">仅支持英文字母、数字、下划线</span>
        </div>
        <div class="form-group">
          <label class="form-label">昵称</label>
          <input
            v-model="form.nickname"
            type="text"
            class="form-input"
            placeholder="2-20个字符"
            required
          />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input
            v-model="form.password"
            type="password"
            class="form-input"
            placeholder="至少6位"
            autocomplete="new-password"
            required
          />
        </div>
        <div class="form-group">
          <label class="form-label">确认密码</label>
          <input
            v-model="confirmPassword"
            type="password"
            class="form-input"
            placeholder="再次输入密码"
            autocomplete="new-password"
            required
          />
        </div>

        <div v-if="error" class="auth-error">{{ error }}</div>

        <button type="submit" class="btn btn-gold auth-submit" :disabled="loading">
          {{ loading ? '注册中…' : '创建账号' }}
        </button>
      </form>

      <div class="auth-switch">
        已有账号？
        <router-link to="/login">立即登录</router-link>
      </div>
    </div>

    <div class="bg-quotes" aria-hidden="true">
      <span>「开卷有益」</span>
      <span>「读万卷书行万里路」</span>
      <span>「书中自有颜如玉」</span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useWelcomeAnnouncement } from '@/composables/useWelcomeAnnouncement'

const router = useRouter()
const userStore = useUserStore()
const { show: showWelcomeAnnouncement } = useWelcomeAnnouncement()

const form = reactive({ username: '', nickname: '', password: '' })
const confirmPassword = ref('')
const loading = ref(false)
const error = ref('')

async function handleRegister() {
  error.value = ''
  if (form.password !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await userStore.register(form)
    // Auto login after register
    await userStore.login({ username: form.username, password: form.password })
    showWelcomeAnnouncement()
    router.push('/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* Reuse auth-page styles from LoginView */
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: var(--paper-1);
}

.bg-quotes {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: var(--space-8);
  padding-left: 12%;
  pointer-events: none;
}

.bg-quotes span {
  font-family: var(--font-serif);
  font-size: clamp(1.2rem, 3vw, 2.2rem);
  color: var(--paper-3);
  writing-mode: vertical-rl;
  letter-spacing: 0.2em;
  opacity: 0.6;
}

.auth-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-xl);
  padding: var(--space-10) var(--space-8);
  box-shadow: var(--shadow-lg);
}

.auth-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-bottom: var(--space-2);
}

.brand-char {
  font-family: var(--font-serif);
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--gold-0);
  line-height: 1;
}

.brand-text {
  font-family: var(--font-serif);
  font-size: 2rem;
  font-weight: 500;
  color: var(--ink-0);
}

.auth-tagline {
  text-align: center;
  font-size: 0.875rem;
  color: var(--ink-4);
  margin-bottom: var(--space-8);
}

.auth-form { display: flex; flex-direction: column; }

.form-hint {
  font-size: 0.75rem;
  color: var(--ink-4);
  margin-top: 4px;
}

.auth-error {
  background: var(--vermilion-light);
  color: var(--vermilion);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  font-size: 0.875rem;
  margin-bottom: var(--space-4);
}

.auth-submit {
  width: 100%;
  justify-content: center;
  padding: var(--space-4);
  font-size: 1rem;
  margin-top: var(--space-2);
  border-radius: var(--radius-md);
}

.auth-switch {
  text-align: center;
  font-size: 0.875rem;
  color: var(--ink-4);
  margin-top: var(--space-5);
}

@media (max-width: 480px) {
  .auth-card { margin: var(--space-4); padding: var(--space-8) var(--space-6); }
  .bg-quotes { display: none; }
}
</style>
