<template>
  <main class="page me-page">
    <section class="profile-card card">
      <div class="profile-card__avatar">
        {{ avatarText }}
      </div>
      <div class="profile-card__body">
        <span class="state-chip">{{ authStore.isLoggedIn ? '已登录' : '未登录' }}</span>
        <h1>{{ authStore.currentUser?.nickname || '欢迎来到 Life Service' }}</h1>
        <p>{{ authStore.currentUser?.email || '登录后可以收藏笔记、查看订单，并继续体验秒杀抢券和支付流程。' }}</p>
        <div class="profile-card__actions">
          <RouterLink v-if="!authStore.isLoggedIn" class="primary-link" to="/login?redirect=/me">
            邮箱登录
          </RouterLink>
          <button v-else type="button" class="logout-button" :disabled="loggingOut" @click="logout">
            退出登录
          </button>
        </div>
      </div>
    </section>

    <section class="section page-grid">
      <div class="entry-panel card">
        <div class="section-title">
          <div>
            <h2>我的服务</h2>
            <p>常用入口集中在这里，登录后可以继续查看订单、收藏和浏览过的内容。</p>
          </div>
        </div>

        <div class="entry-list">
          <RouterLink class="entry-item" to="/orders">
            <span class="entry-item__icon"><van-icon name="orders-o" /></span>
            <span>
              <strong>我的订单</strong>
              <small>查看最近抢到的券和支付状态</small>
            </span>
            <van-icon name="arrow" />
          </RouterLink>

          <RouterLink class="entry-item" to="/favorites">
            <span class="entry-item__icon"><van-icon name="star-o" /></span>
            <span>
              <strong>我的收藏</strong>
              <small>收藏过的笔记和想去的店</small>
            </span>
            <van-icon name="arrow" />
          </RouterLink>

          <button type="button" class="entry-item" @click="showTodo">
            <span class="entry-item__icon"><van-icon name="clock-o" /></span>
            <span>
              <strong>浏览历史</strong>
              <small>记录最近查看的商户</small>
            </span>
            <van-icon name="arrow" />
          </button>

          <button type="button" class="entry-item" @click="showTodo">
            <span class="entry-item__icon"><van-icon name="setting-o" /></span>
            <span>
              <strong>设置</strong>
              <small>账号偏好与提醒</small>
            </span>
            <van-icon name="arrow" />
          </button>
        </div>

        <section v-if="authStore.isLoggedIn" class="favorite-preview">
          <div class="section-title">
            <div>
              <h2>最近收藏</h2>
              <p>从这里快速回到想看的笔记。</p>
            </div>
            <RouterLink to="/favorites">全部收藏</RouterLink>
          </div>
          <van-skeleton v-if="favoritesLoading" title :row="4" />
          <EmptyState v-else-if="favoriteNotes.length === 0" title="还没有收藏" description="看到喜欢的笔记时，点一下收藏就会出现在这里。" />
          <div v-else class="favorite-grid">
            <LifeNoteCard v-for="note in favoriteNotes" :key="note.id" :note="note" @open-note="openNote" />
          </div>
        </section>
      </div>

      <aside class="sidebar">
        <section class="side-card">
          <h3>今日可做</h3>
          <p>从商户详情进入团购或代金券，待支付订单会同步显示在订单页。</p>
        </section>
        <section class="side-card side-card--voucher">
          <h3>继续找店</h3>
          <p>进入商户详情页即可查看图集、到店笔记和可选优惠。</p>
          <RouterLink to="/merchants">浏览商户</RouterLink>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { showSuccessToast, showToast } from 'vant';
import EmptyState from '@/components/EmptyState.vue';
import LifeNoteCard from '@/components/LifeNoteCard.vue';
import { pageFavoriteNotes } from '@/api/note';
import { useAuthStore } from '@/stores/auth';
import { todoMessage } from '@/utils/format';
import { noteCardToView } from '@/utils/noteView';
import type { NoteView } from '@/utils/noteView';

const router = useRouter();
const authStore = useAuthStore();
const loggingOut = ref(false);
const favoritesLoading = ref(false);
const favoriteNotes = ref<NoteView[]>([]);

const avatarText = computed(() => {
  const source = authStore.currentUser?.nickname || authStore.currentUser?.email || 'LS';
  return source.slice(0, 2).toUpperCase();
});

onMounted(async () => {
  if (authStore.isLoggedIn) {
    await authStore.fetchMe().catch(() => undefined);
    loadFavorites();
  }
});

async function loadFavorites() {
  favoritesLoading.value = true;
  try {
    const result = await pageFavoriteNotes({ pageNo: 1, pageSize: 4 });
    favoriteNotes.value = result.data.records.map(noteCardToView);
  } catch {
    favoriteNotes.value = [];
  } finally {
    favoritesLoading.value = false;
  }
}

async function logout() {
  loggingOut.value = true;
  try {
    await authStore.logout();
    showSuccessToast('已退出登录');
    router.push('/');
  } finally {
    loggingOut.value = false;
  }
}

function showTodo() {
  showToast(todoMessage());
}

function openNote(noteId: number) {
  router.push(`/notes/${noteId}`);
}
</script>

<style scoped>
.profile-card {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 22px;
  padding: 28px;
  background:
    linear-gradient(135deg, rgba(255, 232, 217, 0.92), rgba(255, 235, 207, 0.62)),
    var(--neutral-surface);
}

.profile-card__avatar {
  width: 96px;
  height: 96px;
  display: grid;
  place-items: center;
  border-radius: 28px;
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-size: 26px;
  font-weight: 900;
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

.profile-card__actions {
  margin-top: 18px;
}

.primary-link,
.logout-button,
.side-card a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 16px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-weight: 800;
}

.logout-button {
  background: var(--neutral-surface);
  color: var(--danger);
  border: 1px solid rgba(240, 51, 51, 0.22);
}

.entry-panel {
  padding: 20px;
}

.entry-list {
  display: grid;
  gap: 10px;
}

.entry-item {
  width: 100%;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 12px;
  min-height: 70px;
  padding: 12px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  color: var(--text-strong);
  text-align: left;
}

.entry-item__icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 15px;
  background: var(--brand-orange-soft);
  color: var(--brand-orange);
  font-size: 22px;
}

.entry-item strong,
.entry-item small {
  display: block;
}

.entry-item small {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.favorite-preview {
  margin-top: 28px;
}

.favorite-preview .section-title a {
  color: var(--brand-orange-deep);
  font-weight: 800;
}

.favorite-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
}

.side-card a {
  margin-top: 16px;
}

@media (max-width: 720px) {
  .profile-card {
    grid-template-columns: 1fr;
  }

  h1 {
    font-size: 28px;
  }

  .favorite-grid {
    grid-template-columns: 1fr;
  }
}
</style>
