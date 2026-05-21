import { defineStore } from 'pinia';
import { payVoucherOrder } from '@/api/order';
import { OrderDisplayStatus, RecentOrder } from '@/types/order';
import { nowIso } from '@/utils/time';
import { readJson, writeJson } from '@/utils/storage';

const ORDERS_KEY = 'life-service-recent-orders';

function toDisplayStatus(statusCode?: number): OrderDisplayStatus {
  if (statusCode === 2) {
    return 'PAID';
  }
  if (statusCode === 3) {
    return 'CLOSED';
  }
  return 'PENDING_PAYMENT';
}

export const useOrderStore = defineStore('order', {
  state: () => ({
    recentOrders: readJson<RecentOrder[]>(ORDERS_KEY, []),
  }),
  getters: {
    lastOrder: (state) => state.recentOrders[0] || null,
  },
  actions: {
    persist() {
      writeJson(ORDERS_KEY, this.recentOrders.slice(0, 20));
    },
    addRecentOrder(order: Omit<RecentOrder, 'status' | 'createdAt' | 'updatedAt'>) {
      const timestamp = nowIso();
      const record: RecentOrder = {
        ...order,
        status: 'PENDING_PAYMENT',
        createdAt: timestamp,
        updatedAt: timestamp,
      };
      this.recentOrders = [
        record,
        ...this.recentOrders.filter((item) => item.orderNo !== record.orderNo),
      ].slice(0, 20);
      this.persist();
      return record;
    },
    async pay(orderNo: string) {
      const result = await payVoucherOrder(orderNo);
      const index = this.recentOrders.findIndex((item) => item.orderNo === orderNo);
      if (index >= 0) {
        this.recentOrders[index] = {
          ...this.recentOrders[index],
          statusCode: result.data.status,
          status: toDisplayStatus(result.data.status),
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
