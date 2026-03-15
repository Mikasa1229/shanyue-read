import { ref } from 'vue'

const toasts = ref([])
let id = 0

export function useToast() {
  function show(message, duration = 2500) {
    const toast = { id: ++id, message }
    toasts.value.push(toast)
    setTimeout(() => {
      toasts.value = toasts.value.filter((t) => t.id !== toast.id)
    }, duration)
  }

  return { toasts, show }
}
