import dayjs from 'dayjs';

export function formatTime(value: string): string {
  return dayjs(value).format('MM-DD HH:mm');
}

export function formatDateTime(value: string): string {
  return dayjs(value).format('YYYY-MM-DD HH:mm');
}

export function nowIso(): string {
  return new Date().toISOString();
}
