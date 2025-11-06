// 로그인 요청 타입
export interface LoginRequest {
  username: string;
  password: string;
}

// 로그인 응답 타입
export interface LoginResponse {
  accessToken: string;
  refreshToken?: string;
  adminId?: number;
  username?: string;
}

// 에러 응답 타입
export interface ErrorResponse {
  message: string;
  statusCode?: number;
}