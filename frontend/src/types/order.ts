export type OrderDisplayStatus = 'PENDING_PAYMENT' | 'PAID' | 'CLOSED' | 'FAILED';

export interface VoucherOrderPaymentResponse {
  orderNo: string;
  status: number;
  idempotent: boolean;
}

export interface VoucherOrderSummary {
  orderNo: string;
  merchantId: number;
  merchantName: string;
  merchantImages: string[];
  voucherId: number;
  voucherTitle: string;
  voucherSubtitle?: string | null;
  payAmountCent: number;
  status: number;
  createdAt: string;
  paidAt?: string | null;
  closedAt?: string | null;
}

export interface VoucherOrderDetail extends VoucherOrderSummary {
  merchantArea?: string | null;
  merchantAddress?: string | null;
  voucherRules?: string | null;
  discountAmountCent?: number | null;
  voucherType?: number | null;
}

export interface RecentOrder {
  orderNo: string;
  voucherId: number;
  voucherTitle: string;
  voucherSubtitle?: string | null;
  merchantId: number;
  merchantName: string;
  merchantImages?: string[];
  payAmountCent: number;
  status: OrderDisplayStatus;
  statusCode?: number;
  message?: string;
  createdAt: string;
  updatedAt: string;
}
