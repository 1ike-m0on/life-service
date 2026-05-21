import { request } from './client';
import { AuthResponse, CurrentUser } from '@/types/auth';

export function loginByEmail(email: string) {
  return request<AuthResponse>({
    method: 'POST',
    url: '/v1/auth/login',
    data: { email },
  });
}

export function getCurrentUser() {
  return request<CurrentUser>({
    method: 'GET',
    url: '/v1/auth/me',
  });
}

export function logoutRequest() {
  return request<void>({
    method: 'POST',
    url: '/v1/auth/logout',
  });
}
