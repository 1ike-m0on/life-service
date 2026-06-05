<template>
  <article class="merchant-card" @click="$emit('open', merchant.id)">
    <div class="merchant-card__media">
      <img class="merchant-card__image" :src="coverImage" :alt="merchant.name" @error="useFallbackImage" />
      <span class="merchant-card__category">{{ categoryLabel }}</span>
    </div>

    <div class="merchant-card__body">
      <div class="merchant-card__head">
        <div>
          <h3>{{ merchant.name }}</h3>
          <div class="merchant-card__rating">
            <van-rate
              :model-value="merchant.score / 10"
              readonly
              allow-half
              size="15"
              color="#ff6633"
              void-color="#e4ded9"
            />
            <strong>{{ formatScore(merchant.score) }}</strong>
            <span>{{ merchant.commentCount }} 条评价</span>
          </div>
        </div>
        <span class="voucher-tag">{{ highlightLabel }}</span>
      </div>

      <div class="merchant-card__meta">
        <span>{{ merchant.area || '附近' }}</span>
        <span>{{ formatCent(merchant.avgPriceCent) }}/人</span>
        <span>{{ distanceLabel }}</span>
        <span>{{ merchant.soldCount }} 人想去</span>
      </div>

      <p class="merchant-card__tagline">{{ tagline }}</p>

      <p class="merchant-card__address">
        <van-icon name="location-o" />
        {{ merchant.address || '地址待完善' }}
      </p>

      <div class="merchant-card__foot">
        <span>营业时间 {{ merchant.openHours || '以门店为准' }}</span>
        <button type="button" @click.stop="$emit('open', merchant.id)">看详情</button>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { formatCent, formatScore } from '@/utils/money';
import { merchantCoverImage, merchantFallbackImages } from '@/utils/merchantImages';
import type { Merchant } from '@/types/merchant';

const props = defineProps<{
  merchant: Merchant;
}>();

defineEmits<{
  open: [merchantId: number];
}>();

const categoryLabels: Record<number, string> = {
  1: '咖啡轻食',
  2: '火锅聚餐',
  3: '烘焙甜品',
  4: '日料简餐',
  5: '运动健身',
  6: '影院娱乐',
};

const categoryTaglines: Record<number, string> = {
  1: '适合下午小坐和工作日咖啡补给',
  2: '适合朋友聚餐，热度稳定',
  3: '现烤面包和甜品套餐更划算',
  4: '午市定食和晚餐套餐都可选',
  5: '体验课适合先试再办卡',
  6: '热门场次适合提前看套餐和座位',
};

const fallbackImage = computed(() => merchantFallbackImages(props.merchant.id, 1)[0]);
const coverImage = computed(() => merchantCoverImage(props.merchant.images, props.merchant.id));
const categoryLabel = computed(() => categoryLabels[props.merchant.categoryId] || '精选商户');
const tagline = computed(() => categoryTaglines[props.merchant.categoryId] || '本地精选，近期热度较高');
const distanceLabel = computed(() => `${((props.merchant.id % 7) + 4) / 10}km`);
const highlightLabel = computed(() => {
  if (props.merchant.score >= 48) return '高分店';
  if (props.merchant.commentCount >= 80) return '评价多';
  return '口碑店';
});

function useFallbackImage(event: Event) {
  const image = event.target as HTMLImageElement;
  if (image.src.endsWith(fallbackImage.value)) {
    return;
  }
  image.src = fallbackImage.value;
}
</script>

<style scoped>
.merchant-card {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  gap: 18px;
  padding: 14px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-lg);
  background: var(--neutral-surface);
  transition: transform 150ms ease, box-shadow 150ms ease, border-color 150ms ease;
}

.merchant-card:hover {
  transform: translateY(-2px);
  border-color: oklch(0.84 0.09 58);
  box-shadow: var(--shadow-card);
}

.merchant-card__media {
  position: relative;
  min-width: 0;
}

.merchant-card__image {
  width: 100%;
  height: 156px;
  object-fit: cover;
  border-radius: 10px;
  background: var(--neutral-surface-soft);
}

.merchant-card__category {
  position: absolute;
  left: 10px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 9px;
  border-radius: var(--radius-pill);
  background: oklch(0.24 0.013 70 / 0.76);
  color: var(--rice-paper);
  font-size: 12px;
  font-weight: 800;
}

.merchant-card__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.merchant-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

h3 {
  margin: 0;
  font-size: 20px;
  line-height: 1.25;
}

.merchant-card__rating {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 9px;
  color: var(--text-muted);
  font-size: 13px;
}

.merchant-card__rating strong {
  color: var(--brand-orange);
}

.voucher-tag {
  flex: 0 0 auto;
  padding: 6px 10px;
  border-radius: var(--radius-pill);
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
  font-size: 12px;
  font-weight: 800;
}

.merchant-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
  color: var(--text-muted);
  font-size: 13px;
}

.merchant-card__meta span {
  padding: 4px 9px;
  border-radius: var(--radius-pill);
  background: var(--neutral-surface-soft);
}

.merchant-card__address {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 14px 0 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.merchant-card__tagline {
  margin: 12px 0 0;
  color: var(--text-strong);
  font-size: 14px;
  line-height: 1.55;
}

.merchant-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: auto;
  padding-top: 16px;
  color: var(--text-muted);
  font-size: 13px;
}

.merchant-card__foot button {
  min-height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-size: 13px;
  font-weight: 800;
}

@media (max-width: 720px) {
  .merchant-card {
    grid-template-columns: 1fr;
  }

  .merchant-card__image {
    height: 190px;
  }
}
</style>
