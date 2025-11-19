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

// 게임 통계
export interface GameStats {
  totalGames: number;
  completedGames: number;
  overallAverageScore: number;
  perfectRate: number;
  lastPlayedAt?: string;
}

// 동작 항목
export interface ActionItem {
  actionCode: number;
  actionName: string;
  averageScore: number;
  attemptCount: number;
}

// 동작별 수행도 응답
export interface ActionPerformanceResponse {
  topActions: ActionItem[];
  weakActions: ActionItem[];
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
  activityType: 'MUSIC_PLAY' | 'EXERCISE_COMPLETE' | 'EMERGENCY' | 'DEVICE_CONNECT';
  activitySummary: string;
  createdAt: string;
  metadata?: Record<string, any>;
}

// 기간 타입
export type PeriodType = 1 | 7 | 30;

// 사용자 상세 데이터
export interface UserDetailData {
  gameStats: GameStats | null;
  actionPerformance: ActionPerformanceResponse | null;
  recentActivities: ActivityLog[];
}