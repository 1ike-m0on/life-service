<template>
  <main class="page">
    <van-skeleton v-if="loading" title avatar :row="10" />

    <template v-else-if="merchant">
      <div class="detail-title" data-testid="merchant-detail-page">
        <div>
          <RouterLink class="back-link" to="/merchants">返回找好店</RouterLink>
          <h1 data-testid="merchant-detail-name">{{ merchant.name }}</h1>
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
        <button type="button" class="share-button" @click="showTodo">收藏店铺</button>
      </div>

      <section class="page-grid detail-grid">
        <div class="detail-main">
          <section class="card detail-card">
            <MerchantImageStrip :src="merchant.images" :alt="merchant.name" :seed="merchant.id" />

            <div class="merchant-info">
              <div class="rank-row">
                <span class="rank-badge">口碑好店</span>
                <span>{{ merchant.area || '附近' }}高分商户，近期评价热度稳定</span>
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
                <h2>到店笔记</h2>
                <p>看看最近去过的人怎么评价环境、出品和排队情况。</p>
              </div>
              <button type="button" class="plain-link" @click="showTodo">查看全部</button>
            </div>
            <div v-if="merchantNotes.length > 0" class="merchant-notes">
              <LifeNoteCard
                v-for="note in merchantNotes"
                :key="note.id"
                :note="note"
                @open-note="openRelatedNote"
              />
            </div>
            <EmptyState v-else title="暂无到店笔记" description="后续用户发布的评价会展示在这里。" />
          </section>
        </div>

        <aside class="sidebar detail-sidebar">
          <section class="voucher-panel" data-testid="voucher-panel">
            <div class="section-title">
              <div>
                <h2>团购与代金券</h2>
                <p>{{ vouchers.length }} 个可选优惠，下单后可在订单页查看状态。</p>
              </div>
            </div>

            <EmptyState
              v-if="vouchers.length === 0"
              title="暂无可选优惠"
              description="可以先收藏店铺，稍后再回来看看。"
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

          <section v-if="lastMessage || merchantLastOrder" class="result-panel" data-testid="order-result-panel">
            <span class="state-chip" :class="{ 'state-chip--danger': lastError }">
              {{ lastError ? '购买反馈' : '订单状态' }}
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
              支付
            </van-button>
            <RouterLink class="orders-link" to="/orders">查看我的订单</RouterLink>
          </section>
        </aside>
      </section>
    </template>

    <EmptyState v-else title="商户不存在" description="请返回列表重新选择商户。" />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { showFailToast, showSuccessToast, showToast } from 'vant';
import MerchantImageStrip from '@/components/MerchantImageStrip.vue';
import VoucherTicket from '@/components/VoucherTicket.vue';
import EmptyState from '@/components/EmptyState.vue';
import LifeNoteCard from '@/components/LifeNoteCard.vue';
import { getMerchant } from '@/api/merchant';
import { listMerchantVouchers } from '@/api/voucher';
import { createFlashSaleOrder } from '@/api/order';
import { pageMerchantNotes } from '@/api/note';
import { isApiBusinessError } from '@/types/api';
import { formatScore } from '@/utils/money';
import { friendlyMessage, todoMessage } from '@/utils/format';
import { useAuthStore } from '@/stores/auth';
import { useOrderStore } from '@/stores/order';
import { notesForMerchant } from '@/data/lifeNotes';
import { lifeNoteToView, noteCardToView } from '@/utils/noteView';
import { merchantGalleryImages } from '@/utils/merchantImages';
import type { ApiBusinessError } from '@/types/api';
import type { Merchant } from '@/types/merchant';
import type { Voucher } from '@/types/voucher';
import type { NoteView } from '@/utils/noteView';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const orderStore = useOrderStore();

const merchant = ref<Merchant | null>(null);
const vouchers = ref<Voucher[]>([]);
const merchantNotes = ref<NoteView[]>([]);
const loading = ref(false);
const claimingVoucherId = ref<number | null>(null);
const paying = ref(false);
const lastMessage = ref('');
const lastError = ref(false);

const merchantId = computed(() => Number(route.params.id));
const merchantLastOrder = computed(() => (
  orderStore.recentOrders.find((order) => order.merchantId === merchantId.value) || null
));

onMounted(loadDetail);
watch(merchantId, loadDetail);

async function loadDetail() {
  loading.value = true;
  lastMessage.value = '';
  lastError.value = false;
  try {
    const [merchantResult, voucherResult, noteResult] = await Promise.allSettled([
      getMerchant(merchantId.value),
      listMerchantVouchers(merchantId.value),
      pageMerchantNotes(merchantId.value, { pageNo: 1, pageSize: 8 }),
    ]);

    if (merchantResult.status === 'fulfilled') {
      merchant.value = merchantResult.value.data;
    }
    if (voucherResult.status === 'fulfilled') {
      vouchers.value = voucherResult.value.data;
    }
    if (noteResult.status === 'fulfilled') {
      merchantNotes.value = noteResult.value.data.records.map(noteCardToView);
    } else {
      merchantNotes.value = notesForMerchant(merchantId.value).map(lifeNoteToView);
    }
    if (authStore.isLoggedIn) {
      orderStore.loadMyOrders().catch(() => undefined);
    }
    if (merchantResult.status === 'rejected' || voucherResult.status === 'rejected') {
      showToast('部分详情暂时没有加载完整');
    }
  } finally {
    loading.value = false;
  }
}

async function claimVoucher(voucher: Voucher) {
  if (voucher.type !== 2) {
    showToast('到店出示即可使用');
    return;
  }
  if (!authStore.isLoggedIn) {
    showToast('登录后可继续购买');
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
      voucherSubtitle: voucher.subtitle,
      merchantId: merchant.value?.id || voucher.merchantId,
      merchantName: merchant.value?.name || 'Life Service 商户',
      merchantImages: merchantGalleryImages(merchant.value?.images, merchant.value?.id || voucher.merchantId, 3),
      payAmountCent: voucher.payAmountCent,
      statusCode: 1,
      message: '下单成功，等待支付',
    });
    lastMessage.value = '下单成功，订单待支付';
    showSuccessToast('下单成功');
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

function openRelatedNote(id: number) {
  router.push(`/notes/${id}`);
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

.merchant-notes {
  column-count: 2;
  column-gap: 16px;
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
  overflow: hidden;
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

  .merchant-notes {
    column-count: 1;
  }
}
</style>
