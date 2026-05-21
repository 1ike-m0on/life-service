<template>
  <main class="page login-page">
    <section class="login-intro">
      <span class="state-chip">欢迎回来</span>
      <h1>登录后继续抢券和查看订单</h1>
      <p>当前版本使用邮箱登录。输入任意合法邮箱即可进入用户端体验。</p>
      <div class="login-intro__points">
        <span>商户优惠</span>
        <span>秒杀抢券</span>
        <span>最近订单</span>
      </div>
    </section>

    <section class="login-card card">
      <h2>邮箱登录</h2>
      <p>推荐使用测试邮箱，便于重复体验抢券流程。</p>

      <van-form class="login-form" @submit="submit">
        <van-field
          v-model="email"
          name="email"
          label="邮箱"
          placeholder="demo2001@life.local"
          :rules="[{ validator: validateEmail, message: '请输入正确邮箱格式' }]"
        />
        <van-button block type="primary" native-type="submit" :loading="authStore.loading">
          登录
        </van-button>
      </van-form>

      <div class="quick-users">
        <span>测试邮箱</span>
        <button
          v-for="user in users"
          :key="user"
          type="button"
          @click="email = user"
        >
          {{ user }}
        </button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { showFailToast, showSuccessToast } from 'vant';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { friendlyMessage } from '@/utils/format';
import { isApiBusinessError } from '@/types/api';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const email = ref('demo2001@life.local');
const users = ['demo2001@life.local', 'demo2002@life.local', 'demo2003@life.local'];

function validateEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

async function submit() {
  try {
    await authStore.login(email.value);
    showSuccessToast('登录成功');
    router.replace(String(route.query.redirect || '/'));
  } catch (error) {
    const message = isApiBusinessError(error)
      ? friendlyMessage(error.code, error.message)
      : '登录失败';
    showFailToast(message);
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 36px;
  align-items: center;
  min-height: calc(100vh - 64px);
}

.login-intro {
  padding: 42px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-md);
  background:
    linear-gradient(135deg, rgba(255, 241, 235, 0.94), rgba(255, 235, 207, 0.66)),
    var(--neutral-surface);
  box-shadow: var(--shadow-card);
}

.login-intro h1 {
  max-width: 560px;
  margin: 18px 0 12px;
  font-size: 40px;
  line-height: 1.12;
}

.login-intro p,
.login-card p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.login-intro__points {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;
}

.login-intro__points span {
  padding: 8px 12px;
  border-radius: var(--radius-pill);
  background: rgba(255, 253, 251, 0.72);
  color: var(--brand-orange-deep);
  font-weight: 800;
}

.login-card {
  padding: 24px;
}

.login-card h2 {
  margin: 0 0 8px;
  font-size: 24px;
}

.login-form {
  margin-top: 22px;
}

.quick-users {
  display: grid;
  gap: 8px;
  margin-top: 22px;
}

.quick-users span {
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.quick-users button {
  min-height: 38px;
  border: 1px solid var(--neutral-line);
  border-radius: var(--radius-pill);
  background: var(--neutral-surface-soft);
  color: var(--brand-orange-deep);
  font-size: 13px;
  font-weight: 800;
}

@media (max-width: 920px) {
  .login-page {
    grid-template-columns: 1fr;
    min-height: auto;
  }
}

@media (max-width: 720px) {
  .login-intro {
    padding: 24px;
  }

  .login-intro h1 {
    font-size: 30px;
  }
}
</style>
