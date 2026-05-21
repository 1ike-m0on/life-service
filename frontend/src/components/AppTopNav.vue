<template>
  <header class="top-nav">
    <div class="top-nav__inner">
      <RouterLink class="brand" to="/">
        <span class="brand__mark">LS</span>
        <span>
          <strong>Life Service</strong>
          <small>本地生活</small>
        </span>
      </RouterLink>

      <nav class="nav-links" aria-label="主导航">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/merchants">商户</RouterLink>
        <RouterLink to="/orders">订单</RouterLink>
        <RouterLink to="/me">我的</RouterLink>
      </nav>

      <div class="top-nav__actions">
        <button type="button" class="link-button" @click="showTodo">消息</button>
        <RouterLink v-if="!authStore.isLoggedIn" class="login-button" to="/login">登录</RouterLink>
        <RouterLink v-else class="user-pill" to="/me">
          {{ authStore.currentUser?.nickname || authStore.currentUser?.email || '我的账号' }}
        </RouterLink>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router';
import { showToast } from 'vant';
import { useAuthStore } from '@/stores/auth';
import { todoMessage } from '@/utils/format';

const authStore = useAuthStore();

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
  background: rgba(255, 253, 251, 0.96);
  backdrop-filter: blur(10px);
}

.top-nav__inner {
  width: min(var(--content-width), calc(100vw - 48px));
  min-height: 64px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 190px;
}

.brand__mark {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  border-radius: 10px;
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-size: 13px;
  font-weight: 800;
}

.brand strong,
.brand small {
  display: block;
}

.brand strong {
  font-size: 17px;
  line-height: 1.1;
}

.brand small {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
}

.nav-links a,
.link-button {
  min-height: 36px;
  padding: 0 14px;
  border: 0;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 700;
}

.nav-links a {
  display: inline-flex;
  align-items: center;
}

.nav-links a.router-link-active {
  background: var(--brand-orange-soft);
  color: var(--brand-orange-deep);
}

.top-nav__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 220px;
}

.login-button,
.user-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 16px;
  border-radius: var(--radius-pill);
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-size: 14px;
  font-weight: 800;
}

.user-pill {
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .top-nav__inner {
    width: min(100% - 24px, var(--content-width));
    min-height: auto;
    padding: 12px 0;
    flex-wrap: wrap;
    gap: 12px;
  }

  .brand,
  .top-nav__actions {
    min-width: 0;
  }

  .nav-links {
    order: 3;
    width: 100%;
    overflow-x: auto;
  }
}
</style>
