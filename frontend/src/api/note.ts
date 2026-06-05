import { request } from './client';
import type { PageResponse } from '@/types/api';
import type {
  NoteCard,
  NoteComment,
  NoteCommentCreateRequest,
  NoteCreateRequest,
  NoteDetail,
  NoteFavoriteResponse,
} from '@/types/note';

export interface PageQuery {
  pageNo?: number;
  pageSize?: number;
}

export function pageNotes(query: PageQuery = {}) {
  return request<PageResponse<NoteCard>>({
    method: 'GET',
    url: '/v1/notes',
    params: {
      pageNo: query.pageNo || 1,
      pageSize: query.pageSize || 20,
    },
  });
}

export function getNote(noteId: number) {
  return request<NoteDetail>({
    method: 'GET',
    url: `/v1/notes/${noteId}`,
  });
}

export function pageMerchantNotes(merchantId: number, query: PageQuery = {}) {
  return request<PageResponse<NoteCard>>({
    method: 'GET',
    url: `/v1/merchants/${merchantId}/notes`,
    params: {
      pageNo: query.pageNo || 1,
      pageSize: query.pageSize || 10,
    },
  });
}

export function pageMyNotes(query: PageQuery = {}) {
  return request<PageResponse<NoteCard>>({
    method: 'GET',
    url: '/v1/users/me/notes',
    params: {
      pageNo: query.pageNo || 1,
      pageSize: query.pageSize || 10,
    },
  });
}

export function createMyNote(payload: NoteCreateRequest) {
  return request<NoteDetail>({
    method: 'POST',
    url: '/v1/users/me/notes',
    data: payload,
  });
}

export function pageFavoriteNotes(query: PageQuery = {}) {
  return request<PageResponse<NoteCard>>({
    method: 'GET',
    url: '/v1/users/me/favorite-notes',
    params: {
      pageNo: query.pageNo || 1,
      pageSize: query.pageSize || 10,
    },
  });
}

export function getNoteFavorite(noteId: number) {
  return request<NoteFavoriteResponse>({
    method: 'GET',
    url: `/v1/users/me/notes/${noteId}/favorite`,
  });
}

export function favoriteNote(noteId: number) {
  return request<NoteFavoriteResponse>({
    method: 'POST',
    url: `/v1/users/me/notes/${noteId}/favorite`,
  });
}

export function cancelFavoriteNote(noteId: number) {
  return request<NoteFavoriteResponse>({
    method: 'DELETE',
    url: `/v1/users/me/notes/${noteId}/favorite`,
  });
}

export function pageNoteComments(noteId: number, query: PageQuery = {}) {
  return request<PageResponse<NoteComment>>({
    method: 'GET',
    url: `/v1/notes/${noteId}/comments`,
    params: {
      pageNo: query.pageNo || 1,
      pageSize: query.pageSize || 10,
    },
  });
}

export function createNoteComment(noteId: number, payload: NoteCommentCreateRequest) {
  return request<NoteComment>({
    method: 'POST',
    url: `/v1/users/me/notes/${noteId}/comments`,
    data: payload,
  });
}

export function deleteNoteComment(commentId: number) {
  return request<void>({
    method: 'DELETE',
    url: `/v1/users/me/note-comments/${commentId}`,
  });
}
