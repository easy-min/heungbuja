import type { DeviceLoginRequest, DeviceLoginResponse } from '../types/device-auth';
import api from './index';

/**
 * 기기 로그인 API 호출
 */
export const deviceLoginApi = (credentials: DeviceLoginRequest) =>
  api.post<DeviceLoginResponse>('/auth/device', credentials);