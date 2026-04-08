<template>
  <header class="nav-header" :class="{ scrolled: isScrolled }">
    <div class="container nav-inner">
      <!-- Logo -->
      <router-link to="/" class="nav-logo">
        <span class="logo-char">善</span>
        <span class="logo-text">阅坊</span>
      </router-link>

      <!-- Center Nav -->
      <nav class="nav-links">
        <router-link to="/" class="nav-link">发现</router-link>
        <router-link to="/square" class="nav-link">广场</router-link>
        <router-link to="/book-sources" class="nav-link">书源</router-link>
        <router-link to="/ranking" class="nav-link">排行</router-link>
        <router-link v-if="userStore.isLoggedIn" to="/bookshelf" class="nav-link">书架</router-link>
      </nav>

      <!-- Right Actions -->
      <div class="nav-actions">
        <template v-if="userStore.isLoggedIn">
          <router-link to="/profile" class="nav-avatar" :title="userStore.userInfo?.nickname">
            <img
              v-if="userStore.userInfo?.avatar"
              :src="userStore.userInfo.avatar"
              :alt="userStore.userInfo.nickname"
            />
            <span v-else class="avatar-placeholder">
              {{ userStore.userInfo?.nickname?.charAt(0) ?? '读' }}
            </span>
          </router-link>
          <button class="btn btn-ghost btn-sm" @click="handleLogout">退出</button>
        </template>
        <template v-else>
          <router-link to="/login" class="btn btn-ghost btn-sm">登录</router-link>
          <router-link to="/register" class="btn btn-gold btn-sm">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const isScrolled = ref(false)

function onScroll() {
  isScrolled.value = window.scrollY > 20
}

onMounted(() => window.addEventListener('scroll', onScroll))
onUnmounted(() => window.removeEventListener('scroll', onScroll))

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.nav-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 64px;
  background: rgba(250, 246, 239, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid transparent;
  transition: all var(--transition-base);
}

.nav-header.scrolled {
  border-bottom-color: var(--paper-3);
  box-shadow: var(--shadow-xs);
}

.nav-inner {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* Logo */
.nav-logo {
  display: flex;
  align-items: center;
  gap: 2px;
  text-decoration: none;
  color: inherit;
}

.logo-char {
  font-family: var(--font-serif);
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gold-0);
  line-height: 1;
}

.logo-text {
  font-family: var(--font-serif);
  font-size: 1.25rem;
  font-weight: 500;
  color: var(--ink-0);
  letter-spacing: 0.05em;
}

/* Center nav */
.nav-links {
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

.nav-link {
  font-size: 0.9375rem;
  color: var(--ink-3);
  text-decoration: none;
  padding: var(--space-2) 0;
  position: relative;
  transition: color var(--transition-fast);
}

.nav-link::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 0;
  height: 2px;
  background: var(--gold-0);
  border-radius: var(--radius-full);
  transition: width var(--transition-base);
}

.nav-link:hover,
.nav-link.router-link-active {
  color: var(--ink-0);
}

.nav-link.router-link-active::after {
  width: 100%;
}

/* Right actions */
.nav-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

/* Avatar */
.nav-avatar {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  overflow: hidden;
  border: 2px solid var(--paper-3);
  transition: border-color var(--transition-fast);
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-avatar:hover {
  border-color: var(--gold-1);
}

.nav-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  font-family: var(--font-serif);
  font-size: 1rem;
  color: var(--gold-0);
  background: var(--gold-3);
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 600px) {
  .nav-links { display: none; }
}
</style>
