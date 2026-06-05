const codeMessages: Record<string, string> = {
  OK: '操作成功',
  BAD_REQUEST: '请求参数有误',
  UNAUTHORIZED: '请先登录',
  NOT_FOUND: '暂时没有找到相关内容',
  RATE_LIMITED: '操作太频繁，请稍后再试',
  FLASH_SALE_NOT_READY: '当前暂不可购买，请稍后再试',
  FLASH_SALE_STOCK_NOT_ENOUGH: '已售罄',
  FLASH_SALE_DUPLICATE_ORDER: '你已经购买过该优惠',
  ORDER_CLOSED: '订单已关闭',
  SYSTEM_ERROR: '服务暂时不可用，请稍后再试',
};

export function friendlyMessage(code?: string, message?: string | null): string {
  if (code && codeMessages[code]) {
    return codeMessages[code];
  }
  return message || '服务暂时不可用，请稍后再试';
}

export function todoMessage(): string {
  return '功能完善中';
}
