<template>
  <main class="page me-page">
    <section class="profile-card card">
      <div class="profile-card__avatar">
        {{ avatarText }}
      </div>
      <div class="profile-card__body">
        <span class="state-chip">{{ authStore.isLoggedIn ? '已登录' : '未登录' }}</span>
        <h1>{{ authStore.currentUser?.nickname || '欢迎来到 Life Service' }}</h1>
        <p>{{ authStore.currentUser?.email || '登录后可以抢券、模拟支付并查看最近订单。' }}</p>
        <div class="profile-card__actions">
          <RouterLink v-if="!authStore.isLoggedIn" class="primary-link" to="/login?redirect=/me">
            邮箱登录
          </RouterLink>
          <button v-else type="button" class="logout-button" :disabled="loggingOut" @click="logout">
            退出登录
          </button>
        </div>
      </div>
    </section>

    <section class="section page-grid">
      <div class="entry-panel card">
        <div class="section-title">
          <div>
            <h2>我的服务</h2>
            <p>保留用户端自然入口，未完成能力给出明确提示。</p>
          </div>
        </div>

        <div class="entry-list">
          <RouterLink class="entry-item" to="/orders">
            <span class="entry-item__icon"><van-icon name="orders-o" /></span>
            <span>
              <strong>我的订单</strong>
              <small>查看最近抢券和支付状态</small>
            </span>
            <van-icon name="arrow" />
          </RouterLink>

          <button type="button" class="entry-item" @click="showTodo">
            <span class="entry-item__icon"><van-icon name="star-o" /></span>
            <span>
              <strong>我的收藏</strong>
              <small>收藏商户后续补齐</small>
            </span>
            <van-icon name="arrow" />
          </button>

          <button type="button" class="entry-item" @click="showTodo">
            <span class="entry-item__icon"><van-icon name="clock-o" /></span>
            <span>
              <strong>浏览历史</strong>
              <small>记录最近查看的商户</small>
            </span>
            <van-icon name="arrow" />
          </button>

          <button type="button" class="entry-item" @click="showTodo">
            <span class="entry-item__icon"><van-icon name="setting-o" /></span>
            <span>
              <strong>设置</strong>
              <small>账号设置暂未实现</small>
            </span>
            <van-icon name="arrow" />
          </button>
        </div>
      </div>

      <aside class="sidebar">
        <section class="side-card">
          <h3>用户端定位</h3>
          <p>这里保留普通用户会自然使用的入口，项目实现细节放在文档和后端代码中。</p>
        </section>
        <section class="side-card side-card--voucher">
          <h3>去抢券</h3>
          <p>进入商户详情页即可查看代金券和秒杀券。</p>
          <RouterLink to="/merchants">浏览商户</RouterLink>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { showSuccessToast, showToast } from 'vant';
import { useAuthStore } from '@/stores/auth';
import { todoMessage } from '@/utils/format';

const router = useRouter();
const authStore = useAuthStore();
const loggingOut = ref(false);

const avatarText = computed(() => {
  const source = authStore.currentUser?.nickname || authStore.currentUser?.email || 'LS';
  return source.slice(0, 2).toUpperCase();
});

onMounted(() => {
  if (authStore.isLoggedIn) {
    authStore.fetchMe().catch(() => undefined);
  }
});

async function logout() {
  loggingOut.value = true;
  try {
    await authStore.logout();
    showSuccessToast('已退出登录');
    router.push('/');
  } finally {
    loggingOut.value = false;
  }
}

function showTodo() {
  showToast(todoMessage());
}
</script>

<style scoped>
.profile-card {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 22px;
  padding: 28px;
  background:
    linear-gradient(135deg, rgba(255, 232, 217, 0.92), rgba(255, 235, 207, 0.62)),
    var(--neutral-surface);
}

.profile-card__avatar {
  width: 96px;
  height: 96px;
  display: grid;
  place-items: center;
  border-radius: 28px;
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-size: 26px;
  font-weight: 900;
}

h1 {
  margin: 14px 0 8px;
  font-size: 34px;
  line-height: 1.15;
}

p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.65;
}

.profile-card__actions {
  margin-top: 18px;
}

.primary-link,
.logout-button,
.side-card a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 16px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--brand-orange);
  color: var(--neutral-surface);
  font-weight: 800;
}

.logout-button {
  background: var(--neutral-surface);
  color: var(--danger);
  border: 1px solid rgba(240, 51, 51, 0.22);
}

.entry-panel {
  padding: 20px;
}

.entry-list {
  display: grid;
  gap: 10px;
}

.entry-item {
  width: 100%;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 12px;
  min-height: 70px;
  padding: 12px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  color: var(--text-strong);
  text-align: left;
}

.entry-item__icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 15px;
  background: var(--brand-orange-soft);
  color: var(--brand-orange);
  font-size: 22px;
}

.entry-item strong,
.entry-item small {
  display: block;
}

.entry-item small {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.side-card {
  padding: 18px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background: var(--neutral-surface);
  box-shadow: var(--shadow-panel);
}

.side-card--voucher {
  background: var(--voucher-wash);
}

.side-card h3 {
  margin: 0 0 8px;
}

.side-card a {
  margin-top: 16px;
}

@media (max-width: 720px) {
  .profile-card {
    grid-template-columns: 1fr;
  }

  h1 {
    font-size: 28px;
  }
}
</style>
