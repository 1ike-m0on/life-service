import { defineStore } from 'pinia';
import { clearStoredToken, getStoredToken, setStoredToken } from '@/api/client';
import { getCurrentUser, loginByEmail, logoutRequest } from '@/api/auth';
import { CurrentUser } from '@/types/auth';

const USER_KEY = 'life-service-user';

function readUser(): CurrentUser | null {
  const value = localStorage.getItem(USER_KEY);
  if (!value) {
    return null;
  }
  try {
    return JSON.parse(value) as CurrentUser;
  } catch {
    return null;
  }
}

function writeUser(user: CurrentUser | null): void {
  if (!user) {
    localStorage.removeItem(USER_KEY);
    return;
  }
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getStoredToken(),
    currentUser: readUser() as CurrentUser | null,
    loading: false,
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
  },
  actions: {
    async login(email: string) {
      this.loading = true;
      try {
        const result = await loginByEmail(email);
        this.token = result.data.token;
        const user = {
          userId: result.data.userId,
          email: result.data.email,
          nickname: result.data.nickname,
        };
        this.currentUser = user;
        setStoredToken(result.data.token);
        writeUser(user);
        return result;
      } finally {
        this.loading = false;
      }
    },
    async fetchMe() {
      if (!this.token) {
        return null;
      }
      const result = await getCurrentUser();
      this.currentUser = result.data;
      writeUser(result.data);
      return result.data;
    },
    async logout() {
      try {
        if (this.token) {
          await logoutRequest();
        }
      } finally {
        this.token = '';
        this.currentUser = null;
        clearStoredToken();
        writeUser(null);
      }
    },
  },
});
