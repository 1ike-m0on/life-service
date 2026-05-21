<template>
  <div class="image-strip">
    <img
      v-for="(image, index) in images"
      :key="`${image}-${index}`"
      :src="image"
      :alt="`${alt} ${index + 1}`"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  src?: string | null;
  alt: string;
}>();

const fallbackImages = [
  'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=920&q=80',
  'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=920&q=80',
  'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=920&q=80',
];

const images = computed(() => {
  const parsed = props.src?.split(',').map((item) => item.trim()).filter(Boolean) || [];
  return parsed.length > 0 ? parsed.slice(0, 5) : fallbackImages;
});
</script>

<style scoped>
.image-strip {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  grid-auto-rows: 164px;
  gap: 8px;
}

.image-strip img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: var(--radius-sm);
}

.image-strip img:first-child {
  grid-row: span 2;
}

@media (max-width: 720px) {
  .image-strip {
    display: flex;
    overflow-x: auto;
  }

  .image-strip img {
    flex: 0 0 78%;
    height: 190px;
  }
}
</style>
