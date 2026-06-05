import { createRouter, createWebHashHistory } from 'vue-router';
import HomePage from '@/pages/HomePage.vue';
import MerchantListPage from '@/pages/MerchantListPage.vue';
import MerchantDetailPage from '@/pages/MerchantDetailPage.vue';
import NoteDetailPage from '@/pages/NoteDetailPage.vue';
import LoginPage from '@/pages/LoginPage.vue';
import OrdersPage from '@/pages/OrdersPage.vue';
import FavoriteNotesPage from '@/pages/FavoriteNotesPage.vue';
import MePage from '@/pages/MePage.vue';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'home', component: HomePage, meta: { title: 'Life Service' } },
    { path: '/merchants', name: 'merchants', component: MerchantListPage, meta: { title: '找好店' } },
    { path: '/merchants/:id', name: 'merchant-detail', component: MerchantDetailPage, meta: { title: '商户详情' } },
    { path: '/notes/:id', name: 'note-detail', component: NoteDetailPage, meta: { title: '笔记详情' } },
    { path: '/login', name: 'login', component: LoginPage, meta: { title: '邮箱登录' } },
    { path: '/orders', name: 'orders', component: OrdersPage, meta: { title: '我的订单' } },
    { path: '/favorites', name: 'favorites', component: FavoriteNotesPage, meta: { title: '我的收藏' } },
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
