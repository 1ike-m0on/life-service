import axios, { AxiosRequestConfig } from 'axios';
import { ApiBusinessError, ApiResponse, ApiResult } from '@/types/api';
import { friendlyMessage } from '@/utils/format';

const TOKEN_KEY = 'life-service-token';

const http = axios.create({
  baseURL: '/api',
  timeout: 8000,
  validateStatus: () => true,
});

export function getStoredToken(): string {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setStoredToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearStoredToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

function codeFromStatus(status: number): string {
  if (status === 401) return 'UNAUTHORIZED';
  if (status === 404) return 'NOT_FOUND';
  if (status === 429) return 'RATE_LIMITED';
  return 'SYSTEM_ERROR';
}

export async function request<T>(config: AxiosRequestConfig): Promise<ApiResult<T>> {
  const token = getStoredToken();
  const headers = {
    ...(config.headers || {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const response = await http.request<ApiResponse<T>>({
    ...config,
    headers,
  });

  const body = response.data;

  if (!body || typeof body.success !== 'boolean') {
    const code = codeFromStatus(response.status);
    throw new ApiBusinessError(friendlyMessage(code), code, response.status);
  }

  if (!body.success || response.status >= 400) {
    const code = body.code || codeFromStatus(response.status);
    throw new ApiBusinessError(friendlyMessage(code, body.message), code, response.status);
  }

  return {
    data: body.data,
    response: body,
    status: response.status,
  };
}
