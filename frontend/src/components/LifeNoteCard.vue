<template>
  <article class="note-card" @click="$emit('openNote', note.id)">
    <img class="note-card__image" :src="note.image" :alt="note.title" loading="lazy" />
    <div class="note-card__body">
      <h3>{{ note.title }}</h3>
      <p>{{ note.excerpt }}</p>
      <div class="note-card__merchant">
        <van-icon name="shop-o" />
        <span>{{ note.merchantName }}</span>
        <small>{{ note.area }}</small>
      </div>
      <div class="note-card__tags" aria-label="笔记标签">
        <span v-for="tag in note.tags.slice(0, 3)" :key="tag">{{ tag }}</span>
      </div>
      <div class="note-card__foot">
        <span class="note-card__author">
          <span class="note-card__avatar">{{ note.author.slice(0, 1) }}</span>
          {{ note.author }}
        </span>
        <span class="note-card__stats">
          <van-icon name="like-o" />
          {{ note.likes }}
          <span class="note-card__dot" />
          {{ note.comments }}评
        </span>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { NoteView } from '@/utils/noteView';

defineProps<{
  note: NoteView;
}>();

defineEmits<{
  openNote: [noteId: number];
}>();
</script>

<style scoped>
.note-card {
  display: inline-block;
  width: 100%;
  margin: 0 0 22px;
  overflow: hidden;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-lg);
  background: var(--neutral-surface);
  break-inside: avoid;
  cursor: pointer;
  transition: transform 150ms ease, box-shadow 150ms ease, border-color 150ms ease;
}

.note-card:hover {
  transform: translateY(-2px);
  border-color: oklch(0.84 0.09 58);
  box-shadow: var(--shadow-card);
}

.note-card__image {
  display: block;
  width: 100%;
  min-height: 220px;
  max-height: 360px;
  object-fit: cover;
  background: var(--neutral-surface-soft);
}

.note-card__body {
  padding: 12px 13px 13px;
}

h3 {
  margin: 0;
  color: var(--text-strong);
  font-size: 16px;
  line-height: 1.45;
}

p {
  display: -webkit-box;
  margin: 8px 0 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.note-card__merchant {
  display: flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  margin-top: 10px;
  padding: 0 9px;
  border-radius: 6px;
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
  font-size: 12px;
}

.note-card__merchant span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-card__merchant small {
  flex: 0 0 auto;
  color: oklch(0.62 0.07 65);
}

.note-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.note-card__tags span {
  min-height: 24px;
  padding: 4px 8px;
  border-radius: var(--radius-pill);
  background: var(--neutral-surface-soft);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.note-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  color: var(--text-muted);
  font-size: 12px;
}

.note-card__author {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 7px;
}

.note-card__avatar {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
  font-size: 12px;
  font-weight: 900;
}

.note-card__stats {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 4px;
}

.note-card__dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--text-muted);
}
</style>
