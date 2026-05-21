import { createRouter, createWebHashHistory } from 'vue-router';
import HomePage from '@/pages/HomePage.vue';
import MerchantListPage from '@/pages/MerchantListPage.vue';
import MerchantDetailPage from '@/pages/MerchantDetailPage.vue';
import LoginPage from '@/pages/LoginPage.vue';
import OrdersPage from '@/pages/OrdersPage.vue';
import MePage from '@/pages/MePage.vue';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'home', component: HomePage, meta: { title: 'Life Service' } },
    { path: '/merchants', name: 'merchants', component: MerchantListPage, meta: { title: '商户' } },
    { path: '/merchants/:id', name: 'merchant-detail', component: MerchantDetailPage, meta: { title: '商户详情' } },
    { path: '/login', name: 'login', component: LoginPage, meta: { title: '邮箱登录' } },
    { path: '/orders', name: 'orders', component: OrdersPage, meta: { title: '最近订单' } },
    { path: '/me', name: 'me', component: MePage, meta: { title: '我的' } },
  ],
  scrollBehavior() {
    return { top: 0 };
  },
});

router.afterEach((to) => {
  document.title = `${String(to.meta.title || 'Life Service')} | Life Service`;
});

export default router;
