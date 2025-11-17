import type { DeviceLoginRequest, DeviceLoginResponse } from '../types/device-auth';
import api from './index';

/**
 * 기기 로그인 API 호출
 */
export const deviceLoginApi = (credentials: DeviceLoginRequest) =>
  api.post<DeviceLoginResponse>('/auth/device', credentials);

/**
 * Access Token 갱신 API 호출
 */
export const refreshAccessToken = async (): Promise<string | null> => {
  try {
    const refreshToken = localStorage.getItem('userRefreshToken');
    if (!refreshToken) return null;

    const response = await api.post<{
      accessToken: string;
      refreshToken: string;
      tokenType: string;
      expiresIn: number;
    }>('/auth/refresh', { refreshToken }, false);

    // 새 토큰들 저장
    localStorage.setItem('userAccessToken', response.accessToken);
    localStorage.setItem('userRefreshToken', response.refreshToken);

    return response.accessToken;
  } catch (error) {
    console.error('Token refresh error:', error);
    return null;
  }
};