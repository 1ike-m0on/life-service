<template>
  <div class="empty-state">
    <div class="empty-state__mark">
      <van-icon :name="icon" />
    </div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  title?: string;
  description?: string;
  icon?: string;
}>(), {
  title: '暂无内容',
  description: '稍后再来看看',
  icon: 'notes-o',
});
</script>

<style scoped>
.empty-state {
  padding: 40px 24px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  text-align: center;
}

.empty-state__mark {
  width: 56px;
  height: 56px;
  display: inline-grid;
  place-items: center;
  border-radius: 18px;
  background: var(--brand-orange-soft);
  color: var(--brand-orange);
  font-size: 28px;
}

h3 {
  margin: 16px 0 6px;
  font-size: 18px;
}

p {
  margin: 0;
  color: var(--text-muted);
}
</style>
