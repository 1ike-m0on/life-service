export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string | null;
  data: T;
}

export interface PageResponse<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface ApiResult<T> {
  data: T;
  response: ApiResponse<T>;
  status: number;
}

export class ApiBusinessError extends Error {
  code: string;
  status: number;

  constructor(message: string, code: string, status: number) {
    super(message);
    this.name = 'ApiBusinessError';
    this.code = code;
    this.status = status;
  }
}

export function isApiBusinessError(error: unknown): error is ApiBusinessError {
  return error instanceof ApiBusinessError;
}
