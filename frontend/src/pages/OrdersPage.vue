<template>
  <main class="page">
    <section class="orders-head">
      <div>
        <span class="state-chip">最近订单</span>
        <h1>抢券后的订单反馈</h1>
        <p>当前先记录本地最近订单，用来串起抢券和模拟支付体验。</p>
      </div>
      <RouterLink class="primary-link" to="/merchants">继续浏览商户</RouterLink>
    </section>

    <section class="section page-grid">
      <div>
        <EmptyState
          v-if="orderStore.recentOrders.length === 0"
          title="暂无最近订单"
          description="进入商户详情抢券后，订单会出现在这里。"
        >
          <RouterLink class="empty-link" to="/merchants">去浏览商户</RouterLink>
        </EmptyState>

        <div v-else class="order-list">
          <article v-for="order in orderStore.recentOrders" :key="order.orderNo" class="order-card card">
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
                模拟支付
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
          <p>后端还没有完整订单查询接口，所以这里展示的是前端本地最近订单。服务端最终状态以后续订单查询接口为准。</p>
        </section>
        <section class="side-card side-card--soft">
          <h3>未完成能力</h3>
          <p>订单详情、退款、评价等能力暂不实现。点击后会统一提示功能未完成。</p>
        </section>
        <button
          v-if="orderStore.recentOrders.length > 0"
          type="button"
          class="clear-button"
          @click="orderStore.clearOrders()"
        >
          清空本地订单
        </button>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { RouterLink } from 'vue-router';
import { showFailToast, showSuccessToast, showToast } from 'vant';
import EmptyState from '@/components/EmptyState.vue';
import OrderStatusBadge from '@/components/OrderStatusBadge.vue';
import { useOrderStore } from '@/stores/order';
import { friendlyMessage, todoMessage } from '@/utils/format';
import { formatCent } from '@/utils/money';
import { isApiBusinessError } from '@/types/api';
import { formatDateTime } from '@/utils/time';

const orderStore = useOrderStore();
const payingOrderNo = ref('');

async function pay(orderNo: string) {
  payingOrderNo.value = orderNo;
  try {
    const result = await orderStore.pay(orderNo);
    showSuccessToast(result.data.idempotent ? '订单已支付' : '支付成功');
  } catch (error) {
    const message = isApiBusinessError(error) ? friendlyMessage(error.code, error.message) : '支付失败';
    orderStore.markFailed(orderNo, message, isApiBusinessError(error) && error.code === 'ORDER_CLOSED' ? 'CLOSED' : 'FAILED');
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
