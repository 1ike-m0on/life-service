<template>
  <main class="page home-page">
    <section class="home-hero">
      <div class="home-hero__copy">
        <span class="state-chip">杭州今日推荐</span>
        <h1>今天想吃点什么，先看附近的人怎么选。</h1>
        <p>从真实笔记进入店铺详情，再查看团购券、秒杀券和订单状态。</p>
      </div>
      <SearchBar v-model="keywordInput" @search="applySearch" />
      <div class="hot-searches" aria-label="热门搜索">
        <span>大家在搜</span>
        <button
          v-for="keyword in hotKeywords"
          :key="keyword"
          type="button"
          @click="goSearch(keyword)"
        >
          {{ keyword }}
        </button>
      </div>
    </section>

    <section class="quick-categories" aria-label="生活分类">
      <button
        v-for="category in displayCategories"
        :key="category.id"
        type="button"
        class="category-entry"
        @click="goCategory(category.id)"
      >
        <span class="category-entry__icon">{{ iconFor(category.name) }}</span>
        <span>{{ category.name }}</span>
      </button>
    </section>

    <section class="home-board" aria-label="好店榜单">
      <div>
        <div class="section-title">
          <div>
            <h2>本周好店榜</h2>
            <p>先从高赞笔记里挑几家，再去店铺页看优惠和评价。</p>
          </div>
          <RouterLink class="plain-link" to="/merchants">全部好店</RouterLink>
        </div>

        <div class="rank-picks">
          <article
            v-for="(note, index) in rankNotes"
            :key="note.id"
            class="rank-pick"
            @click="openNote(note.id)"
          >
            <img :src="note.image" :alt="note.title" loading="lazy" />
            <div>
              <span class="rank-pick__index">TOP {{ index + 1 }}</span>
              <h3>{{ note.merchantName }}</h3>
              <p>{{ note.title }}</p>
              <div class="rank-pick__meta">
                <span>{{ note.area }}</span>
                <span>{{ note.likes }} 人想去</span>
              </div>
            </div>
          </article>
        </div>
      </div>

      <aside class="scene-panel">
        <h2>按场景找</h2>
        <button
          v-for="scene in sceneKeywords"
          :key="scene.label"
          type="button"
          @click="goSearch(scene.keyword)"
        >
          <span>{{ scene.label }}</span>
          <small>{{ scene.hint }}</small>
        </button>
      </aside>
    </section>

    <section class="feed-head">
      <div>
        <h2>最近大家在去哪儿</h2>
        <p>首页展示笔记和体验内容，店铺页再承接团购券、秒杀券和下单。</p>
      </div>
      <div class="feed-tabs">
        <button type="button" class="active">推荐</button>
        <button type="button" @click="goSearch('火锅')">美食</button>
        <button type="button" @click="goSearch('咖啡')">咖啡</button>
        <button type="button" @click="goSearch('周末')">周末</button>
      </div>
    </section>

    <van-skeleton v-if="loading" title :row="10" />
    <section v-else class="note-feed" aria-label="本地生活笔记">
      <LifeNoteCard
        v-for="note in feedNotes"
        :key="note.id"
        :note="note"
        @open-note="openNote"
      />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { showToast } from 'vant';
import SearchBar from '@/components/SearchBar.vue';
import LifeNoteCard from '@/components/LifeNoteCard.vue';
import { listCategories } from '@/api/merchant';
import { pageNotes } from '@/api/note';
import { lifeNotes } from '@/data/lifeNotes';
import { lifeNoteToView, noteCardToView } from '@/utils/noteView';
import type { MerchantCategory } from '@/types/merchant';
import type { NoteView } from '@/utils/noteView';

const router = useRouter();
const loading = ref(false);
const categories = ref<MerchantCategory[]>([]);
const remoteNotes = ref<NoteView[]>([]);
const keywordInput = ref('');

const hotKeywords = ['火锅', '咖啡自习', '周末约会', '烘焙早餐', '日料午餐'];
const sceneKeywords = [
  { label: '朋友聚餐', hint: '热闹、不踩雷', keyword: '朋友聚餐' },
  { label: '一个人吃', hint: '快、稳、舒服', keyword: '午餐' },
  { label: '下午小坐', hint: '咖啡、甜品、靠窗', keyword: '咖啡' },
  { label: '周末放松', hint: '电影、运动、散步', keyword: '周末' },
];

const fallbackCategories: MerchantCategory[] = [
  { id: 2, name: '美食', sortOrder: 1, status: 1 },
  { id: 1, name: '咖啡', sortOrder: 2, status: 1 },
  { id: 3, name: '烘焙甜品', sortOrder: 3, status: 1 },
  { id: 4, name: '日料', sortOrder: 4, status: 1 },
  { id: 6, name: '电影演出', sortOrder: 5, status: 1 },
  { id: 5, name: '健身运动', sortOrder: 6, status: 1 },
  { id: 7, name: '周边游', sortOrder: 7, status: 1 },
  { id: 8, name: '更多', sortOrder: 8, status: 1 },
];

const fallbackNotes = computed(() => lifeNotes.map(lifeNoteToView));
const displayCategories = computed(() => (
  categories.value.length > 0 ? categories.value.slice(0, 8) : fallbackCategories
));
const feedNotes = computed(() => (
  remoteNotes.value.length > 0 ? remoteNotes.value : fallbackNotes.value
));
const rankNotes = computed(() => [...feedNotes.value].sort((a, b) => b.likes - a.likes).slice(0, 3));

onMounted(loadHome);

async function loadHome() {
  loading.value = true;
  const [categoryResult, noteResult] = await Promise.allSettled([
    listCategories(),
    pageNotes({ pageNo: 1, pageSize: 20 }),
  ]);

  if (categoryResult.status === 'fulfilled') {
    categories.value = categoryResult.value.data;
  }
  if (noteResult.status === 'fulfilled') {
    remoteNotes.value = noteResult.value.data.records.map(noteCardToView);
  }
  if (categoryResult.status === 'rejected' || noteResult.status === 'rejected') {
    showToast('部分内容暂时使用本地展示数据');
  }
  loading.value = false;
}

function iconFor(name: string): string {
  if (name.includes('咖')) return '咖';
  if (name.includes('火') || name.includes('美') || name.includes('食')) return '食';
  if (name.includes('甜') || name.includes('烘')) return '甜';
  if (name.includes('日')) return '日';
  if (name.includes('影')) return '影';
  if (name.includes('健')) return '动';
  if (name.includes('游')) return '游';
  return '好';
}

function goCategory(categoryId: number) {
  router.push({ path: '/merchants', query: { categoryId } });
}

function goSearch(keyword: string) {
  router.push({ path: '/merchants', query: { keyword } });
}

function applySearch(value = keywordInput.value) {
  router.push({ path: '/merchants', query: { keyword: value || undefined } });
}

function openNote(noteId: number) {
  router.push(`/notes/${noteId}`);
}
</script>

<style scoped>
.home-page {
  padding-top: 24px;
}

.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(420px, 1.1fr);
  gap: 22px 36px;
  align-items: end;
  padding: 30px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(135deg, oklch(0.985 0.038 72), oklch(0.975 0.018 105)),
    var(--neutral-surface);
  box-shadow: var(--shadow-panel);
}

.home-hero__copy h1 {
  max-width: 680px;
  margin: 16px 0 10px;
  color: var(--text-strong);
  font-size: 38px;
  line-height: 1.16;
}

.home-hero__copy p {
  max-width: 58ch;
  margin: 0;
  color: var(--text-muted);
  font-size: 15px;
  line-height: 1.7;
}

.hot-searches {
  grid-column: 2;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text-muted);
  font-size: 13px;
}

.hot-searches span {
  font-weight: 800;
}

.hot-searches button,
.plain-link {
  min-height: 30px;
  padding: 0 11px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-pill);
  background: var(--neutral-surface);
  color: var(--brand-orange-deep);
  font-size: 13px;
  font-weight: 800;
}

.quick-categories {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
  padding: 20px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-lg);
  background: var(--neutral-surface);
}

.category-entry {
  display: grid;
  justify-items: center;
  gap: 9px;
  min-width: 0;
  min-height: 92px;
  padding: 10px 6px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 700;
  transition: background 150ms ease, transform 150ms ease;
}

.category-entry span:last-child {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-entry:hover {
  transform: translateY(-1px);
  background: var(--brand-orange-soft);
}

.category-entry__icon {
  display: grid;
  place-items: center;
  width: 50px;
  height: 50px;
  border-radius: 17px;
  background: linear-gradient(135deg, oklch(0.95 0.08 83), var(--brand-orange) 62%, oklch(0.66 0.18 31));
  color: var(--neutral-surface);
  font-size: 21px;
  font-weight: 900;
  box-shadow: 0 8px 18px oklch(0.75 0.17 55 / 0.16);
}

.home-board {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 24px;
  align-items: start;
  margin-top: 30px;
}

.rank-picks {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.rank-pick {
  overflow: hidden;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-lg);
  background: var(--neutral-surface);
  cursor: pointer;
  transition: transform 150ms ease, box-shadow 150ms ease;
}

.rank-pick:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card);
}

.rank-pick img {
  display: block;
  width: 100%;
  height: 148px;
  object-fit: cover;
  background: var(--neutral-surface-soft);
}

.rank-pick div {
  padding: 13px;
}

.rank-pick__index {
  color: var(--brand-orange-deep);
  font-size: 12px;
  font-weight: 900;
}

.rank-pick h3 {
  margin: 6px 0;
  font-size: 17px;
  line-height: 1.3;
}

.rank-pick p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.rank-pick__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding: 0;
  color: var(--text-muted);
  font-size: 12px;
}

.scene-panel {
  display: grid;
  gap: 10px;
  padding: 18px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-lg);
  background: var(--neutral-surface);
  box-shadow: var(--shadow-panel);
}

.scene-panel h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.scene-panel button {
  display: grid;
  gap: 4px;
  min-height: 58px;
  padding: 11px 12px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  color: var(--text-strong);
  text-align: left;
}

.scene-panel button:hover {
  border-color: oklch(0.84 0.09 58);
  background: var(--brand-orange-soft);
}

.scene-panel span {
  font-weight: 900;
}

.scene-panel small {
  color: var(--text-muted);
  font-size: 12px;
}

.feed-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin: 38px 0 20px;
}

.feed-head h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 26px;
  line-height: 1.2;
}

.feed-head p {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 14px;
}

.feed-tabs {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.feed-tabs button {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--neutral-line);
  border-radius: 999px;
  background: var(--neutral-surface);
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.feed-tabs button.active {
  border-color: oklch(0.84 0.09 58);
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
}

.note-feed {
  column-count: 4;
  column-gap: 22px;
}

@media (max-width: 1024px) {
  .home-hero,
  .home-board {
    grid-template-columns: 1fr;
  }

  .hot-searches {
    grid-column: auto;
  }

  .quick-categories {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .note-feed {
    column-count: 3;
  }
}

@media (max-width: 720px) {
  .home-page {
    width: calc(100vw - 24px);
    max-width: calc(100vw - 24px);
    overflow-x: hidden;
  }

  .home-hero {
    padding: 20px;
  }

  .home-hero__copy h1 {
    font-size: 30px;
  }

  .quick-categories {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px;
    padding: 12px 8px;
  }

  .category-entry {
    min-height: 82px;
    font-size: 12px;
  }

  .category-entry__icon {
    width: 42px;
    height: 42px;
    border-radius: 14px;
    font-size: 18px;
  }

  .rank-picks {
    grid-template-columns: 1fr;
  }

  .feed-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .note-feed {
    column-count: 1;
  }
}
</style>
