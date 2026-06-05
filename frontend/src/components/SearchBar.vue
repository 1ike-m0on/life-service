<template>
  <form class="search-bar" @submit.prevent="submit">
    <button type="button" class="city-button" @click="todo">
      <van-icon name="location-o" />
      杭州
      <van-icon name="arrow-down" />
    </button>
    <label class="search-field">
      <van-icon name="search" />
      <input
        :value="modelValue"
        type="search"
        placeholder="搜索店名、菜品、目的地"
        @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      />
    </label>
    <button type="submit" class="search-button">搜索</button>
  </form>
</template>

<script setup lang="ts">
import { showToast } from 'vant';
import { todoMessage } from '@/utils/format';

const props = defineProps<{
  modelValue: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string];
  search: [value: string];
}>();

function submit() {
  emit('search', props.modelValue.trim());
}

function todo() {
  showToast(todoMessage());
}
</script>

<style scoped>
.search-bar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border: 1px solid rgba(255, 102, 51, 0.2);
  border-radius: var(--radius-lg);
  background: var(--neutral-surface);
  box-shadow: var(--shadow-card);
}

.city-button,
.search-button {
  min-height: 44px;
  border: 0;
  border-radius: var(--radius-pill);
  font-weight: 800;
}

.city-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
}

.search-field {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 0 16px;
  border-radius: var(--radius-pill);
  background: var(--neutral-surface-soft);
  color: var(--text-muted);
}

.search-field input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-strong);
  font-size: 15px;
}

.search-button {
  padding: 0 24px;
  background: var(--brand-orange);
  color: var(--neutral-surface);
}

@media (max-width: 720px) {
  .search-bar {
    grid-template-columns: 1fr;
  }

  .city-button,
  .search-button {
    width: 100%;
  }
}
</style>
