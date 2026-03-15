import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiLogin, apiRegister, apiLogout, apiGetProfile, apiUpdateProfile } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed(() => !!token.value)

  function setAuth(t, info) {
    token.value = t
    userInfo.value = info
    localStorage.setItem('token', t)
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function clearAuth() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  async function login(dto) {
    const res = await apiLogin(dto)
    setAuth(res.token, res.userInfo)
    return res
  }

  async function register(dto) {
    await apiRegister(dto)
  }

  async function logout() {
    try { await apiLogout() } catch (_) {}
    clearAuth()
  }

  async function fetchProfile() {
    const info = await apiGetProfile()
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
    return info
  }

  async function updateProfile(dto) {
    await apiUpdateProfile(dto)
    await fetchProfile()
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    login,
    register,
    logout,
    fetchProfile,
    updateProfile,
    clearAuth
  }
})
