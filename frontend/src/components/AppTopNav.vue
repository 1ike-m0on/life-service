<template>
  <header class="top-nav">
    <div class="top-nav__inner">
      <RouterLink class="brand" to="/">
        <span class="brand__mark">LS</span>
        <span>
          <strong>Life Service</strong>
          <small>本地好店</small>
        </span>
      </RouterLink>

      <button type="button" class="city-button" @click="showTodo">
        <van-icon name="location-o" />
        杭州
        <van-icon name="arrow-down" />
      </button>

      <form class="nav-search" @submit.prevent="search">
        <input v-model.trim="keyword" placeholder="搜索店名、菜品、目的地" />
        <button type="submit" aria-label="搜索">
          <van-icon name="search" />
        </button>
      </form>

      <nav class="nav-links" aria-label="主导航">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/merchants">找好店</RouterLink>
        <RouterLink to="/orders">订单</RouterLink>
        <RouterLink to="/me">我的</RouterLink>
      </nav>

      <RouterLink v-if="!authStore.isLoggedIn" class="login-button" to="/login">
        登录
      </RouterLink>
      <RouterLink v-else class="user-pill" to="/me">
        {{ authStore.currentUser?.nickname || authStore.currentUser?.email || '我的账号' }}
      </RouterLink>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { useAuthStore } from '@/stores/auth';
import { todoMessage } from '@/utils/format';

const authStore = useAuthStore();
const router = useRouter();
const keyword = ref('');

function search() {
  router.push({ path: '/merchants', query: { keyword: keyword.value || undefined } });
}

function showTodo() {
  showToast(todoMessage());
}
</script>

<style scoped>
.top-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  border-bottom: 1px solid var(--neutral-line);
  background: oklch(0.995 0.008 82 / 0.98);
}

.top-nav__inner {
  display: grid;
  grid-template-columns: auto auto minmax(260px, 420px) minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  width: min(var(--content-width), calc(100vw - 48px));
  min-height: 68px;
  margin: 0 auto;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 164px;
}

.brand__mark {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-size: 13px;
  font-weight: 900;
}

.brand strong,
.brand small {
  display: block;
}

.brand strong {
  color: var(--text-strong);
  font-size: 20px;
  line-height: 1;
}

.brand small {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 11px;
  letter-spacing: 0;
}

.city-button,
.nav-search button,
.login-button,
.user-pill {
  border: 0;
  background: transparent;
}

.city-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-strong);
  font-size: 15px;
  font-weight: 800;
}

.city-button .van-icon:first-child {
  color: var(--brand-orange);
  font-size: 20px;
}

.nav-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 42px;
  height: 40px;
  overflow: hidden;
  border: 1px solid oklch(0.84 0.09 62);
  border-radius: 999px;
  background: var(--neutral-surface);
}

.nav-search input {
  min-width: 0;
  border: 0;
  outline: 0;
  padding: 0 16px 0 20px;
  background: transparent;
  color: var(--text-strong);
  font-size: 14px;
}

.nav-search input::placeholder {
  color: oklch(0.68 0.025 70);
}

.nav-search button {
  display: grid;
  place-items: center;
  color: var(--brand-orange-deep);
  font-size: 19px;
}

.nav-links {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 24px;
}

.nav-links a {
  color: var(--text-muted);
  font-size: 14px;
  white-space: nowrap;
}

.nav-links a.router-link-active {
  color: var(--brand-orange-deep);
  font-weight: 800;
}

.login-button,
.user-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 92px;
  min-height: 38px;
  padding: 0 18px;
  border-radius: 999px;
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-size: 14px;
  font-weight: 800;
}

.user-pill {
  max-width: 168px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1024px) {
  .top-nav__inner {
    grid-template-columns: auto auto minmax(220px, 1fr) auto;
  }

  .nav-links {
    grid-column: 1 / -1;
    justify-content: flex-start;
    padding-bottom: 12px;
  }
}

@media (max-width: 720px) {
  .top-nav__inner {
    width: min(calc(100vw - 24px), var(--content-width));
    grid-template-columns: 1fr auto;
    gap: 12px;
    padding: 12px 0;
  }

  .top-nav__inner > * {
    min-width: 0;
  }

  .city-button,
  .nav-search,
  .nav-links {
    grid-column: 1 / -1;
  }

  .nav-links {
    overflow-x: auto;
    padding-bottom: 0;
  }
}
</style>
