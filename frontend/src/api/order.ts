import { request } from './client';
import type { PageResponse } from '@/types/api';
import type { VoucherOrderDetail, VoucherOrderPaymentResponse, VoucherOrderSummary } from '@/types/order';

export function createFlashSaleOrder(voucherId: number) {
  return request<string>({
    method: 'POST',
    url: `/v1/flash-sale-vouchers/${voucherId}/orders`,
  });
}

export function payVoucherOrder(orderNo: string) {
  return request<VoucherOrderPaymentResponse>({
    method: 'POST',
    url: `/v1/voucher-orders/${orderNo}/payment`,
  });
}

export interface VoucherOrderQuery {
  status?: number | null;
  pageNo?: number;
  pageSize?: number;
}

export function pageMyVoucherOrders(query: VoucherOrderQuery = {}) {
  return request<PageResponse<VoucherOrderSummary>>({
    method: 'GET',
    url: '/v1/users/me/voucher-orders',
    params: {
      status: query.status || undefined,
      pageNo: query.pageNo || 1,
      pageSize: query.pageSize || 20,
    },
  });
}

export function getVoucherOrder(orderNo: string) {
  return request<VoucherOrderDetail>({
    method: 'GET',
    url: `/v1/voucher-orders/${orderNo}`,
  });
}
