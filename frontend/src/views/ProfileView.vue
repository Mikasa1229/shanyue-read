<template>
  <main class="page">
    <div class="container">
      <div class="profile-layout">
        <!-- Left: user card -->
        <aside class="profile-sidebar">
          <div class="user-card">
            <div class="user-avatar-wrap">
              <div class="user-avatar">
                <img v-if="userInfo?.avatar" :src="userInfo.avatar" :alt="userInfo.nickname" />
                <span v-else class="avatar-text">{{ userInfo?.nickname?.charAt(0) ?? '读' }}</span>
              </div>
              <button class="avatar-edit-btn" title="更换头像">＋</button>
            </div>
            <h2 class="user-name">{{ userInfo?.nickname }}</h2>
            <p class="user-username">@{{ userInfo?.username }}</p>
            <p v-if="userInfo?.bio" class="user-bio">{{ userInfo.bio }}</p>
            <p v-else class="user-bio text-muted">暂无简介</p>

            <div class="user-divider"></div>

            <!-- Navigation -->
            <nav class="profile-nav">
              <button
                v-for="tab in tabs"
                :key="tab.key"
                class="profile-nav-item"
                :class="{ active: activeTab === tab.key }"
                @click="activeTab = tab.key"
              >
                <span class="nav-icon">{{ tab.icon }}</span>
                {{ tab.label }}
              </button>
            </nav>
          </div>
        </aside>

        <!-- Right: content area -->
        <section class="profile-main">
          <!-- Edit Profile -->
          <div v-if="activeTab === 'edit'" class="content-card">
            <h3 class="content-title">编辑资料</h3>
            <form @submit.prevent="saveProfile">
              <div class="form-group">
                <label class="form-label">昵称</label>
                <input v-model="editForm.nickname" type="text" class="form-input" />
              </div>
              <div class="form-group">
                <label class="form-label">个人简介</label>
                <textarea v-model="editForm.bio" class="form-input" rows="3" placeholder="写点什么介绍自己…"></textarea>
              </div>
              <div v-if="editError" class="auth-error">{{ editError }}</div>
              <button type="submit" class="btn btn-primary" :disabled="editLoading">
                {{ editLoading ? '保存中…' : '保存修改' }}
              </button>
            </form>

            <div class="divider"></div>

            <h3 class="content-title">修改密码</h3>
            <form @submit.prevent="savePassword">
              <div class="form-group">
                <label class="form-label">当前密码</label>
                <input v-model="pwdForm.oldPassword" type="password" class="form-input" />
              </div>
              <div class="form-group">
                <label class="form-label">新密码</label>
                <input v-model="pwdForm.newPassword" type="password" class="form-input" />
              </div>
              <div class="form-group">
                <label class="form-label">确认新密码</label>
                <input v-model="pwdConfirm" type="password" class="form-input" />
              </div>
              <div v-if="pwdError" class="auth-error">{{ pwdError }}</div>
              <button type="submit" class="btn btn-ghost" :disabled="pwdLoading">
                {{ pwdLoading ? '修改中…' : '修改密码' }}
              </button>
            </form>
          </div>

          <!-- Checkin Calendar -->
          <div v-if="activeTab === 'checkin'" class="content-card">
            <h3 class="content-title">打卡记录</h3>
            <CheckinCalendar />
          </div>

          <!-- Favorites (placeholder) -->
          <div v-if="activeTab === 'favorites'" class="content-card">
            <h3 class="content-title">我的收藏</h3>
            <div class="empty-state">
              <div class="empty-icon">🔖</div>
              <p>收藏你喜爱的小说，在这里找到它们</p>
              <router-link to="/" class="btn btn-gold mt-4">去发现好书</router-link>
            </div>
          </div>
        </section>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { apiUpdatePassword } from '@/api/user'
import { useToast } from '@/composables/useToast'
import CheckinCalendar from '@/components/CheckinCalendar.vue'

const userStore = useUserStore()
const { show } = useToast()

const userInfo = computed(() => userStore.userInfo)
const activeTab = ref('checkin')

const tabs = [
  { key: 'checkin', label: '打卡记录', icon: '📅' },
  { key: 'favorites', label: '我的收藏', icon: '🔖' },
  { key: 'edit', label: '编辑资料', icon: '✏️' }
]

// Edit profile
const editForm = reactive({ nickname: '', bio: '' })
const editLoading = ref(false)
const editError = ref('')

async function saveProfile() {
  editError.value = ''
  editLoading.value = true
  try {
    await userStore.updateProfile(editForm)
    show('资料已更新')
  } catch (e) {
    editError.value = e.message
  } finally {
    editLoading.value = false
  }
}

// Change password
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const pwdConfirm = ref('')
const pwdLoading = ref(false)
const pwdError = ref('')

async function savePassword() {
  pwdError.value = ''
  if (pwdForm.newPassword !== pwdConfirm.value) {
    pwdError.value = '两次密码不一致'
    return
  }
  pwdLoading.value = true
  try {
    await apiUpdatePassword(pwdForm)
    show('密码已修改，请重新登录')
    await userStore.logout()
  } catch (e) {
    pwdError.value = e.message
  } finally {
    pwdLoading.value = false
  }
}

onMounted(async () => {
  await userStore.fetchProfile()
  editForm.nickname = userInfo.value?.nickname ?? ''
  editForm.bio = userInfo.value?.bio ?? ''
})
</script>

<style scoped>
.profile-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: var(--space-8);
  align-items: start;
}

/* Sidebar */
.user-card {
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-xl);
  padding: var(--space-8) var(--space-6);
  box-shadow: var(--shadow-sm);
  text-align: center;
  position: sticky;
  top: 80px;
}

.user-avatar-wrap {
  position: relative;
  display: inline-block;
  margin-bottom: var(--space-4);
}

.user-avatar {
  width: 88px;
  height: 88px;
  border-radius: var(--radius-full);
  overflow: hidden;
  background: var(--gold-3);
  border: 3px solid var(--paper-0);
  box-shadow: 0 0 0 2px var(--gold-2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar img { width: 100%; height: 100%; object-fit: cover; }

.avatar-text {
  font-family: var(--font-serif);
  font-size: 2.5rem;
  color: var(--gold-0);
}

.avatar-edit-btn {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-full);
  background: var(--ink-0);
  color: var(--paper-0);
  border: none;
  font-size: 0.875rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background var(--transition-fast);
}

.avatar-edit-btn:hover { background: var(--gold-0); }

.user-name {
  font-family: var(--font-serif);
  font-size: 1.25rem;
  color: var(--ink-0);
  margin-bottom: 4px;
}

.user-username {
  font-size: 0.875rem;
  color: var(--ink-4);
  margin-bottom: var(--space-3);
}

.user-bio {
  font-size: 0.875rem;
  color: var(--ink-3);
  line-height: 1.6;
}

.user-divider {
  height: 1px;
  background: var(--paper-2);
  margin: var(--space-5) 0;
}

.profile-nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.profile-nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  font-size: 0.9375rem;
  color: var(--ink-2);
  cursor: pointer;
  transition: all var(--transition-fast);
  text-align: left;
}

.profile-nav-item:hover {
  background: var(--paper-2);
  color: var(--ink-0);
}

.profile-nav-item.active {
  background: var(--paper-2);
  color: var(--ink-0);
  font-weight: 500;
}

.nav-icon { font-size: 1rem; }

/* Main content */
.content-card {
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-xl);
  padding: var(--space-8);
  box-shadow: var(--shadow-sm);
}

.content-title {
  font-family: var(--font-serif);
  font-size: 1.125rem;
  color: var(--ink-0);
  margin-bottom: var(--space-6);
}

.auth-error {
  background: var(--vermilion-light);
  color: var(--vermilion);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  font-size: 0.875rem;
  margin-bottom: var(--space-4);
}

/* Empty state */
.empty-state {
  text-align: center;
  padding: var(--space-12) 0;
  color: var(--ink-4);
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: var(--space-4);
}

@media (max-width: 768px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }
  .user-card {
    position: static;
  }
}
</style>
