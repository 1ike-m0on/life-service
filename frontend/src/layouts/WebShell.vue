<template>
  <div class="web-shell">
    <AppTopNav />
    <main class="web-shell__body">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
import AppTopNav from '@/components/AppTopNav.vue';
</script>

<style scoped>
.web-shell {
  min-height: 100vh;
  background: var(--neutral-bg);
}

.web-shell__body {
  min-height: calc(100vh - 64px);
}
</style>
