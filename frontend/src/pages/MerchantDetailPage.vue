<template>
  <main class="page">
    <van-skeleton v-if="loading" title avatar :row="10" />

    <template v-else-if="merchant">
      <div class="detail-title">
        <div>
          <RouterLink class="back-link" to="/merchants">返回商户列表</RouterLink>
          <h1>{{ merchant.name }}</h1>
          <div class="rating-line">
            <van-rate
              :model-value="merchant.score / 10"
              readonly
              allow-half
              size="17"
              color="#ff6633"
              void-color="#e4ded9"
            />
            <strong>{{ formatScore(merchant.score) }}</strong>
            <span>{{ merchant.commentCount }} 条评价</span>
            <span>{{ merchant.soldCount }} 人气</span>
          </div>
        </div>
        <button type="button" class="share-button" @click="showTodo">分享</button>
      </div>

      <section class="page-grid detail-grid">
        <div class="detail-main">
          <section class="card detail-card">
            <MerchantImageStrip :src="merchant.images" :alt="merchant.name" />

            <div class="merchant-info">
              <div class="rank-row">
                <span class="rank-badge">口碑好店</span>
                <span>{{ merchant.area || '附近' }}高分商户，适合聚餐和休闲小坐</span>
              </div>

              <div class="score-grid">
                <div>
                  <strong>4.8</strong>
                  <span>口味</span>
                </div>
                <div>
                  <strong>4.7</strong>
                  <span>环境</span>
                </div>
                <div>
                  <strong>4.8</strong>
                  <span>服务</span>
                </div>
              </div>

              <div class="info-row">
                <van-icon name="location-o" />
                <span>{{ merchant.address || '地址待完善' }}</span>
                <button type="button" @click="showTodo">导航</button>
              </div>
              <div class="info-row">
                <van-icon name="clock-o" />
                <span>营业时间 {{ merchant.openHours || '以门店为准' }}</span>
                <button type="button" @click="showTodo">详情</button>
              </div>
              <div class="info-row">
                <van-icon name="phone-o" />
                <span>电话联系</span>
                <button type="button" @click="showTodo">拨打</button>
              </div>
            </div>
          </section>

          <section class="section card comments-card">
            <div class="section-title">
              <div>
                <h2>网友评价</h2>
                <p>评论能力后续补齐，当前展示用户端占位结构。</p>
              </div>
              <button type="button" class="plain-link" @click="showTodo">查看全部</button>
            </div>
            <div class="comment-tags">
              <button type="button" @click="showTodo">味道不错</button>
              <button type="button" @click="showTodo">环境舒服</button>
              <button type="button" @click="showTodo">适合朋友聚餐</button>
              <button type="button" @click="showTodo">服务热情</button>
            </div>
            <div class="comment-placeholder">
              <div class="avatar" />
              <div>
                <strong>本地生活用户</strong>
                <p>这部分会在评论接口完成后接入真实数据，目前点击评论相关入口会提示功能未完成。</p>
              </div>
            </div>
          </section>
        </div>

        <aside class="sidebar detail-sidebar">
          <section class="voucher-panel">
            <div class="section-title">
              <div>
                <h2>代金券</h2>
                <p>{{ flashSaleCount }} 张秒杀券，以后端活动状态为准。</p>
              </div>
            </div>

            <EmptyState
              v-if="vouchers.length === 0"
              title="暂无优惠券"
              description="商户暂未配置优惠券"
            />
            <div v-else class="voucher-list">
              <VoucherTicket
                v-for="voucher in vouchers"
                :key="voucher.id"
                :voucher="voucher"
                :loading="claimingVoucherId === voucher.id"
                @claim="claimVoucher"
              />
            </div>
          </section>

          <section v-if="lastMessage || merchantLastOrder" class="result-panel">
            <span class="state-chip" :class="{ 'state-chip--danger': lastError }">
              {{ lastError ? '业务反馈' : '订单反馈' }}
            </span>
            <h3>{{ lastMessage || '最近订单待支付' }}</h3>
            <p v-if="merchantLastOrder">订单号 {{ merchantLastOrder.orderNo }}</p>
            <van-button
              v-if="merchantLastOrder?.status === 'PENDING_PAYMENT'"
              block
              type="primary"
              :loading="paying"
              @click="pay(merchantLastOrder.orderNo)"
            >
              模拟支付
            </van-button>
            <RouterLink class="orders-link" to="/orders">查看最近订单</RouterLink>
          </section>
        </aside>
      </section>
    </template>

    <EmptyState v-else title="商户不存在" description="请返回列表重新选择商户" />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { showFailToast, showSuccessToast, showToast } from 'vant';
import MerchantImageStrip from '@/components/MerchantImageStrip.vue';
import VoucherTicket from '@/components/VoucherTicket.vue';
import EmptyState from '@/components/EmptyState.vue';
import { getMerchant } from '@/api/merchant';
import { listMerchantVouchers } from '@/api/voucher';
import { createFlashSaleOrder } from '@/api/order';
import { Merchant } from '@/types/merchant';
import { Voucher } from '@/types/voucher';
import { ApiBusinessError, isApiBusinessError } from '@/types/api';
import { formatScore } from '@/utils/money';
import { friendlyMessage, todoMessage } from '@/utils/format';
import { useAuthStore } from '@/stores/auth';
import { useOrderStore } from '@/stores/order';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const orderStore = useOrderStore();

const merchant = ref<Merchant | null>(null);
const vouchers = ref<Voucher[]>([]);
const loading = ref(false);
const claimingVoucherId = ref<number | null>(null);
const paying = ref(false);
const lastMessage = ref('');
const lastError = ref(false);

const merchantId = computed(() => Number(route.params.id));
const flashSaleCount = computed(() => vouchers.value.filter((voucher) => voucher.type === 2).length);
const merchantLastOrder = computed(() => (
  orderStore.recentOrders.find((order) => order.merchantId === merchantId.value) || null
));

onMounted(loadDetail);

async function loadDetail() {
  loading.value = true;
  try {
    const [merchantResult, voucherResult] = await Promise.allSettled([
      getMerchant(merchantId.value),
      listMerchantVouchers(merchantId.value),
    ]);

    if (merchantResult.status === 'fulfilled') {
      merchant.value = merchantResult.value.data;
    }
    if (voucherResult.status === 'fulfilled') {
      vouchers.value = voucherResult.value.data;
    }
    if (merchantResult.status === 'rejected' || voucherResult.status === 'rejected') {
      showToast('功能未完成，请稍后再试');
    }
  } finally {
    loading.value = false;
  }
}

async function claimVoucher(voucher: Voucher) {
  if (voucher.type !== 2) {
    showToast(todoMessage());
    return;
  }
  if (!authStore.isLoggedIn) {
    showToast('请先登录');
    router.push({ path: '/login', query: { redirect: route.fullPath } });
    return;
  }

  claimingVoucherId.value = voucher.id;
  lastMessage.value = '';
  lastError.value = false;
  try {
    const result = await createFlashSaleOrder(voucher.id);
    orderStore.addRecentOrder({
      orderNo: result.data,
      voucherId: voucher.id,
      voucherTitle: voucher.title,
      merchantId: merchant.value?.id || voucher.merchantId,
      merchantName: merchant.value?.name || 'Life Service 商户',
      payAmountCent: voucher.payAmountCent,
      statusCode: 1,
    });
    lastMessage.value = '抢购成功，订单待支付';
    showSuccessToast('抢购成功');
  } catch (error) {
    const known: ApiBusinessError | null = isApiBusinessError(error) ? error : null;
    lastError.value = true;
    lastMessage.value = friendlyMessage(known?.code, known?.message);
    if (known?.code === 'UNAUTHORIZED') {
      router.push({ path: '/login', query: { redirect: route.fullPath } });
      return;
    }
    showFailToast(lastMessage.value);
  } finally {
    claimingVoucherId.value = null;
  }
}

async function pay(orderNo: string) {
  paying.value = true;
  try {
    const result = await orderStore.pay(orderNo);
    lastError.value = false;
    lastMessage.value = result.data.idempotent ? '订单已支付，本次为幂等返回' : '支付成功';
    showSuccessToast(lastMessage.value);
  } catch (error) {
    const known: ApiBusinessError | null = isApiBusinessError(error) ? error : null;
    lastError.value = true;
    lastMessage.value = friendlyMessage(known?.code, known?.message);
    orderStore.markFailed(orderNo, lastMessage.value, known?.code === 'ORDER_CLOSED' ? 'CLOSED' : 'FAILED');
    showFailToast(lastMessage.value);
  } finally {
    paying.value = false;
  }
}

function showTodo() {
  showToast(todoMessage());
}
</script>

<style scoped>
.detail-title {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
}

.back-link,
.plain-link,
.orders-link {
  border: 0;
  background: transparent;
  color: var(--brand-orange-deep);
  font-size: 14px;
  font-weight: 800;
}

h1 {
  margin: 10px 0 10px;
  font-size: 36px;
  line-height: 1.15;
}

.rating-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text-muted);
  font-size: 13px;
}

.rating-line strong {
  color: var(--brand-orange);
}

.share-button {
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-pill);
  background: var(--neutral-surface);
  color: var(--text-muted);
  font-weight: 800;
}

.detail-grid {
  grid-template-columns: minmax(0, 1fr) 360px;
}

.detail-main {
  display: grid;
  gap: 20px;
}

.detail-card {
  padding: 16px;
}

.merchant-info {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #b15e2c;
  font-size: 13px;
}

.rank-badge {
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  background: var(--rank-wash);
  font-weight: 800;
}

.score-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.score-grid div {
  padding: 12px;
  border-radius: var(--radius-sm);
  background: var(--neutral-surface-soft);
}

.score-grid strong,
.score-grid span {
  display: block;
}

.score-grid strong {
  color: var(--brand-orange);
  font-size: 20px;
}

.score-grid span {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 12px;
}

.info-row {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  border-top: 1px solid var(--neutral-line);
  color: var(--text-muted);
}

.info-row button {
  min-height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
  font-size: 12px;
  font-weight: 800;
}

.comments-card {
  padding: 18px;
}

.comment-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.comment-tags button {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(66, 127, 196, 0.35);
  border-radius: var(--radius-pill);
  background: rgba(66, 127, 196, 0.08);
  color: var(--info);
  font-weight: 700;
}

.comment-placeholder {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 12px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--neutral-line);
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-orange-soft), var(--rank-wash));
}

.comment-placeholder p {
  margin: 7px 0 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.detail-sidebar {
  top: 88px;
}

.voucher-panel,
.result-panel {
  padding: 18px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  box-shadow: var(--shadow-panel);
}

.voucher-list {
  display: grid;
  gap: 12px;
}

.result-panel {
  display: grid;
  gap: 12px;
  background: var(--voucher-wash);
}

.result-panel h3 {
  margin: 0;
  font-size: 18px;
}

.result-panel p {
  margin: 0;
  color: var(--text-muted);
  word-break: break-all;
}

@media (max-width: 1024px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .detail-title {
    align-items: flex-start;
    flex-direction: column;
  }

  h1 {
    font-size: 30px;
  }
}
</style>
