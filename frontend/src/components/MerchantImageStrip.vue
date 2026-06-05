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
import { merchantFallbackImages, merchantGalleryImages } from '@/utils/merchantImages';

const props = defineProps<{
  src?: string | null;
  alt: string;
  seed?: number;
}>();

const images = computed(() => {
  return merchantGalleryImages(props.src, props.seed || 0, 5);
});

function useFallbackImage(event: Event, index: number) {
  const image = event.target as HTMLImageElement;
  const fallbackImage = merchantFallbackImages(props.seed || 0, 5)[index % 5];
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
