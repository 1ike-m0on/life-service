export interface AuthResponse {
  token: string;
  tokenType: string;
  userId: number;
  email: string;
  nickname: string;
}

export interface CurrentUser {
  userId: number;
  email: string;
  nickname: string;
}
