<template>
  <span class="status-badge" :class="className">{{ text }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { OrderDisplayStatus } from '@/types/order';

const props = defineProps<{
  status: OrderDisplayStatus;
}>();

const text = computed(() => {
  if (props.status === 'PAID') return '已支付';
  if (props.status === 'CLOSED') return '已关闭';
  if (props.status === 'FAILED') return '处理失败';
  return '待支付';
});

const className = computed(() => {
  if (props.status === 'PAID') return 'status-badge--paid';
  if (props.status === 'PENDING_PAYMENT') return 'status-badge--pending';
  return 'status-badge--danger';
});
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  padding: 5px 11px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.status-badge--paid {
  background: rgba(47, 143, 91, 0.12);
  color: var(--success);
}

.status-badge--pending {
  background: var(--sun-wash);
  color: var(--warning);
}

.status-badge--danger {
  background: var(--voucher-coral-soft);
  color: var(--danger);
}
</style>
