import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guest: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guest: true }
  },
  {
    path: '/novel/:id',
    name: 'NovelDetail',
    component: () => import('@/views/NovelDetailView.vue')
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { auth: true }
  },
  {
    path: '/book-sources',
    name: 'BookSource',
    component: () => import('@/views/BookSourceView.vue'),
    meta: { auth: true }
  },
  {
    path: '/square',
    name: 'Square',
    component: () => import('@/views/SquareView.vue')
  },
  {
    path: '/ranking',
    name: 'Ranking',
    component: () => import('@/views/RankingView.vue')
  },
  {
    path: '/bookshelf',
    name: 'Bookshelf',
    component: () => import('@/views/BookshelfView.vue'),
    meta: { auth: true }
  },
  {
    path: '/reader',
    name: 'Reader',
    component: () => import('@/views/ReaderView.vue')
  },
  {
    path: '/agent',
    name: 'Agent',
    component: () => import('@/views/AgentView.vue'),
    meta: { auth: true }
  },
  {
    path: '/agent/knowledge-graphs',
    name: 'KnowledgeGraphManage',
    component: () => import('@/views/KnowledgeGraphManageView.vue'),
    meta: { auth: true }
  },
  {
    path: '/admin/agent',
    name: 'AgentAdmin',
    component: () => import('@/views/AgentAdminView.vue'),
    meta: { auth: true }
  },
  {
    path: '/source-book-detail',
    name: 'SourceBookDetail',
    component: () => import('@/views/SourceBookDetailView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    // Reader progress is persisted by replacing only the query string. Do not
    // reset the window to the first rendered chapter for those same-page
    // updates; a real route change should still start at the top.
    if (to.path === from.path) return false
    return { top: 0, behavior: 'smooth' }
  }
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.auth && !userStore.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guest && userStore.isLoggedIn) {
    return { name: 'Home' }
  }
})

export default router
