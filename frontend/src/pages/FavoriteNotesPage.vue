<template>
  <main class="page">
    <section class="orders-head">
      <div>
        <span class="state-chip">我的收藏</span>
        <h1>收藏过的好店笔记</h1>
        <p>把想去的店先收起来，之后可以从这里回到笔记和商户详情。</p>
      </div>
      <RouterLink class="primary-link" to="/">继续看笔记</RouterLink>
    </section>

    <section v-if="!authStore.isLoggedIn" class="section">
      <EmptyState title="登录后查看收藏" description="邮箱登录后可以同步查看收藏过的笔记。">
        <RouterLink class="empty-link" to="/login?redirect=/favorites">去登录</RouterLink>
      </EmptyState>
    </section>

    <section v-else class="section">
      <van-skeleton v-if="loading" title :row="10" />
      <EmptyState v-else-if="notes.length === 0" title="暂无收藏" description="在笔记详情页点击收藏后，会展示在这里。">
        <RouterLink class="empty-link" to="/">去看首页笔记</RouterLink>
      </EmptyState>
      <div v-else class="note-feed">
        <LifeNoteCard v-for="note in notes" :key="note.id" :note="note" @open-note="openNote" />
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { showFailToast } from 'vant';
import EmptyState from '@/components/EmptyState.vue';
import LifeNoteCard from '@/components/LifeNoteCard.vue';
import { pageFavoriteNotes } from '@/api/note';
import { useAuthStore } from '@/stores/auth';
import { friendlyMessage } from '@/utils/format';
import { isApiBusinessError } from '@/types/api';
import { noteCardToView } from '@/utils/noteView';
import type { NoteView } from '@/utils/noteView';

const authStore = useAuthStore();
const router = useRouter();
const loading = ref(false);
const notes = ref<NoteView[]>([]);

onMounted(loadFavorites);

async function loadFavorites() {
  if (!authStore.isLoggedIn) {
    return;
  }

  loading.value = true;
  try {
    const result = await pageFavoriteNotes({ pageNo: 1, pageSize: 30 });
    notes.value = result.data.records.map(noteCardToView);
  } catch (error) {
    const message = isApiBusinessError(error) ? friendlyMessage(error.code, error.message) : '收藏加载失败';
    showFailToast(message);
  } finally {
    loading.value = false;
  }
}

function openNote(noteId: number) {
  router.push(`/notes/${noteId}`);
}
</script>

<style scoped>
.orders-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  padding: 34px 0 22px;
}

.orders-head h1 {
  margin: 12px 0 8px;
  color: var(--text-strong);
  font-size: 34px;
  line-height: 1.15;
}

.orders-head p {
  margin: 0;
  color: var(--text-muted);
}

.primary-link,
.empty-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 18px;
  border-radius: var(--radius-pill);
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-weight: 800;
  white-space: nowrap;
}

.note-feed {
  column-count: 4;
  column-gap: 18px;
}

.note-feed :deep(.note-card) {
  margin-bottom: 18px;
  break-inside: avoid;
}

@media (max-width: 1180px) {
  .note-feed {
    column-count: 3;
  }
}

@media (max-width: 820px) {
  .orders-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .note-feed {
    column-count: 2;
  }
}

@media (max-width: 560px) {
  .note-feed {
    column-count: 1;
  }
}
</style>
