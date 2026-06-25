import { expect, test, type Page, type Route } from '@playwright/test';

const user = {
  userId: 2001,
  email: 'demo2001@life.local',
  nickname: 'E2E Demo User',
  token: 'e2e-token',
};

const merchant = {
  id: 101,
  categoryId: 2,
  name: 'E2E Hotpot House',
  images: '/assets/merchants/hotpot/red-flame-cover.jpg',
  area: 'E2E Business District',
  address: 'No. 18 E2E Road',
  longitude: 120.12,
  latitude: 30.28,
  avgPriceCent: 9800,
  soldCount: 268,
  commentCount: 96,
  score: 48,
  openHours: '10:00-22:00',
  status: 1,
};

const voucher = {
  id: 501,
  merchantId: merchant.id,
  title: 'E2E Flash Voucher',
  subtitle: 'Valid for the E2E purchase flow',
  rules: 'Use in store after purchase.',
  payAmountCent: 3900,
  discountAmountCent: 10000,
  type: 2,
  status: 1,
};

const orderNo = 'E2E-ORDER-1001';
const createdAt = '2026-06-25T10:00:00+08:00';

function ok<T>(data: T) {
  return {
    success: true,
    code: 'SUCCESS',
    message: null,
    data,
  };
}

function pageOf<T>(records: T[]) {
  return {
    records,
    total: records.length,
    pageNo: 1,
    pageSize: 20,
  };
}

async function fulfillJson(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });
}

async function mockLifeServiceApi(page: Page) {
  let claimed = false;

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (method === 'POST' && path === '/api/v1/auth/login') {
      await fulfillJson(route, ok(user));
      return;
    }

    if (method === 'GET' && path === '/api/v1/auth/me') {
      await fulfillJson(route, ok({
        userId: user.userId,
        email: user.email,
        nickname: user.nickname,
      }));
      return;
    }

    if (method === 'GET' && path === '/api/v1/merchant-categories') {
      await fulfillJson(route, ok([
        { id: 2, name: 'E2E Food', sortOrder: 1, status: 1 },
      ]));
      return;
    }

    if (method === 'GET' && path === '/api/v1/notes') {
      await fulfillJson(route, ok(pageOf([])));
      return;
    }

    if (method === 'GET' && path === '/api/v1/merchants') {
      await fulfillJson(route, ok(pageOf([merchant])));
      return;
    }

    if (method === 'GET' && path === `/api/v1/merchants/${merchant.id}`) {
      await fulfillJson(route, ok(merchant));
      return;
    }

    if (method === 'GET' && path === `/api/v1/merchants/${merchant.id}/vouchers`) {
      await fulfillJson(route, ok([voucher]));
      return;
    }

    if (method === 'GET' && path === `/api/v1/merchants/${merchant.id}/notes`) {
      await fulfillJson(route, ok(pageOf([])));
      return;
    }

    if (method === 'POST' && path === `/api/v1/flash-sale-vouchers/${voucher.id}/orders`) {
      claimed = true;
      await fulfillJson(route, ok(orderNo));
      return;
    }

    if (method === 'GET' && path === '/api/v1/users/me/voucher-orders') {
      await fulfillJson(route, ok(pageOf(claimed ? [{
        orderNo,
        merchantId: merchant.id,
        merchantName: merchant.name,
        merchantImages: [merchant.images],
        voucherId: voucher.id,
        voucherTitle: voucher.title,
        voucherSubtitle: voucher.subtitle,
        payAmountCent: voucher.payAmountCent,
        status: 1,
        createdAt,
        paidAt: null,
        closedAt: null,
      }] : [])));
      return;
    }

    if (method === 'POST' && path === `/api/v1/voucher-orders/${orderNo}/payment`) {
      await fulfillJson(route, ok({ orderNo, status: 2, idempotent: false }));
      return;
    }

    await fulfillJson(route, {
      success: false,
      code: 'NOT_FOUND',
      message: `Unhandled E2E mock route: ${method} ${path}`,
      data: null,
    }, 404);
  });
}

test.describe('local life service purchase journey', () => {
  test.beforeEach(async ({ page }) => {
    await mockLifeServiceApi(page);
  });

  test('logs in, browses merchants, claims a flash-sale voucher, and sees the order', async ({ page }) => {
    await page.goto('/#/login');

    await expect(page.getByTestId('login-form')).toBeVisible();
    await page.getByTestId('login-email').locator('input').fill(user.email);
    await page.getByTestId('login-submit').click();

    await expect.poll(() => page.evaluate(() => localStorage.getItem('life-service-token'))).toBe(user.token);

    await page.goto('/#/merchants');
    const merchantCard = page.getByTestId(`merchant-card-${merchant.id}`);
    await expect(merchantCard).toBeVisible();
    await expect(merchantCard).toContainText(merchant.name);

    await merchantCard.click();
    await expect(page).toHaveURL(new RegExp(`#/merchants/${merchant.id}$`));
    await expect(page.getByTestId('merchant-detail-name')).toContainText(merchant.name);

    await expect(page.getByTestId(`voucher-ticket-${voucher.id}`)).toContainText(voucher.title);
    await page.getByTestId(`voucher-claim-${voucher.id}`).click();

    const resultPanel = page.getByTestId('order-result-panel');
    await expect(resultPanel).toContainText(orderNo);
    await resultPanel.locator('a').click();

    const orderCard = page.getByTestId(`order-card-${orderNo}`);
    await expect(page.getByTestId('orders-page')).toBeVisible();
    await expect(orderCard).toBeVisible();
    await expect(orderCard).toHaveAttribute('data-order-status', 'PENDING_PAYMENT');
    await expect(orderCard).toContainText(voucher.title);
    await expect(orderCard).toContainText(merchant.name);
  });
});
