// 신고 상태 타입
export type EmergencyStatus = 'PENDING' | 'CONFIRMED' | 'RESOLVED' | 'FALSE_ALARM';

// 신고 타입
export interface EmergencyReport {
  id: number;
  userId: number;
  userName: string;
  userRoom?: string;
  status: EmergencyStatus;
  reportedAt: string;
  resolvedAt?: string;
  location?: string;
  description?: string;
}

// 신고 해결 요청
export interface ResolveEmergencyRequest {
  reportId: number;
}

// 신고 해결 응답
export interface ResolveEmergencyResponse {
  id: number;
  status: EmergencyStatus;
  resolvedAt: string;
}