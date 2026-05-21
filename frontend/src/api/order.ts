import { request } from './client';
import { VoucherOrderPaymentResponse } from '@/types/order';

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
