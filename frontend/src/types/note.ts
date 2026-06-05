export interface NoteCard {
  id: number;
  merchantId: number;
  merchantName: string;
  userId: number;
  nickname: string;
  avatarUrl?: string | null;
  title: string;
  content: string;
  rating: number;
  images: string[];
  likeCount: number;
  commentCount: number;
  favoriteCount: number;
  createdAt: string;
}

export interface NoteDetail extends NoteCard {
  updatedAt: string;
}

export interface NoteComment {
  id: number;
  noteId: number;
  userId: number;
  nickname: string;
  avatarUrl?: string | null;
  parentId?: number | null;
  content: string;
  createdAt: string;
}

export interface NoteFavoriteResponse {
  noteId: number;
  favorited: boolean;
}

export interface NoteCommentCreateRequest {
  content: string;
}

export interface NoteCreateRequest {
  merchantId: number;
  title: string;
  content: string;
  rating: number;
  images?: string[];
}
