import type { DeviceLoginRequest, DeviceLoginResponse } from '../types/device-auth';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

/**
 * 기기 로그인 API 호출
 */
export const deviceLoginApi = async (credentials: DeviceLoginRequest): Promise<DeviceLoginResponse> => {
  const response = await fetch(`${API_BASE}/auth/device`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(credentials),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '로그인에 실패했습니다.');
  }

  return response.json();
};