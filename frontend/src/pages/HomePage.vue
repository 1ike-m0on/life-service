<template>
  <div class="home-page">
    <section class="home-hero">
      <div class="page home-hero__inner">
        <div class="home-hero__copy">
          <span class="state-chip">杭州本地生活</span>
          <h1>找附近好店，抢限时优惠券</h1>
          <p>浏览商户、查看代金券、参与秒杀抢券，体验 Life Service 的核心用户链路。</p>
        </div>
        <SearchBar v-model="keyword" @search="goSearch" />
      </div>
    </section>

    <main class="page">
      <section class="section">
        <div class="section-title">
          <div>
            <h2>精选分类</h2>
            <p>参考 hmdp 的本地生活入口，保留轻量清晰的 PC 展示。</p>
          </div>
          <button type="button" class="plain-link" @click="showTodo">更多分类</button>
        </div>
        <CategoryGrid :categories="categories" @select="goCategory" />
      </section>

      <section class="section page-grid">
        <div>
          <div class="section-title">
            <div>
              <h2>附近推荐</h2>
              <p>真实调用商户列表接口，图片为空时使用产品占位图。</p>
            </div>
            <RouterLink class="plain-link" to="/merchants">全部商户</RouterLink>
          </div>

          <van-skeleton v-if="loading" title :row="8" />
          <EmptyState
            v-else-if="merchants.length === 0"
            title="暂无商户"
            description="商户数据暂未准备好"
          />
          <div v-else class="merchant-list">
            <MerchantCard
              v-for="merchant in merchants"
              :key="merchant.id"
              :merchant="merchant"
              @open="openMerchant"
            />
          </div>
        </div>

        <aside class="sidebar">
          <section class="side-card side-card--hot">
            <span class="state-chip state-chip--warn">今日热门</span>
            <h3>周五晚餐好去处</h3>
            <p>按人气和评分优先展示餐饮、咖啡、甜品类商户。</p>
            <button type="button" @click="goSearch('美食')">看看美食</button>
          </section>

          <section class="side-card side-card--voucher">
            <span class="ticket-label">券</span>
            <h3>限时抢券</h3>
            <p>秒杀券以服务端活动状态为准。活动未准备好时会直接提示用户稍后再试。</p>
            <RouterLink to="/merchants">去抢券</RouterLink>
          </section>

          <OrderMiniCard v-if="orderStore.lastOrder" :order="orderStore.lastOrder" />
          <section v-else class="side-card">
            <h3>最近订单</h3>
            <p>抢券成功后会在这里展示最近订单，完整订单中心后续再补。</p>
            <RouterLink to="/orders">查看订单</RouterLink>
          </section>
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { showToast } from 'vant';
import SearchBar from '@/components/SearchBar.vue';
import CategoryGrid from '@/components/CategoryGrid.vue';
import MerchantCard from '@/components/MerchantCard.vue';
import EmptyState from '@/components/EmptyState.vue';
import OrderMiniCard from '@/components/OrderMiniCard.vue';
import { listCategories, pageMerchants } from '@/api/merchant';
import { Merchant, MerchantCategory } from '@/types/merchant';
import { todoMessage } from '@/utils/format';
import { useOrderStore } from '@/stores/order';

const router = useRouter();
const orderStore = useOrderStore();
const keyword = ref('');
const loading = ref(false);
const categories = ref<MerchantCategory[]>([]);
const merchants = ref<Merchant[]>([]);

onMounted(loadHome);

async function loadHome() {
  loading.value = true;
  try {
    const [categoryResult, merchantResult] = await Promise.allSettled([
      listCategories(),
      pageMerchants({ pageNo: 1, pageSize: 8 }),
    ]);

    if (categoryResult.status === 'fulfilled') {
      categories.value = categoryResult.value.data;
    }
    if (merchantResult.status === 'fulfilled') {
      merchants.value = merchantResult.value.data.records;
    }
    if (categoryResult.status === 'rejected' || merchantResult.status === 'rejected') {
      showToast('功能未完成，请稍后再试');
    }
  } finally {
    loading.value = false;
  }
}

function goSearch(value = keyword.value) {
  router.push({ path: '/merchants', query: { keyword: value || undefined } });
}

function goCategory(categoryId: number) {
  router.push({ path: '/merchants', query: { categoryId } });
}

function openMerchant(id: number) {
  router.push(`/merchants/${id}`);
}

function showTodo() {
  showToast(todoMessage());
}
</script>

<style scoped>
.home-hero {
  background:
    linear-gradient(180deg, rgba(255, 241, 235, 0.96), rgba(255, 253, 251, 0.72)),
    var(--neutral-surface);
  border-bottom: 1px solid var(--neutral-line);
}

.home-hero__inner {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(420px, 1fr);
  align-items: end;
  gap: 42px;
  padding-top: 44px;
  padding-bottom: 42px;
}

.home-hero__copy h1 {
  max-width: 520px;
  margin: 18px 0 12px;
  font-size: 42px;
  line-height: 1.12;
}

.home-hero__copy p {
  max-width: 560px;
  margin: 0;
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1.7;
}

.plain-link {
  border: 0;
  background: transparent;
  color: var(--brand-orange-deep);
  font-size: 14px;
  font-weight: 800;
}

.merchant-list {
  display: grid;
  gap: 14px;
}

.side-card {
  padding: 18px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  box-shadow: var(--shadow-panel);
}

.side-card--hot {
  background:
    linear-gradient(135deg, rgba(255, 235, 207, 0.78), rgba(255, 253, 251, 0.92)),
    var(--neutral-surface);
}

.side-card--voucher {
  background: var(--voucher-wash);
}

.side-card h3 {
  margin: 12px 0 8px;
  font-size: 18px;
}

.side-card p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.side-card button,
.side-card a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  margin-top: 16px;
  padding: 0 14px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-size: 13px;
  font-weight: 800;
}

.ticket-label {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-weight: 800;
}

@media (max-width: 1024px) {
  .home-hero__inner {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .home-hero__copy h1 {
    font-size: 30px;
  }
}
</style>
