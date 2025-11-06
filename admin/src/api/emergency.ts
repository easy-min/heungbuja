import type { EmergencyReport, ResolveEmergencyResponse } from '../types/emergency';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

/**
 * 신고 목록 조회
 */
export const getEmergencyReports = async (): Promise<EmergencyReport[]> => {
  const token = localStorage.getItem('accessToken');
  
  // HTML과 동일한 엔드포인트 사용
  const response = await fetch(`${API_BASE}/emergency/admins/reports`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '신고 목록을 불러오는데 실패했습니다.');
  }

  return response.json();
};

/**
 * 신고 처리 (해결)
 */
export const resolveEmergency = async (reportId: number): Promise<ResolveEmergencyResponse> => {
  const token = localStorage.getItem('accessToken');
  const notes = '관리자 확인 완료';
  
  // HTML과 동일한 엔드포인트 사용 (PUT 방식)
  const response = await fetch(`${API_BASE}/emergency/admins/reports/${reportId}?notes=${encodeURIComponent(notes)}`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '신고 처리에 실패했습니다.');
  }

  return response.json();
};