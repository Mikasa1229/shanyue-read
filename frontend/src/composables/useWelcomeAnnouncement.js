import { ref } from 'vue'

const STORAGE_KEY = 'reader:welcome-announcement'
const open = ref(false)

export function useWelcomeAnnouncement() {
  function show() {
    localStorage.setItem(STORAGE_KEY, 'pending')
    open.value = true
  }

  function close() {
    localStorage.removeItem(STORAGE_KEY)
    open.value = false
  }

  return { open, show, close }
}
