<template>
  <article class="order-mini">
    <div>
      <span class="state-chip state-chip--warn">最近订单</span>
      <h3>{{ order.voucherTitle }}</h3>
      <p>{{ order.merchantName }}</p>
      <small>订单号 {{ order.orderNo }}</small>
    </div>
    <OrderStatusBadge :status="order.status" />
  </article>
</template>

<script setup lang="ts">
import OrderStatusBadge from './OrderStatusBadge.vue';
import { RecentOrder } from '@/types/order';

defineProps<{
  order: RecentOrder;
}>();
</script>

<style scoped>
.order-mini {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
}

h3 {
  margin: 12px 0 6px;
  font-size: 16px;
}

p,
small {
  display: block;
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}
</style>
