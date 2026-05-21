<template>
  <WebShell>
    <RouterView />
  </WebShell>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router';
import WebShell from './layouts/WebShell.vue';
</script>
