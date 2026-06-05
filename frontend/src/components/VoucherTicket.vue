<template>
  <article class="voucher-ticket" :class="{ 'voucher-ticket--flash': isFlashSale }">
    <div class="voucher-ticket__stub" aria-hidden="true">
      <span />
      <span />
      <span />
    </div>

    <div class="voucher-ticket__main">
      <div class="voucher-ticket__head">
        <span class="voucher-ticket__type">{{ isFlashSale ? '限时秒杀' : '到店代金券' }}</span>
        <span class="voucher-ticket__discount">{{ discountLabel(voucher.payAmountCent, voucher.discountAmountCent) }}</span>
      </div>
      <h3>{{ voucher.title }}</h3>
      <p>{{ voucher.subtitle || voucher.rules || '到店消费可用，以商家规则为准' }}</p>
      <div class="voucher-ticket__price">
        <strong>{{ formatCent(voucher.payAmountCent) }}</strong>
        <span>抵 {{ formatCent(voucher.discountAmountCent) }}</span>
      </div>
    </div>

    <div class="voucher-ticket__action">
      <button type="button" :disabled="loading" @click="$emit('claim', voucher)">
        {{ loading ? '处理中' : isFlashSale ? '抢券' : '查看' }}
      </button>
      <small>{{ isFlashSale ? '成功后生成订单' : '到店可用' }}</small>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Voucher } from '@/types/voucher';
import { discountLabel, formatCent } from '@/utils/money';

const props = defineProps<{
  voucher: Voucher;
  loading?: boolean;
}>();

defineEmits<{
  claim: [voucher: Voucher];
}>();

const isFlashSale = computed(() => props.voucher.type === 2);
</script>

<style scoped>
.voucher-ticket {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) 98px;
  gap: 14px;
  align-items: stretch;
  padding: 12px;
  border: 1px solid oklch(0.84 0.09 58 / 0.36);
  border-radius: var(--radius-md);
  background: var(--voucher-wash);
}

.voucher-ticket__stub {
  display: flex;
  flex-direction: column;
  justify-content: space-around;
}

.voucher-ticket__stub span {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--neutral-surface);
}

.voucher-ticket__main {
  min-width: 0;
}

.voucher-ticket__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.voucher-ticket__type,
.voucher-ticket__discount {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 4px 8px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 800;
}

.voucher-ticket__type {
  background: var(--brand-orange);
  color: var(--neutral-surface);
}

.voucher-ticket__discount {
  background: rgba(240, 51, 51, 0.08);
  color: var(--danger);
}

h3 {
  margin: 10px 0 6px;
  font-size: 17px;
  line-height: 1.3;
}

p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.45;
}

.voucher-ticket__price {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 10px;
}

.voucher-ticket__price strong {
  color: var(--brand-orange);
  font-size: 24px;
}

.voucher-ticket__price span {
  color: var(--text-muted);
  font-size: 12px;
}

.voucher-ticket__action {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 7px;
}

.voucher-ticket__action button {
  width: 78px;
  min-height: 34px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-size: 14px;
  font-weight: 800;
}

.voucher-ticket__action button:disabled {
  opacity: 0.68;
  cursor: wait;
}

.voucher-ticket__action small {
  color: var(--text-muted);
  font-size: 12px;
}

@media (max-width: 560px) {
  .voucher-ticket {
    grid-template-columns: 14px minmax(0, 1fr);
  }

  .voucher-ticket__action {
    grid-column: 2;
    align-items: flex-start;
  }
}
</style>
