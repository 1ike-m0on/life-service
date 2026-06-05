import { defineStore } from 'pinia';
import { pageMyVoucherOrders, payVoucherOrder } from '@/api/order';
import { nowIso } from '@/utils/time';
import { readJson, writeJson } from '@/utils/storage';
import type { OrderDisplayStatus, RecentOrder, VoucherOrderSummary } from '@/types/order';

const ORDERS_KEY = 'life-service-recent-orders';

function toDisplayStatus(statusCode?: number): OrderDisplayStatus {
  if (statusCode === 2) {
    return 'PAID';
  }
  if (statusCode === 3) {
    return 'CLOSED';
  }
  if (statusCode === 4) {
    return 'FAILED';
  }
  return 'PENDING_PAYMENT';
}

function statusMessage(status: OrderDisplayStatus): string {
  if (status === 'PAID') {
    return '已支付，可到店出示使用';
  }
  if (status === 'CLOSED') {
    return '订单已关闭';
  }
  if (status === 'FAILED') {
    return '订单处理失败，请稍后查看';
  }
  return '待支付，请在有效期内完成支付';
}

function fromSummary(summary: VoucherOrderSummary): RecentOrder {
  const status = toDisplayStatus(summary.status);
  return {
    orderNo: summary.orderNo,
    voucherId: summary.voucherId,
    voucherTitle: summary.voucherTitle,
    voucherSubtitle: summary.voucherSubtitle,
    merchantId: summary.merchantId,
    merchantName: summary.merchantName,
    merchantImages: summary.merchantImages,
    payAmountCent: summary.payAmountCent,
    status,
    statusCode: summary.status,
    message: statusMessage(status),
    createdAt: summary.createdAt,
    updatedAt: summary.paidAt || summary.closedAt || summary.createdAt,
  };
}

function mergeOrders(remote: RecentOrder[], local: RecentOrder[]): RecentOrder[] {
  const seen = new Set<string>();
  const merged: RecentOrder[] = [];
  for (const order of [...remote, ...local]) {
    if (seen.has(order.orderNo)) {
      continue;
    }
    seen.add(order.orderNo);
    merged.push(order);
  }
  return merged
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
    .slice(0, 50);
}

export const useOrderStore = defineStore('order', {
  state: () => ({
    recentOrders: readJson<RecentOrder[]>(ORDERS_KEY, []),
    loading: false,
  }),
  getters: {
    lastOrder: (state) => state.recentOrders[0] || null,
  },
  actions: {
    persist() {
      writeJson(ORDERS_KEY, this.recentOrders.slice(0, 50));
    },
    addRecentOrder(order: Omit<RecentOrder, 'status' | 'createdAt' | 'updatedAt'>) {
      const timestamp = nowIso();
      const record: RecentOrder = {
        ...order,
        status: 'PENDING_PAYMENT',
        message: order.message || '下单成功，等待支付',
        createdAt: timestamp,
        updatedAt: timestamp,
      };
      this.recentOrders = [
        record,
        ...this.recentOrders.filter((item) => item.orderNo !== record.orderNo),
      ].slice(0, 50);
      this.persist();
      return record;
    },
    async loadMyOrders(status?: number | null) {
      this.loading = true;
      try {
        const result = await pageMyVoucherOrders({ status, pageNo: 1, pageSize: 50 });
        const remote = result.data.records.map(fromSummary);
        this.recentOrders = mergeOrders(remote, status ? [] : this.recentOrders);
        this.persist();
        return result;
      } finally {
        this.loading = false;
      }
    },
    async pay(orderNo: string) {
      const result = await payVoucherOrder(orderNo);
      const index = this.recentOrders.findIndex((item) => item.orderNo === orderNo);
      if (index >= 0) {
        const status = toDisplayStatus(result.data.status);
        this.recentOrders[index] = {
          ...this.recentOrders[index],
          statusCode: result.data.status,
          status,
          message: result.data.idempotent ? '订单已支付，本次为幂等返回' : '支付成功',
          updatedAt: nowIso(),
        };
        this.persist();
      }
      return result;
    },
    markFailed(orderNo: string, message: string, status: OrderDisplayStatus = 'FAILED') {
      const index = this.recentOrders.findIndex((item) => item.orderNo === orderNo);
      if (index >= 0) {
        this.recentOrders[index] = {
          ...this.recentOrders[index],
          status,
          message,
          updatedAt: nowIso(),
        };
        this.persist();
      }
    },
    clearOrders() {
      this.recentOrders = [];
      this.persist();
    },
  },
});
