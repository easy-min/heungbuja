// 신고 상태 타입
export type EmergencyStatus = 'CONFIRMED' | 'RESOLVED' | 'FALSE_ALARM';

// 신고 타입
export interface EmergencyReport {
  reportId: number;
  userId: number;
  userName: string;
  triggerWord?: string;
  isConfirmed?: boolean;
  status: EmergencyStatus;
  reportedAt: string;
  message?: string;
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