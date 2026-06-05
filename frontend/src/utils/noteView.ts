import type { LifeNote } from '@/data/lifeNotes';
import type { NoteCard, NoteDetail } from '@/types/note';

export interface NoteView {
  id: number;
  merchantId: number;
  merchantName: string;
  author: string;
  avatarUrl?: string | null;
  title: string;
  excerpt: string;
  content: string;
  image: string;
  images: string[];
  rating: number;
  likes: number;
  comments: number;
  favorites: number;
  area: string;
  tags: string[];
  createdAt?: string;
}

const fallbackImages = [
  '/assets/merchants/hotpot/red-flame-cover.jpg',
  '/assets/merchants/coffee/moonlight-cover.jpg',
  '/assets/merchants/bakery/morning-wheat-cover.jpg',
  '/assets/merchants/japanese/sora-sushi-cover.jpg',
  '/assets/merchants/lifestyle/starlight-cinema-cover.jpg',
  '/assets/merchants/lifestyle/urban-fit-cover.jpg',
];

function fallbackImage(id: number): string {
  return fallbackImages[Math.abs(id) % fallbackImages.length];
}

function excerptOf(content: string): string {
  const trimmed = content.trim();
  return trimmed.length > 74 ? `${trimmed.slice(0, 74)}...` : trimmed;
}

function tagsFrom(note: NoteCard | NoteDetail): string[] {
  const tags = new Set<string>();
  if (note.rating >= 5) {
    tags.add('高分体验');
  }
  if (note.commentCount > 10) {
    tags.add('讨论热门');
  }
  if (note.favoriteCount > 20) {
    tags.add('值得收藏');
  }
  if (tags.size === 0) {
    tags.add('真实笔记');
  }
  return Array.from(tags).slice(0, 3);
}

export function noteCardToView(note: NoteCard | NoteDetail): NoteView {
  const images = note.images?.length ? note.images : [fallbackImage(note.id)];
  return {
    id: note.id,
    merchantId: note.merchantId,
    merchantName: note.merchantName,
    author: note.nickname || '本地生活用户',
    avatarUrl: note.avatarUrl,
    title: note.title,
    excerpt: excerptOf(note.content),
    content: note.content,
    image: images[0],
    images,
    rating: note.rating || 5,
    likes: note.likeCount || 0,
    comments: note.commentCount || 0,
    favorites: note.favoriteCount || 0,
    area: '本地',
    tags: tagsFrom(note),
    createdAt: note.createdAt,
  };
}

export function lifeNoteToView(note: LifeNote): NoteView {
  return {
    id: note.id,
    merchantId: note.merchantId,
    merchantName: note.merchantName,
    author: note.author,
    avatarUrl: note.avatar,
    title: note.title,
    excerpt: note.excerpt,
    content: note.excerpt,
    image: note.image,
    images: note.images?.length ? note.images : [note.image],
    rating: note.rating || 5,
    likes: note.likes,
    comments: note.comments,
    favorites: note.favorites || 0,
    area: note.area,
    tags: note.tags,
    createdAt: note.createdAt,
  };
}

export function toNoteViews(notes: Array<NoteCard | NoteDetail | LifeNote>): NoteView[] {
  return notes.map((note) => (
    'nickname' in note ? noteCardToView(note) : lifeNoteToView(note)
  ));
}
