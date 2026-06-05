import { request } from './client';
import type { Voucher } from '@/types/voucher';

export function listMerchantVouchers(merchantId: number) {
  return request<Voucher[]>({
    method: 'GET',
    url: `/v1/merchants/${merchantId}/vouchers`,
  });
}
