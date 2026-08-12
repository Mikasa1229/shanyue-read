<template>
  <Teleport to="body">
    <Transition name="confirm-dialog">
      <div v-if="dialog.open" class="confirm-overlay" @click.self="close(false)">
        <section
          class="confirm-card"
          :class="`is-${dialog.tone}`"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="confirm-dialog-title"
          aria-describedby="confirm-dialog-message"
          tabindex="-1"
        >
          <div class="confirm-mark" aria-hidden="true">{{ dialog.tone === 'danger' ? '!' : '?' }}</div>
          <div class="confirm-content">
            <h2 id="confirm-dialog-title">{{ dialog.title }}</h2>
            <p id="confirm-dialog-message">{{ dialog.message }}</p>
          </div>
          <div class="confirm-actions">
            <button ref="cancelButton" type="button" class="confirm-cancel" @click="close(false)">{{ dialog.cancelText }}</button>
            <button type="button" class="confirm-accept" @click="close(true)">{{ dialog.confirmText }}</button>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useConfirmDialog } from '@/composables/useConfirmDialog'

const { dialog, close } = useConfirmDialog()
const cancelButton = ref(null)

function onKeydown(event) {
  if (event.key === 'Escape' && dialog.value.open) close(false)
}

watch(() => dialog.value.open, async (open) => {
  if (open) {
    window.addEventListener('keydown', onKeydown)
    await nextTick()
    cancelButton.value?.focus()
  } else {
    window.removeEventListener('keydown', onKeydown)
  }
})

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.confirm-dialog-enter-active, .confirm-dialog-leave-active { transition: opacity .18s ease; }
.confirm-dialog-enter-active .confirm-card, .confirm-dialog-leave-active .confirm-card { transition: transform .18s ease, opacity .18s ease; }
.confirm-dialog-enter-from, .confirm-dialog-leave-to { opacity: 0; }
.confirm-dialog-enter-from .confirm-card, .confirm-dialog-leave-to .confirm-card { opacity: 0; transform: translateY(10px) scale(.98); }
.confirm-overlay { position:fixed; z-index:2400; inset:0; display:grid; place-items:center; padding:20px; background:rgba(16,44,50,.38); backdrop-filter:blur(3px); }
.confirm-card { width:min(100%, 460px); display:grid; grid-template-columns:42px minmax(0, 1fr); gap:14px 16px; padding:24px; border:1px solid rgba(172,122,41,.38); border-radius:18px; background:linear-gradient(145deg, #fffefa, #f6eddb); color:#102c32; box-shadow:0 24px 70px rgba(16,44,50,.28); outline:none; }
.confirm-mark { display:grid; place-items:center; width:42px; height:42px; border-radius:50%; background:#e6c27a; color:#523714; font-family:Georgia, serif; font-size:1.35rem; font-weight:800; }
.is-danger .confirm-mark { background:#b43f2d; color:#fffefa; }
.confirm-content h2 { margin:1px 0 8px; color:#102c32; font-family:var(--font-serif, Georgia, serif); font-size:1.2rem; line-height:1.35; }
.confirm-content p { margin:0; color:#54737a; font-size:.92rem; line-height:1.7; white-space:pre-line; }
.confirm-actions { grid-column:2; display:flex; justify-content:flex-end; gap:10px; margin-top:2px; }
.confirm-actions button { min-width:88px; min-height:38px; border-radius:10px; padding:8px 14px; font:inherit; font-size:.9rem; font-weight:700; cursor:pointer; transition:transform .15s ease, box-shadow .15s ease, background .15s ease; }
.confirm-actions button:hover { transform:translateY(-1px); }
.confirm-actions button:focus-visible { outline:3px solid rgba(172,122,41,.38); outline-offset:2px; }
.confirm-cancel { border:1px solid rgba(16,44,50,.18); background:rgba(255,254,250,.68); color:#31535a; }
.confirm-accept { border:1px solid #ac7a29; background:#ac7a29; color:#fffefa; box-shadow:0 5px 12px rgba(117,76,20,.2); }
.is-danger .confirm-accept { border-color:#a93527; background:#b43f2d; box-shadow:0 5px 12px rgba(128,41,28,.2); }
@media (max-width:480px) { .confirm-overlay { padding:14px; align-items:end; }.confirm-card { padding:20px; }.confirm-actions { grid-column:1 / -1; }.confirm-actions button { flex:1; } }
</style>
