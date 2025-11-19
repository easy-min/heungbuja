import type {
  Device,
  RegisterDeviceRequest,
  RegisterDeviceResponse,
  GameStats,
  ActionPerformanceResponse,
  ActivityTrendPoint,
  ActivityLog,
  PeriodType,
} from '../types/device';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

/**
 * 기기 목록 조회
 */
export const getDevices = async (availableOnly: boolean = false): Promise<Device[]> => {
  const token = localStorage.getItem('accessToken');
  const queryParam = availableOnly ? '?availableOnly=true' : '';
  
  const response = await fetch(`${API_BASE}/admins/devices${queryParam}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '기기 목록을 불러오는데 실패했습니다.');
  }

  return response.json();
};

/**
 * 기기 등록
 */
export const registerDevice = async (deviceData: RegisterDeviceRequest): Promise<RegisterDeviceResponse> => {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`${API_BASE}/admins/devices`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(deviceData),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '기기 등록에 실패했습니다.');
  }

  return response.json();
};

/**
 * 특정 기기 상세 조회
 */
export const getDeviceById = async (deviceId: number): Promise<Device> => {
  const token = localStorage.getItem('accessToken');

  const response = await fetch(`${API_BASE}/admins/devices/${deviceId}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '기기 정보를 불러오는데 실패했습니다.');
  }

  return response.json();
};

/**
 * 사용자 게임 통계 조회
 */
export const getUserGameStats = async (userId: number): Promise<GameStats> => {
  const token = localStorage.getItem('accessToken');

  const response = await fetch(`${API_BASE}/admins/users/${userId}/game-stats`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '게임 통계를 불러오는데 실패했습니다.');
  }

  return response.json();
};

/**
 * 사용자 동작별 수행도 조회
 */
export const getUserActionPerformance = async (userId: number, periodDays: PeriodType): Promise<ActionPerformanceResponse> => {
  const token = localStorage.getItem('accessToken');

  const response = await fetch(`${API_BASE}/admins/users/${userId}/action-performance?periodDays=${periodDays}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '수행도 데이터를 불러오는데 실패했습니다.');
  }

  return response.json();
};

/**
 * 사용자 활동 추이 조회
 */
export const getUserActivityTrend = async (userId: number, periodDays: PeriodType): Promise<ActivityTrendPoint[]> => {
  const token = localStorage.getItem('accessToken');

  const response = await fetch(`${API_BASE}/admins/users/${userId}/activity-trend?periodDays=${periodDays}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '활동 추이를 불러오는데 실패했습니다.');
  }

  return response.json();
};

/**
 * 사용자 최근 활동 로그 조회
 */
export const getUserRecentActivities = async (userId: number, size: number = 10): Promise<ActivityLog[]> => {
  const token = localStorage.getItem('accessToken');

  const response = await fetch(`${API_BASE}/admins/activity-logs/users/${userId}?page=0&size=${size}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '활동 로그를 불러오는데 실패했습니다.');
  }

  const data = await response.json();
  // 페이지네이션 응답에서 content만 반환
  return data.content || [];
};