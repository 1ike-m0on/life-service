export function formatCent(value?: number | null): string {
  if (value == null) {
    return '--';
  }
  return `¥${(value / 100).toFixed(value % 100 === 0 ? 0 : 2)}`;
}

export function formatScore(score?: number | null): string {
  if (score == null) {
    return '--';
  }
  return (score / 10).toFixed(1);
}

export function discountLabel(payAmountCent?: number | null, discountAmountCent?: number | null): string {
  if (!payAmountCent || !discountAmountCent) {
    return '限时优惠';
  }
  const actual = payAmountCent + discountAmountCent;
  const discount = (payAmountCent / actual) * 10;
  return `${discount.toFixed(1)}折`;
}
