<template>
  <div class="image-strip">
    <img
      v-for="(image, index) in images"
      :key="`${image}-${index}`"
      :src="image"
      :alt="`${alt} ${index + 1}`"
      @error="useFallbackImage($event, index)"
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
  '/assets/merchant-coffee.svg',
  '/assets/merchant-hotpot.svg',
  '/assets/merchant-bakery.svg',
  '/assets/merchant-sushi.svg',
];

const images = computed(() => {
  const parsed = props.src?.split(',').map((item) => item.trim()).filter(Boolean) || [];
  return parsed.length > 0 ? parsed.slice(0, 5) : fallbackImages;
});

function useFallbackImage(event: Event, index: number) {
  const image = event.target as HTMLImageElement;
  const fallbackImage = fallbackImages[index % fallbackImages.length];
  if (image.src.endsWith(fallbackImage)) {
    return;
  }
  image.src = fallbackImage;
}
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
