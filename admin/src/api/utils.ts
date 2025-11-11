/**
 * API 요청을 위한 공통 헤더 생성
 */
export const getAuthHeaders = (): HeadersInit => {
  const token = localStorage.getItem('accessToken');
  
  return {
    'Content-Type': 'application/json',
    ...(token && { 'Authorization': `Bearer ${token}` }),
  };
};

/**
 * API 에러 핸들러
 */
export const handleApiError = async (response: Response): Promise<never> => {
  let errorMessage = '요청 처리 중 오류가 발생했습니다.';
  
  try {
    const error = await response.json();
    errorMessage = error.message || errorMessage;
  } catch {
    errorMessage = response.statusText || errorMessage;
  }
  
  // 401 Unauthorized - 토큰 만료 또는 인증 실패
  if (response.status === 401) {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.location.href = '/login';
  }
  
  throw new Error(errorMessage);
};

/**
 * API Base URL 가져오기
 */
export const getApiBaseUrl = (): string => {
  return import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
};