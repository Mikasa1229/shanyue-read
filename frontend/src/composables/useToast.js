import { ref } from 'vue'

const toasts = ref([])
let id = 0

export function useToast() {
  function show(message, type = 'info', duration = 3000) {
    const toast = { id: ++id, message: String(message || ''), type }
    toasts.value.push(toast)
    setTimeout(() => {
      toasts.value = toasts.value.filter((t) => t.id !== toast.id)
    }, duration)
  }

  return {
    toasts,
    show: (message, duration) => show(message, 'info', duration),
    success: (message, duration) => show(message, 'success', duration),
    error: (message, duration) => show(message, 'error', duration)
  }
}
