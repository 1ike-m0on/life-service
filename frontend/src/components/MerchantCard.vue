<template>
  <article class="merchant-card" @click="$emit('open', merchant.id)">
    <img class="merchant-card__image" :src="coverImage" :alt="merchant.name" @error="useFallbackImage" />

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
        <span class="voucher-tag">可抢券</span>
      </div>

      <div class="merchant-card__meta">
        <span>{{ merchant.area || '附近' }}</span>
        <span>{{ formatCent(merchant.avgPriceCent) }}/人</span>
        <span>{{ merchant.soldCount }} 人气</span>
      </div>

      <p class="merchant-card__address">
        <van-icon name="location-o" />
        {{ merchant.address || '地址待完善' }}
      </p>

      <div class="merchant-card__foot">
        <span>营业时间 {{ merchant.openHours || '以门店为准' }}</span>
        <button type="button" @click.stop="$emit('open', merchant.id)">查看详情</button>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Merchant } from '@/types/merchant';
import { formatCent, formatScore } from '@/utils/money';

const props = defineProps<{
  merchant: Merchant;
}>();

defineEmits<{
  open: [merchantId: number];
}>();

const fallbackImages = [
  '/assets/merchant-coffee.svg',
  '/assets/merchant-hotpot.svg',
  '/assets/merchant-bakery.svg',
  '/assets/merchant-sushi.svg',
];

const fallbackImage = computed(() => fallbackImages[props.merchant.id % fallbackImages.length]);

const coverImage = computed(() => {
  const first = props.merchant.images?.split(',').map((item) => item.trim()).filter(Boolean)[0];
  return first || fallbackImage.value;
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
  grid-template-columns: 212px minmax(0, 1fr);
  gap: 18px;
  padding: 14px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  box-shadow: var(--shadow-panel);
  transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease;
}

.merchant-card:hover {
  transform: translateY(-2px);
  border-color: rgba(255, 102, 51, 0.3);
  box-shadow: var(--shadow-card);
}

.merchant-card__image {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  background: var(--neutral-surface-soft);
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
  background: var(--voucher-wash);
  color: var(--danger);
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
