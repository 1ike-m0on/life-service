<template>
  <div class="category-grid">
    <button
      v-for="category in displayCategories"
      :key="category.id"
      type="button"
      class="category-item"
      @click="$emit('select', category.id)"
    >
      <span class="category-item__icon">
        <van-icon :name="iconFor(category.name)" />
      </span>
      <span>{{ category.name }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { MerchantCategory } from '@/types/merchant';

const props = defineProps<{
  categories: MerchantCategory[];
}>();

defineEmits<{
  select: [categoryId: number];
}>();

const fallbackCategories: MerchantCategory[] = [
  { id: 1, name: '美食', sortOrder: 1, status: 1 },
  { id: 2, name: '咖啡', sortOrder: 2, status: 1 },
  { id: 3, name: '火锅', sortOrder: 3, status: 1 },
  { id: 4, name: '烘焙', sortOrder: 4, status: 1 },
  { id: 5, name: '日料', sortOrder: 5, status: 1 },
  { id: 6, name: '甜品', sortOrder: 6, status: 1 },
  { id: 7, name: '健身', sortOrder: 7, status: 1 },
  { id: 8, name: '更多', sortOrder: 8, status: 1 },
];

const displayCategories = computed(() => (
  props.categories.length > 0 ? props.categories.slice(0, 10) : fallbackCategories
));

function iconFor(name: string): string {
  if (name.includes('咖')) return 'hot-o';
  if (name.includes('火')) return 'fire-o';
  if (name.includes('甜') || name.includes('烘')) return 'gift-o';
  if (name.includes('健')) return 'like-o';
  if (name.includes('日')) return 'flower-o';
  if (name.includes('美') || name.includes('食')) return 'shop-o';
  return 'apps-o';
}
</script>

<style scoped>
.category-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 12px;
}

.category-item {
  min-height: 106px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  color: var(--text-strong);
  box-shadow: var(--shadow-panel);
  font-size: 14px;
  font-weight: 800;
  transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease;
}

.category-item:hover {
  transform: translateY(-2px);
  border-color: rgba(255, 102, 51, 0.3);
  box-shadow: var(--shadow-card);
}

.category-item__icon {
  width: 44px;
  height: 44px;
  display: inline-grid;
  place-items: center;
  border-radius: 16px;
  background: var(--brand-orange-soft);
  color: var(--brand-orange);
  font-size: 24px;
}

@media (max-width: 1024px) {
  .category-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .category-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
