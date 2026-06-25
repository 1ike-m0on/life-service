<template>
  <main class="page" data-testid="orders-page">
    <section class="orders-head">
      <div>
        <span class="state-chip">我的订单</span>
        <h1>团购券、秒杀券和到店消费状态</h1>
        <p>下单后可以继续支付、回到商户详情，或查看当前订单状态。</p>
      </div>
      <RouterLink class="primary-link" to="/merchants">继续浏览商户</RouterLink>
    </section>

    <section v-if="!authStore.isLoggedIn" class="section">
      <EmptyState title="登录后查看订单" description="邮箱登录后，可以同步查看后端保存的订单状态。">
        <RouterLink class="empty-link" to="/login?redirect=/orders">去登录</RouterLink>
      </EmptyState>
    </section>

    <section v-else class="section page-grid">
      <div>
        <div class="order-tabs">
          <button
            v-for="tab in statusTabs"
            :key="tab.label"
            type="button"
            :class="{ active: selectedStatus === tab.value }"
            @click="selectStatus(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>

        <van-skeleton v-if="orderStore.loading" title :row="8" />
        <EmptyState
          v-else-if="orderStore.recentOrders.length === 0"
          title="暂无订单"
          description="购买团购券或秒杀券后，订单会出现在这里。"
        >
          <RouterLink class="empty-link" to="/merchants">去浏览商户</RouterLink>
        </EmptyState>

        <div v-else class="order-list">
          <article
            v-for="order in orderStore.recentOrders"
            :key="order.orderNo"
            class="order-card card"
            :data-testid="`order-card-${order.orderNo}`"
            :data-order-status="order.status"
          >
            <div class="order-card__top">
              <div>
                <h2>{{ order.voucherTitle }}</h2>
                <p>{{ order.merchantName }}</p>
              </div>
              <OrderStatusBadge :status="order.status" />
            </div>

            <div class="order-card__details">
              <div>
                <span>订单号</span>
                <strong>{{ order.orderNo }}</strong>
              </div>
              <div>
                <span>支付金额</span>
                <strong>{{ formatCent(order.payAmountCent) }}</strong>
              </div>
              <div>
                <span>更新时间</span>
                <strong>{{ formatDateTime(order.updatedAt) }}</strong>
              </div>
            </div>

            <p v-if="order.message" class="message">{{ order.message }}</p>

            <div class="order-card__actions">
              <van-button
                v-if="order.status === 'PENDING_PAYMENT'"
                type="primary"
                size="small"
                :loading="payingOrderNo === order.orderNo"
                @click="pay(order.orderNo)"
              >
                支付
              </van-button>
              <van-button plain size="small" :to="`/merchants/${order.merchantId}`">查看商户</van-button>
              <van-button plain size="small" @click="showTodo">订单详情</van-button>
              <van-button plain size="small" @click="showTodo">退款</van-button>
            </div>
          </article>
        </div>
      </div>

      <aside class="sidebar">
        <section class="side-card">
          <h3>订单说明</h3>
          <p>待支付订单可以继续支付，已关闭或处理失败会显示原因。支付、关单和库存释放的并发处理由后端保证。</p>
        </section>
        <section class="side-card side-card--soft">
          <h3>后续服务</h3>
          <p>订单详情、退款、评价发布会在后续版本继续完善，目前先保留入口并展示友好的未完成提示。</p>
        </section>
        <button
          v-if="orderStore.recentOrders.length > 0"
          type="button"
          class="clear-button"
          @click="orderStore.clearOrders()"
        >
          清空本地缓存
        </button>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { showFailToast, showSuccessToast, showToast } from 'vant';
import EmptyState from '@/components/EmptyState.vue';
import OrderStatusBadge from '@/components/OrderStatusBadge.vue';
import { useOrderStore } from '@/stores/order';
import { friendlyMessage, todoMessage } from '@/utils/format';
import { formatCent } from '@/utils/money';
import { isApiBusinessError } from '@/types/api';
import { formatDateTime } from '@/utils/time';
import { useAuthStore } from '@/stores/auth';

const orderStore = useOrderStore();
const authStore = useAuthStore();
const payingOrderNo = ref('');
const selectedStatus = ref<number | null>(null);

const statusTabs = [
  { label: '全部', value: null },
  { label: '待支付', value: 1 },
  { label: '已支付', value: 2 },
  { label: '已关闭', value: 3 },
];

onMounted(() => {
  if (authStore.isLoggedIn) {
    refreshOrders();
  }
});

async function refreshOrders() {
  try {
    await orderStore.loadMyOrders(selectedStatus.value);
  } catch (error) {
    const message = isApiBusinessError(error) ? friendlyMessage(error.code, error.message) : '订单加载失败';
    showFailToast(message);
  }
}

function selectStatus(status: number | null) {
  selectedStatus.value = status;
  refreshOrders();
}

async function pay(orderNo: string) {
  payingOrderNo.value = orderNo;
  try {
    const result = await orderStore.pay(orderNo);
    showSuccessToast(result.data.idempotent ? '订单已支付' : '支付成功');
    refreshOrders();
  } catch (error) {
    const message = isApiBusinessError(error) ? friendlyMessage(error.code, error.message) : '支付失败';
    const failedStatus = isApiBusinessError(error) && error.code === 'ORDER_CLOSED' ? 'CLOSED' : 'FAILED';
    orderStore.markFailed(orderNo, message, failedStatus);
    showFailToast(message);
  } finally {
    payingOrderNo.value = '';
  }
}

function showTodo() {
  showToast(todoMessage());
}
</script>

<style scoped>
.orders-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  box-shadow: var(--shadow-panel);
}

h1 {
  margin: 14px 0 8px;
  font-size: 34px;
}

p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.65;
}

.primary-link,
.empty-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 16px;
  border-radius: var(--radius-pill);
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-weight: 800;
}

.empty-link {
  margin-top: 18px;
}

.order-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.order-tabs button {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-pill);
  background: var(--neutral-surface);
  color: var(--text-muted);
  font-weight: 800;
}

.order-tabs button.active {
  border-color: oklch(0.84 0.09 58);
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
}

.order-list {
  display: grid;
  gap: 14px;
}

.order-card {
  padding: 18px;
}

.order-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

h2 {
  margin: 0;
  font-size: 19px;
}

.order-card__details {
  display: grid;
  grid-template-columns: 1.3fr 0.7fr 0.8fr;
  gap: 12px;
  margin-top: 18px;
  padding: 14px;
  border-radius: var(--radius-sm);
  background: var(--neutral-surface-soft);
}

.order-card__details span,
.order-card__details strong {
  display: block;
}

.order-card__details span {
  color: var(--text-muted);
  font-size: 12px;
}

.order-card__details strong {
  margin-top: 5px;
  color: var(--text-strong);
  word-break: break-all;
}

.message {
  margin-top: 12px;
  color: var(--brand-orange-deep);
}

.order-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.side-card {
  padding: 18px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  box-shadow: var(--shadow-panel);
}

.side-card--soft {
  background: var(--brand-orange-soft);
}

.side-card h3 {
  margin: 0 0 8px;
}

.clear-button {
  min-height: 40px;
  border: 1px solid rgba(240, 51, 51, 0.2);
  border-radius: var(--radius-pill);
  background: var(--voucher-wash);
  color: var(--danger);
  font-weight: 800;
}

@media (max-width: 720px) {
  .orders-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .order-card__details {
    grid-template-columns: 1fr;
  }
}
</style>
