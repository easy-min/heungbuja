// API 공통 응답
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

// 에러 응답
export interface ErrorResponse {
  success: false;
  message: string;
  statusCode?: number;
  errors?: Record<string, string>;
}

// 페이지네이션 응답
export interface PaginatedResponse<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
  hasNext: boolean;
}