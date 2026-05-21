export type OrderDisplayStatus = 'PENDING_PAYMENT' | 'PAID' | 'CLOSED' | 'FAILED';

export interface VoucherOrderPaymentResponse {
  orderNo: string;
  status: number;
  idempotent: boolean;
}

export interface RecentOrder {
  orderNo: string;
  voucherId: number;
  voucherTitle: string;
  merchantId: number;
  merchantName: string;
  payAmountCent: number;
  status: OrderDisplayStatus;
  statusCode?: number;
  message?: string;
  createdAt: string;
  updatedAt: string;
}
