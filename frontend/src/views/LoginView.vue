<template>
  <div class="auth-page">
    <div class="auth-card">
      <!-- Brand -->
      <div class="auth-brand">
        <span class="brand-char">善</span>
        <span class="brand-text">阅坊</span>
      </div>
      <p class="auth-tagline">小说阅读分享，与同好相遇</p>

      <form @submit.prevent="handleLogin" class="auth-form">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <input
            v-model="form.username"
            type="text"
            class="form-input"
            placeholder="请输入用户名"
            autocomplete="username"
            required
          />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <div class="input-wrap">
            <input
              v-model="form.password"
              :type="showPwd ? 'text' : 'password'"
              class="form-input"
              placeholder="请输入密码"
              autocomplete="current-password"
              required
            />
            <button type="button" class="toggle-pwd" @click="showPwd = !showPwd" tabindex="-1">
              {{ showPwd ? '隐藏' : '显示' }}
            </button>
          </div>
        </div>

        <div v-if="error" class="auth-error">{{ error }}</div>

        <button type="submit" class="btn btn-primary auth-submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>

      <div class="auth-switch">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>

    <!-- Decorative background quotes -->
    <div class="bg-quotes" aria-hidden="true">
      <span>「书山有路勤为径」</span>
      <span>「腹有诗书气自华」</span>
      <span>「读书破万卷」</span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')
const showPwd = ref(false)

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    await userStore.login(form)
    const redirect = route.query.redirect
    router.push(redirect ? String(redirect) : '/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: var(--paper-1);
}

/* Background deco */
.bg-quotes {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: var(--space-8);
  padding-right: 12%;
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

/* Card */
.auth-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-xl);
  padding: var(--space-10) var(--space-8);
  box-shadow: var(--shadow-lg);
}

/* Brand */
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

/* Form */
.auth-form { display: flex; flex-direction: column; }

.input-wrap {
  position: relative;
}

.input-wrap .form-input {
  width: 100%;
  padding-right: 56px;
}

.toggle-pwd {
  position: absolute;
  right: var(--space-3);
  top: 50%;
  transform: translateY(-50%);
  border: none;
  background: none;
  font-size: 0.75rem;
  color: var(--ink-4);
  cursor: pointer;
  padding: 2px 4px;
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
