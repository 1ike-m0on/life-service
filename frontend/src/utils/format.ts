const codeMessages: Record<string, string> = {
  OK: '操作成功',
  BAD_REQUEST: '请求参数有误',
  UNAUTHORIZED: '请先登录',
  NOT_FOUND: '功能未完成',
  RATE_LIMITED: '操作太频繁，请稍后再试',
  FLASH_SALE_NOT_READY: '活动准备中，请稍后再试',
  FLASH_SALE_STOCK_NOT_ENOUGH: '已抢光',
  FLASH_SALE_DUPLICATE_ORDER: '你已经抢过该券',
  ORDER_CLOSED: '订单已关闭',
  SYSTEM_ERROR: '功能未完成，请稍后再试',
};

export function friendlyMessage(code?: string, message?: string | null): string {
  if (code && codeMessages[code]) {
    return codeMessages[code];
  }
  return message || '功能未完成，请稍后再试';
}

export function todoMessage(): string {
  return '功能未完成';
}
