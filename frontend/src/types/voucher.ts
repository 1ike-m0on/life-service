export interface Voucher {
  id: number;
  merchantId: number;
  title: string;
  subtitle: string;
  rules: string;
  payAmountCent: number;
  discountAmountCent: number;
  type: number;
  status: number;
}
