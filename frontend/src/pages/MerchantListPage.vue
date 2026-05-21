<template>
  <main class="page">
    <section class="list-head">
      <div>
        <span class="state-chip">商户发现</span>
        <h1>{{ pageTitle }}</h1>
        <p>按分类、关键词、人气和评分浏览附近好店。</p>
      </div>
      <SearchBar v-model="keywordInput" @search="applySearch" />
    </section>

    <section class="page-grid section">
      <div>
        <div class="filter-panel">
          <div class="filter-row">
            <button
              type="button"
              class="filter-chip"
              :class="{ 'filter-chip--active': !selectedCategoryId }"
              @click="selectCategory(null)"
            >
              全部
            </button>
            <button
              v-for="category in categories"
              :key="category.id"
              type="button"
              class="filter-chip"
              :class="{ 'filter-chip--active': selectedCategoryId === category.id }"
              @click="selectCategory(category.id)"
            >
              {{ category.name }}
            </button>
          </div>

          <div class="sort-row">
            <button
              v-for="item in sortOptions"
              :key="item.value"
              type="button"
              :class="{ active: sortMode === item.value }"
              @click="handleSort(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <van-skeleton v-if="loading" title :row="9" />
        <EmptyState
          v-else-if="sortedMerchants.length === 0"
          title="没有找到匹配商户"
          description="换个关键词或分类再试试"
        />
        <div v-else class="merchant-list">
          <MerchantCard
            v-for="merchant in sortedMerchants"
            :key="merchant.id"
            :merchant="merchant"
            @open="openMerchant"
          />
        </div>
      </div>

      <aside class="sidebar">
        <section class="side-card">
          <h3>筛选说明</h3>
          <p>分类和关键词会请求后端接口。人气、评分是当前页本地排序。距离需要定位能力，首版先提示功能未完成。</p>
        </section>
        <section class="side-card side-card--voucher">
          <span class="state-chip state-chip--danger">限时券</span>
          <h3>进入商户详情抢券</h3>
          <p>秒杀入口在商户详情页。活动未准备好时，页面会提示稍后再试。</p>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { showToast } from 'vant';
import { useRoute, useRouter } from 'vue-router';
import SearchBar from '@/components/SearchBar.vue';
import MerchantCard from '@/components/MerchantCard.vue';
import EmptyState from '@/components/EmptyState.vue';
import { listCategories, pageMerchants } from '@/api/merchant';
import { Merchant, MerchantCategory } from '@/types/merchant';
import { todoMessage } from '@/utils/format';

type SortMode = 'default' | 'popular' | 'score' | 'distance';

const route = useRoute();
const router = useRouter();
const categories = ref<MerchantCategory[]>([]);
const merchants = ref<Merchant[]>([]);
const loading = ref(false);
const selectedCategoryId = ref<number | null>(toNumber(route.query.categoryId));
const keywordInput = ref(String(route.query.keyword || ''));
const sortMode = ref<SortMode>('default');

const sortOptions: Array<{ label: string; value: SortMode }> = [
  { label: '默认', value: 'default' },
  { label: '距离', value: 'distance' },
  { label: '人气', value: 'popular' },
  { label: '评分', value: 'score' },
];

const pageTitle = computed(() => {
  if (keywordInput.value) return `搜索：${keywordInput.value}`;
  const category = categories.value.find((item) => item.id === selectedCategoryId.value);
  return category?.name || String(route.query.name || '全部商户');
});

const sortedMerchants = computed(() => {
  const list = [...merchants.value];
  if (sortMode.value === 'popular') {
    return list.sort((left, right) => (right.soldCount + right.commentCount) - (left.soldCount + left.commentCount));
  }
  if (sortMode.value === 'score') {
    return list.sort((left, right) => right.score - left.score);
  }
  return list;
});

onMounted(async () => {
  await Promise.all([loadCategories(), loadMerchants()]);
});

watch(
  () => route.query,
  () => {
    selectedCategoryId.value = toNumber(route.query.categoryId);
    keywordInput.value = String(route.query.keyword || '');
    loadMerchants();
  },
);

function toNumber(value: unknown): number | null {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) && numberValue > 0 ? numberValue : null;
}

async function loadCategories() {
  try {
    categories.value = (await listCategories()).data;
  } catch {
    showToast('功能未完成，请稍后再试');
  }
}

async function loadMerchants() {
  loading.value = true;
  try {
    const result = await pageMerchants({
      categoryId: selectedCategoryId.value,
      keyword: keywordInput.value,
      pageNo: 1,
      pageSize: 30,
    });
    merchants.value = result.data.records;
  } catch {
    merchants.value = [];
    showToast('功能未完成，请稍后再试');
  } finally {
    loading.value = false;
  }
}

function selectCategory(categoryId: number | null) {
  router.push({
    path: '/merchants',
    query: {
      categoryId: categoryId || undefined,
      keyword: keywordInput.value || undefined,
    },
  });
}

function applySearch(value = keywordInput.value) {
  router.push({
    path: '/merchants',
    query: {
      categoryId: selectedCategoryId.value || undefined,
      keyword: value || undefined,
    },
  });
}

function handleSort(mode: SortMode) {
  if (mode === 'distance') {
    showToast(todoMessage());
    return;
  }
  sortMode.value = mode;
}

function openMerchant(id: number) {
  router.push(`/merchants/${id}`);
}
</script>

<style scoped>
.list-head {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(420px, 1fr);
  gap: 36px;
  align-items: end;
  padding: 28px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background:
    linear-gradient(135deg, rgba(255, 241, 235, 0.92), rgba(255, 253, 251, 0.96)),
    var(--neutral-surface);
  box-shadow: var(--shadow-panel);
}

h1 {
  margin: 14px 0 8px;
  font-size: 34px;
  line-height: 1.15;
}

p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.65;
}

.filter-panel {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
}

.filter-row,
.sort-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-chip,
.sort-row button {
  min-height: 34px;
  padding: 0 13px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-pill);
  background: var(--neutral-surface);
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 800;
}

.filter-chip--active,
.sort-row button.active {
  border-color: rgba(255, 102, 51, 0.28);
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
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

.side-card--voucher {
  background: var(--voucher-wash);
}

.side-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
}

.side-card .state-chip + h3 {
  margin-top: 12px;
}

@media (max-width: 1024px) {
  .list-head {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .list-head {
    padding: 18px;
  }

  h1 {
    font-size: 28px;
  }
}
</style>
