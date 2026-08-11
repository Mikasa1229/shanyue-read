import { ref } from 'vue'

const dialog = ref({
  open: false,
  title: '',
  message: '',
  confirmText: '确认',
  cancelText: '取消',
  tone: 'default'
})

let resolveCurrent = null

export function useConfirmDialog() {
  function confirm(options = {}) {
    // A second request replaces the first one safely instead of leaving its promise pending.
    if (resolveCurrent) resolveCurrent(false)

    dialog.value = {
      open: true,
      title: options.title || '请确认操作',
      message: options.message || '',
      confirmText: options.confirmText || '确认',
      cancelText: options.cancelText || '取消',
      tone: options.tone || 'default'
    }

    return new Promise((resolve) => {
      resolveCurrent = resolve
    })
  }

  function close(confirmed = false) {
    const resolve = resolveCurrent
    resolveCurrent = null
    dialog.value.open = false
    resolve?.(confirmed)
  }

  return { dialog, confirm, close }
}
