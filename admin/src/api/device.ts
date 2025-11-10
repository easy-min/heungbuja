import type { Device, RegisterDeviceRequest, RegisterDeviceResponse } from '../types/device';

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