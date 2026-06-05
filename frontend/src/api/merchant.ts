import { request } from './client';
import type { PageResponse } from '@/types/api';
import type { Merchant, MerchantCategory } from '@/types/merchant';

export function listCategories() {
  return request<MerchantCategory[]>({
    method: 'GET',
    url: '/v1/merchant-categories',
  });
}

export interface MerchantQuery {
  categoryId?: number | null;
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}

export function pageMerchants(query: MerchantQuery = {}) {
  return request<PageResponse<Merchant>>({
    method: 'GET',
    url: '/v1/merchants',
    params: {
      categoryId: query.categoryId || undefined,
      keyword: query.keyword || undefined,
      pageNo: query.pageNo || 1,
      pageSize: query.pageSize || 10,
    },
  });
}

export function getMerchant(id: number) {
  return request<Merchant>({
    method: 'GET',
    url: `/v1/merchants/${id}`,
  });
}
