<template>
  <div id="app-root">
    <AppNav />
    <router-view v-slot="{ Component, route }">
      <transition name="page">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
    <ToastManager />
    <AgentFloating />
  </div>
</template>

<script setup>
import AppNav from '@/components/AppNav.vue'
import ToastManager from '@/components/ToastManager.vue'
import AgentFloating from '@/components/AgentFloating.vue'
</script>

<style scoped>
#app-root {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
</style>
