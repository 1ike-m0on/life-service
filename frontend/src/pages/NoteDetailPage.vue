<template>
  <main class="page note-detail-page">
    <van-skeleton v-if="loading" title avatar :row="10" />

    <template v-else-if="note">
      <div class="note-top">
        <RouterLink class="back-link" to="/">返回首页</RouterLink>
        <RouterLink class="merchant-link" :to="`/merchants/${note.merchantId}`">去看看这家店</RouterLink>
      </div>

      <section class="note-layout">
        <div class="note-media">
          <img :src="note.image" :alt="note.title" />
          <span class="image-count">1/{{ note.images.length }}</span>
        </div>

        <article class="note-content">
          <header class="note-author">
            <div class="author-avatar">{{ note.author.slice(0, 1) }}</div>
            <div>
              <strong>{{ note.author }}</strong>
              <span>{{ note.area }} 路 {{ note.merchantName }}</span>
            </div>
            <button type="button" @click="showTodo">关注</button>
          </header>

          <h1>{{ note.title }}</h1>
          <p class="note-lead">{{ note.excerpt }}</p>

          <div class="note-body">
            <p v-for="paragraph in paragraphs" :key="paragraph">{{ paragraph }}</p>
          </div>

          <div class="note-tags" aria-label="笔记标签">
            <span v-for="tag in note.tags" :key="tag">{{ tag }}</span>
          </div>

          <RouterLink class="merchant-card" :to="`/merchants/${note.merchantId}`">
            <span class="merchant-card__icon"><van-icon name="shop-o" /></span>
            <span>
              <strong>{{ note.merchantName }}</strong>
              <small>{{ note.area }} 路，查看图集、评价和团购券</small>
            </span>
            <van-icon name="arrow" />
          </RouterLink>

          <footer class="note-actions" aria-label="笔记互动">
            <button type="button" @click="showTodo">
              <van-icon name="like-o" />
              {{ note.likes }}
            </button>
            <button type="button" :class="{ active: favorited }" :disabled="favoriteLoading" @click="toggleFavorite">
              <van-icon :name="favorited ? 'star' : 'star-o'" />
              {{ favorited ? '已收藏' : '收藏' }}
            </button>
            <button type="button" @click="focusComment">
              <van-icon name="comment-o" />
              {{ totalComments }} 评
            </button>
            <button type="button" @click="showTodo">
              <van-icon name="share-o" />
              分享
            </button>
          </footer>

          <section class="comments-section">
            <div class="section-title section-title--compact">
              <div>
                <h2>评论</h2>
                <p>把真实体验留在这里，后续也会进入店铺评价区。</p>
              </div>
            </div>

            <div v-if="authStore.isLoggedIn" class="comment-box">
              <textarea
                ref="commentInputRef"
                v-model="commentText"
                rows="3"
                maxlength="500"
                placeholder="说说你的体验..."
              />
              <button type="button" :disabled="commentSubmitting || !commentText.trim()" @click="submitComment">
                {{ commentSubmitting ? '发布中' : '发布评论' }}
              </button>
            </div>
            <div v-else class="login-tip">
              <span>登录后可以评论和收藏笔记。</span>
              <RouterLink :to="{ path: '/login', query: { redirect: route.fullPath } }">去登录</RouterLink>
            </div>

            <div v-if="comments.length > 0" class="comment-list">
              <article v-for="comment in comments" :key="comment.id" class="comment-item">
                <div class="comment-avatar">{{ comment.nickname.slice(0, 1) }}</div>
                <div>
                  <strong>{{ comment.nickname }}</strong>
                  <p>{{ comment.content }}</p>
                  <time>{{ formatDateTime(comment.createdAt) }}</time>
                </div>
              </article>
            </div>
            <EmptyState v-else title="暂无评论" description="成为第一个留下体验的人。" />
          </section>
        </article>
      </section>

      <section class="related-section">
        <div class="section-title">
          <div>
            <h2>相关推荐</h2>
            <p>继续看看同类场景和附近好店。</p>
          </div>
        </div>
        <div class="related-notes">
          <LifeNoteCard
            v-for="item in relatedNotes"
            :key="item.id"
            :note="item"
            @open-note="openNote"
          />
        </div>
      </section>
    </template>

    <EmptyState v-else title="笔记不存在" description="请返回首页重新选择笔记。" />
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { showFailToast, showSuccessToast, showToast } from 'vant';
import EmptyState from '@/components/EmptyState.vue';
import LifeNoteCard from '@/components/LifeNoteCard.vue';
import {
  cancelFavoriteNote,
  createNoteComment,
  favoriteNote,
  getNote,
  getNoteFavorite,
  pageNoteComments,
  pageNotes,
} from '@/api/note';
import { noteById, noteContent, relatedNotesForNote } from '@/data/lifeNotes';
import { friendlyMessage, todoMessage } from '@/utils/format';
import { lifeNoteToView, noteCardToView } from '@/utils/noteView';
import { formatDateTime } from '@/utils/time';
import { useAuthStore } from '@/stores/auth';
import { isApiBusinessError } from '@/types/api';
import type { NoteComment } from '@/types/note';
import type { NoteView } from '@/utils/noteView';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const note = ref<NoteView | null>(null);
const relatedNotes = ref<NoteView[]>([]);
const comments = ref<NoteComment[]>([]);
const totalComments = ref(0);
const favorited = ref(false);
const favoriteLoading = ref(false);
const commentText = ref('');
const commentSubmitting = ref(false);
const commentInputRef = ref<HTMLTextAreaElement | null>(null);

const noteId = computed(() => Number(route.params.id));
const paragraphs = computed(() => {
  if (!note.value) {
    return [];
  }
  const text = note.value.content || note.value.excerpt;
  const split = text.split(/\n+/).map((item) => item.trim()).filter(Boolean);
  return split.length > 0 ? split : noteContent(noteId.value);
});

onMounted(loadPage);
watch(noteId, loadPage);

async function loadPage() {
  if (!Number.isFinite(noteId.value)) {
    note.value = null;
    return;
  }
  loading.value = true;
  comments.value = [];
  totalComments.value = 0;
  favorited.value = false;
  try {
    try {
      const result = await getNote(noteId.value);
      note.value = noteCardToView(result.data);
    } catch {
      const fallback = noteById(noteId.value);
      note.value = fallback ? lifeNoteToView(fallback) : null;
      if (fallback) {
        showToast('笔记暂时使用本地展示数据');
      }
    }

    await Promise.allSettled([
      loadComments(),
      loadRelatedNotes(),
      loadFavoriteState(),
    ]);
  } finally {
    loading.value = false;
  }
}

async function loadComments() {
  try {
    const result = await pageNoteComments(noteId.value, { pageNo: 1, pageSize: 20 });
    comments.value = result.data.records;
    totalComments.value = Number(result.data.total);
  } catch {
    totalComments.value = note.value?.comments || 0;
  }
}

async function loadRelatedNotes() {
  try {
    const result = await pageNotes({ pageNo: 1, pageSize: 12 });
    relatedNotes.value = result.data.records
      .filter((item) => item.id !== noteId.value)
      .slice(0, 4)
      .map(noteCardToView);
  } catch {
    relatedNotes.value = relatedNotesForNote(noteId.value, 4).map(lifeNoteToView);
  }
}

async function loadFavoriteState() {
  if (!authStore.isLoggedIn) {
    return;
  }
  try {
    const result = await getNoteFavorite(noteId.value);
    favorited.value = result.data.favorited;
  } catch {
    favorited.value = false;
  }
}

async function toggleFavorite() {
  if (!authStore.isLoggedIn) {
    showToast('登录后可以收藏笔记');
    router.push({ path: '/login', query: { redirect: route.fullPath } });
    return;
  }
  favoriteLoading.value = true;
  try {
    const result = favorited.value
      ? await cancelFavoriteNote(noteId.value)
      : await favoriteNote(noteId.value);
    favorited.value = result.data.favorited;
    showSuccessToast(favorited.value ? '已收藏' : '已取消收藏');
  } catch (error) {
    const message = isApiBusinessError(error) ? friendlyMessage(error.code, error.message) : '收藏失败';
    showFailToast(message);
  } finally {
    favoriteLoading.value = false;
  }
}

async function submitComment() {
  const content = commentText.value.trim();
  if (!content) {
    return;
  }
  commentSubmitting.value = true;
  try {
    const result = await createNoteComment(noteId.value, { content });
    comments.value = [result.data, ...comments.value];
    totalComments.value += 1;
    commentText.value = '';
    showSuccessToast('评论已发布');
  } catch (error) {
    const message = isApiBusinessError(error) ? friendlyMessage(error.code, error.message) : '评论发布失败';
    showFailToast(message);
  } finally {
    commentSubmitting.value = false;
  }
}

function openNote(id: number) {
  router.push(`/notes/${id}`);
}

async function focusComment() {
  await nextTick();
  commentInputRef.value?.focus();
}

function showTodo() {
  showToast(todoMessage());
}
</script>

<style scoped>
.note-detail-page {
  padding-top: 24px;
}

.note-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.back-link,
.merchant-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 14px;
  border-radius: var(--radius-pill);
  font-size: 14px;
  font-weight: 800;
}

.back-link {
  color: var(--brand-orange-deep);
}

.merchant-link {
  background: var(--brand-orange);
  color: var(--neutral-surface);
}

.note-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: 28px;
  align-items: start;
}

.note-media {
  position: sticky;
  top: 92px;
  overflow: hidden;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-lg);
  background: var(--neutral-surface);
  box-shadow: var(--shadow-panel);
}

.note-media img {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 5;
  object-fit: cover;
  background: var(--neutral-surface-soft);
}

.image-count {
  position: absolute;
  top: 14px;
  right: 14px;
  min-height: 28px;
  padding: 5px 11px;
  border-radius: var(--radius-pill);
  background: oklch(0.24 0.013 70 / 0.64);
  color: var(--neutral-surface);
  font-size: 13px;
  font-weight: 800;
}

.note-content {
  min-width: 0;
  padding: 22px 0 8px;
}

.note-author {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}

.author-avatar {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
  font-size: 20px;
  font-weight: 900;
}

.note-author strong,
.note-author span {
  display: block;
}

.note-author strong {
  color: var(--text-strong);
  font-size: 17px;
}

.note-author span {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

.note-author button {
  min-height: 34px;
  padding: 0 16px;
  border: 1px solid oklch(0.84 0.09 58);
  border-radius: var(--radius-pill);
  background: var(--neutral-surface);
  color: var(--brand-orange-deep);
  font-size: 13px;
  font-weight: 800;
}

h1 {
  margin: 0;
  color: var(--text-strong);
  font-size: 28px;
  line-height: 1.32;
}

.note-lead {
  margin: 14px 0 0;
  color: var(--text-muted);
  font-size: 15px;
  line-height: 1.75;
}

.note-body {
  display: grid;
  gap: 14px;
  margin-top: 24px;
}

.note-body p {
  margin: 0;
  color: var(--text-strong);
  font-size: 16px;
  line-height: 1.9;
}

.note-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 24px;
}

.note-tags span {
  min-height: 28px;
  padding: 5px 10px;
  border-radius: var(--radius-pill);
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
  font-size: 13px;
  font-weight: 800;
}

.merchant-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  padding: 13px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  box-shadow: var(--shadow-panel);
}

.merchant-card__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
  font-size: 22px;
}

.merchant-card strong,
.merchant-card small {
  display: block;
}

.merchant-card small {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.note-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid var(--neutral-line);
}

.note-actions button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-pill);
  background: var(--neutral-surface);
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 800;
}

.note-actions button.active {
  border-color: oklch(0.84 0.09 58);
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
}

.note-actions .van-icon {
  color: var(--brand-orange-deep);
  font-size: 18px;
}

.comments-section {
  margin-top: 34px;
  padding-top: 22px;
  border-top: 1px solid var(--neutral-line);
}

.section-title--compact {
  margin-bottom: 14px;
}

.comment-box {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
}

.comment-box textarea {
  width: 100%;
  resize: vertical;
  border: 0;
  outline: none;
  color: var(--text-strong);
  font: inherit;
  line-height: 1.6;
}

.comment-box button,
.login-tip a {
  justify-self: end;
  min-height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-weight: 800;
}

.comment-box button:disabled {
  opacity: 0.5;
}

.login-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border-radius: var(--radius-md);
  background: var(--brand-orange-soft);
  color: var(--text-muted);
}

.comment-list {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.comment-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 10px;
}

.comment-avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--neutral-surface-soft);
  color: var(--brand-orange-deep);
  font-weight: 900;
}

.comment-item strong {
  color: var(--text-strong);
}

.comment-item p {
  margin: 5px 0;
  color: var(--text-strong);
  line-height: 1.65;
}

.comment-item time {
  color: var(--text-muted);
  font-size: 12px;
}

.related-section {
  margin-top: 46px;
}

.related-notes {
  column-count: 4;
  column-gap: 18px;
}

@media (max-width: 1024px) {
  .note-layout {
    grid-template-columns: 1fr;
  }

  .note-media {
    position: relative;
    top: auto;
  }

  .note-media img {
    aspect-ratio: 16 / 10;
  }

  .related-notes {
    column-count: 3;
  }
}

@media (max-width: 720px) {
  .note-top {
    align-items: flex-start;
    flex-direction: column;
  }

  .merchant-link {
    width: 100%;
  }

  .note-content {
    padding-top: 0;
  }

  .note-author {
    grid-template-columns: 42px minmax(0, 1fr) auto;
  }

  .author-avatar {
    width: 42px;
    height: 42px;
  }

  h1 {
    font-size: 24px;
  }

  .note-body p {
    font-size: 15px;
  }

  .related-notes {
    column-count: 1;
  }
}
</style>
