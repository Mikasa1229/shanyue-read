<template>
  <teleport to="body">
    <div class="toast-container">
      <transition-group name="toast">
        <div v-for="t in toasts" :key="t.id" class="toast" :class="`toast-${t.type}`" role="status" aria-live="polite">
          <span class="toast-icon" aria-hidden="true">{{ t.type === 'success' ? '✓' : t.type === 'error' ? '!' : 'i' }}</span>
          {{ t.message }}
        </div>
      </transition-group>
    </div>
  </teleport>
</template>

<script setup>
import { useToast } from '@/composables/useToast'
const { toasts } = useToast()
</script>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.95);
}
.toast { display:flex; align-items:center; gap:9px; min-width:240px; max-width:min(420px,calc(100vw - 32px)); padding:12px 15px; border:1px solid rgba(16,44,50,.14); border-radius:12px; color:#102c32; background:#fffefa; box-shadow:0 12px 30px rgba(16,44,50,.18); font-size:.86rem; }
.toast-icon { display:grid; place-items:center; width:20px; height:20px; flex:0 0 20px; border-radius:50%; color:#fffefa; font-weight:800; }
.toast-success { border-color:rgba(62,128,74,.35); }.toast-success .toast-icon { background:#3e804a; }
.toast-error { border-color:rgba(180,63,45,.35); }.toast-error .toast-icon { background:#b43f2d; }.toast-info .toast-icon { background:#54737a; }
</style>
