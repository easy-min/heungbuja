// 기기 정보
export interface Device {
  id: number;
  serialNumber: string;
  location?: string;
  isConnected: boolean;
  connectedUserId?: number;
  createdAt: string;
}

// 기기 등록 요청
export interface RegisterDeviceRequest {
  serialNumber: string;
  location?: string;
}

// 기기 등록 응답
export interface RegisterDeviceResponse {
  id: number;
  serialNumber: string;
  location?: string;
  createdAt: string;
}

// 건강 통계
export interface HealthStats {
  heartRate: number;
  heartRateStatus: 'normal' | 'high' | 'low';
  steps: number;
  calories: number;
  exerciseTime: number; // 분 단위
}

// 동작별 수행도
export interface ActionPerformance {
  actionCode: number;
  actionName: string;
  successCount: number;
  totalCount: number;
  accuracy: number; // 0-100
}

// 활동 추이 데이터 포인트
export interface ActivityTrendPoint {
  date: string; // YYYY-MM-DD
  exerciseTime: number; // 분 단위
  accuracy: number; // 0-100
}

// 활동 로그
export interface ActivityLog {
  id: number;
  type: 'MUSIC_PLAY' | 'EXERCISE_COMPLETE' | 'EMERGENCY' | 'DEVICE_CONNECT';
  description: string;
  timestamp: string;
  metadata?: Record<string, any>;
}

// 기간 타입
export type PeriodType = 1 | 7 | 30;

// 사용자 상세 데이터
export interface UserDetailData {
  healthStats: HealthStats | null;
  actionPerformance: ActionPerformance[];
  activityTrend: ActivityTrendPoint[];
  recentActivities: ActivityLog[];
}